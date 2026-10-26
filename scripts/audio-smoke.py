"""Desktop Whisper smoke test using the app's catalog artifact and decode settings.

Setup: python -m pip install pywhispercpp==1.5.1; FFmpeg on PATH for float WAV.
Usage: python scripts/audio-smoke.py --directory work/implementation/asr
Inputs: ggml-base-q5_1.bin, jfk.wav, hindi-0.wav, hindi-1.wav.
This does NOT exercise Android JNI, capture, foreground services or editor insertion.
"""
import argparse
import hashlib
import importlib.metadata
import json
import re
import time
import unicodedata
import wave
import subprocess
from pathlib import Path

import numpy as np
from pywhispercpp.model import Model


def read_wav(path):
    try:
        with wave.open(str(path), "rb") as source:
            assert source.getnchannels() == 1 and source.getframerate() == 16000 and source.getsampwidth() == 2
            return np.frombuffer(source.readframes(source.getnframes()), dtype="<i2").astype(np.float32) / 32768
    except wave.Error:
        # FLEURS viewer assets can use IEEE-float WAV, which Python's wave reader rejects.
        decoded=subprocess.run(["ffmpeg","-v","error","-i",str(path),"-ac","1","-ar","16000","-f","f32le","pipe:1"],capture_output=True,check=True)
        return np.frombuffer(decoded.stdout,dtype="<f4").copy()


def words(text):
    return "".join(c if unicodedata.category(c)[0] in "LMN" else " " for c in unicodedata.normalize("NFC", text).lower()).split()


def wer(reference, hypothesis):
    ref, hyp = words(reference), words(hypothesis)
    row = list(range(len(hyp) + 1))
    for i, a in enumerate(ref, 1):
        next_row = [i]
        for j, b in enumerate(hyp, 1):
            next_row.append(min(next_row[-1] + 1, row[j] + 1, row[j - 1] + (a != b)))
        row = next_row
    return row[-1] / max(1, len(ref))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--threads",type=int,default=1)
    parser.add_argument("--long-seconds",type=int,default=132)
    parser.add_argument("--resume",action="store_true")
    args = parser.parse_args()
    directory = args.directory.resolve()
    model_path = directory / "ggml-base-q5_1.bin"
    assert hashlib.sha256(model_path.read_bytes()).hexdigest() == "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
    assert 120 < args.long_seconds <= 600 and 1 <= args.threads <= 4
    report = {"backend": "pywhispercpp", "version": importlib.metadata.version("pywhispercpp"), "threads": args.threads, "decode_version": 3, "model_sha256": hashlib.sha256(model_path.read_bytes()).hexdigest(), "tests": []}
    if args.resume and (directory/"audio-results.json").exists():
        previous=json.loads((directory/"audio-results.json").read_text(encoding="utf-8"))
        assert all(previous.get(key)==report[key] for key in ("backend","version","threads","model_sha256","decode_version"))
        report=previous
    common = dict(n_threads=args.threads, translate=False, detect_language=False, no_context=True, single_segment=False,
                  print_realtime=False, print_progress=False, print_timestamps=False, print_special=False,
                  suppress_blank=True, suppress_nst=True)

    def run(model, name, pcm, language, reference=None, constructed=False):
        if args.resume and any(item["name"]==name and item["audio_seconds"]==len(pcm)/16000 for item in report["tests"]) and (directory/f"{name}.raw.txt").exists():
            print(f"Keeping completed {name}",flush=True)
            return
        print(f"Starting {name}: {len(pcm)/16000:.1f}s",flush=True)
        started = time.perf_counter()
        # This binding strips whitespace from each segment; restore a separator at each join.
        text = " ".join(segment.text for segment in model.transcribe(pcm)).strip()
        elapsed = time.perf_counter() - started
        assert text, f"Empty transcript: {name}"
        (directory / f"{name}.raw.txt").write_text(text, encoding="utf-8")
        item = dict(name=name, language=language, audio_seconds=len(pcm) / 16000, inference_seconds=round(elapsed, 3), constructed=constructed, text=text)
        if language=="hi":
            item.update(has_devanagari=any("\u0900"<=c<="\u097f" for c in text),has_arabic=any("\u0600"<=c<="\u06ff" for c in text))
        if reference:
            item.update(reference=reference, word_error_rate=round(wer(reference, text), 4))
        report["tests"].append(item)
        (directory / "audio-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"{name}: {item['audio_seconds']:.1f}s audio, {elapsed:.1f}s inference, WER={item.get('word_error_rate', 'n/a')}", flush=True)

    english = Model(str(model_path), params_sampling_strategy=0, context_params={"use_gpu": False}, language="en", **common)
    jfk = read_wav(directory / "jfk.wav")
    run(english, "jfk", jfk, "en", "And so my fellow Americans ask not what your country can do for you ask what you can do for your country")
    if (directory / "formatting.wav").exists():
        run(english, "formatting", read_wav(directory / "formatting.wav"), "en", constructed=True)
    del english

    hindi = Model(str(model_path), params_sampling_strategy=1, context_params={"use_gpu": False}, language="hi",
                  beam_search={"beam_size": 5, "patience": -1.0}, temperature=0.0, temperature_inc=0.2,
                  suppress_regex=r"[\s\S]*[\xD8-\xDF][\s\S]*", **common)
    sources = json.loads((directory.parent / "015-hindi-audio-source.json").read_text(encoding="utf-8-sig"))
    for index, source in enumerate(sources):
        run(hindi, f"hindi-{index}", read_wav(directory / f"hindi-{index}.wav"), "hi", source["row"]["transcription"])
    del hindi
    # Constructed duration smoke test, not independent natural long-form speech.
    english = Model(str(model_path), params_sampling_strategy=0, context_params={"use_gpu": False}, language="en", **common)
    long_audio = np.tile(jfk, int(args.long_seconds * 16000 // len(jfk)))
    run(english, "english-long", long_audio, "en", constructed=True)


if __name__ == "__main__":
    main()
