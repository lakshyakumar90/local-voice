"""Compare unprompted local Hindi recognition on the two downloaded FLEURS clips.

Uses the same environment and input directory as audio-smoke.py. No cloud recognition.
"""
import argparse
import hashlib
import json
import runpy
import time
from pathlib import Path

from pywhispercpp.model import Model

helpers=runpy.run_path(str(Path(__file__).with_name("audio-smoke.py")))
parser=argparse.ArgumentParser()
parser.add_argument("--directory",type=Path,required=True)
parser.add_argument("--threads",type=int,default=2)
parser.add_argument("--suppress-arabic",action="store_true")
args=parser.parse_args()
directory=args.directory.resolve()
catalog=json.loads((Path(__file__).resolve().parent.parent/"app/src/main/assets/models.json").read_text(encoding="utf-8"))
sources=json.loads((directory.parent/"015-hindi-audio-source.json").read_text(encoding="utf-8-sig"))
report={"backend":"pywhispercpp 1.5.1", "threads":args.threads,"language":"hi","translate":False,"initial_prompt":None,"temperature_inc":0.2,"script_constraint":args.suppress_arabic,"tests":[]}
for model_id in ("whisper-base","whisper-small"):
    spec=next(item for item in catalog["models"] if item["id"]==model_id)["files"][0]
    model_path=directory/spec["name"]
    assert hashlib.sha256(model_path.read_bytes()).hexdigest()==spec["sha256"]
    constraint={"suppress_regex":r"[\s\S]*[\xD8-\xDF][\s\S]*"} if args.suppress_arabic else {}
    model=Model(str(model_path),params_sampling_strategy=1,context_params={"use_gpu":False},
                n_threads=args.threads,language="hi",translate=False,detect_language=False,no_context=True,
                single_segment=False,print_realtime=False,print_progress=False,print_timestamps=False,
                print_special=False,suppress_blank=True,suppress_nst=True,
                beam_search={"beam_size":5,"patience":-1.0},temperature=0.0,temperature_inc=0.2,**constraint)
    for index,source in enumerate(sources):
        name=f"{model_id}-{'constrained' if args.suppress_arabic else 'unprompted'}-hindi-{index}"
        print(f"Starting {name}",flush=True)
        pcm=helpers["read_wav"](directory/f"hindi-{index}.wav")
        started=time.perf_counter()
        text=" ".join(segment.text for segment in model.transcribe(pcm)).strip()
        elapsed=time.perf_counter()-started
        reference=source["row"]["transcription"]
        (directory/f"{name}.raw.txt").write_text(text,encoding="utf-8")
        item=dict(name=name,model=model_id,model_sha256=spec["sha256"],audio_seconds=len(pcm)/16000,
                  inference_seconds=round(elapsed,3),text=text,reference=reference,
                  word_error_rate=round(helpers["wer"](reference,text),4))
        report["tests"].append(item)
        (directory/("hindi-comparison-constrained.json" if args.suppress_arabic else "hindi-comparison.json")).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
        print(f"{name}: {elapsed:.1f}s, WER={item['word_error_rate']}",flush=True)
    del model
