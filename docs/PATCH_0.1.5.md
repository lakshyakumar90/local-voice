# 0.1.5 — local formatting and longer dictation

Date: 26 October 2026. Install as an upgrade over 0.1.4; do not uninstall, which removes local models and history.

## Delivered behavior

Smart formatting is on by default and runs entirely on the device. Settings → Dictation offers Original wording to bypass it. It handles ordered lists from consecutive sequence markers, explicit bullet commands, paragraph/line commands, spoken punctuation, conservative filler cleanup, restricted numeric corrections, and simple greeting/sign-off spacing. Longer prose gets paragraph grouping; unpunctuated long output also gets readable groups without inventing sentences. Quoted text, code, URLs, email addresses, meaningful repeated words and ordinary uses of “actually” are protected.

Formatting happens before Roman Hindi conversion. It never translates, summarizes or invokes a cloud endpoint. Requested leading/trailing paragraph breaks survive final insertion; a standalone line-break command can insert a break into an existing field. Structure-only commands are not automatically added to transcript history. Field validation and no-Send behavior remain intact. Lists use plain-text numbers/bullets and real newlines, not rich-text editor markup.

Recording now allows ten minutes. The recorder allocation, native input validation, timer/warning, processing timeout and original-field target lifetime were updated together. Cancel and screen-off cancellation remain. Ten minutes of 48 kHz capture can temporarily require about 58 MB for the short buffer plus 38 MB for resampled float audio, in addition to model/native/history buffers. No claim of phone memory or battery measurement is made.

## Recognition findings and changes

Public audio revealed defects that text-only tests could not catch. With the prior decoder configuration, Base could echo words from the invented Hindi example prompt and produce a long repetitive fragment. Removing the prompt exposed Base's tendency toward Urdu script on these clips. The final policy removes invented prompt sentences, restores normal entropy/log-probability fallback, retains a fixed Hindi transcription task, and excludes regular Arabic/Urdu UTF-8 lead-byte tokens during Hindi decoding. Latin stays available for English and Roman Hindi. Token masks are computed once at model load and applied by a lightweight native logits callback; the app does not run a full vocabulary regex at each decoding step.

Final delivery validation still rejects Arabic/Urdu output, unsupported mixed alphabets, English/Roman-only output in Hindi-script mode, and long within-word decoder loops. Pure punctuation/line-break commands are exempt from the Hindi-letter requirement. The app does not attempt to repair uncertain words by translating them or generating a plausible replacement.

These changes do not make Base or Small reliably accurate on every Hindi recording. On the two FLEURS clips, Small stayed in Devanagari but raw orthographic word-error rates remained about 53–56%. Base showed script and recognition failures. Formatting cannot fix that. The public samples are challenging read speech with specific vocabulary, not a benchmark of this user's everyday speech. Small is a model to try for Hindi, not a guarantee of correct words.

## Research and evidence

See [Wispr Flow research](WISPR_FLOW_FORMATTING_RESEARCH.md) for primary sources, feature mapping, commands and the distinction between this deterministic offline layer and Flow's cloud/model-based editing. This build does not add generative grammar repair, semantic rewriting, automatic personal dictionaries, snippets or app-specific writing styles.

Desktop testing uses the catalog's SHA-256-verified Base and Small Q5_1 files. Public sources: whisper.cpp's JFK clip and Google FLEURS hi_in validation rows 0/1, with reference transcripts and CC BY 4.0 attribution. A synthesized English command utterance exercises actual ASR → Kotlin list/paragraph formatting. Repeating JFK creates a 132-second constructed duration case; it is not independent natural long-form speech. The full ten-minute microphone lifecycle has not been run on a device.

Python desktop bindings strip segment whitespace; the harness restores separators before passing their output to Kotlin. Float-format FLEURS WAVs are decoded with FFmpeg. Desktop tests use one/two threads; Android uses up to four. The desktop script exclusion uses a byte-equivalent regex, whereas the app uses a precomputed native mask. A C++ test checks 2,431 code points, split/mixed BPE byte pieces and regex equivalence. Desktop inference times are not Android performance measurements.

The final build passes 52 JVM tests, lint with zero errors, signature checks and 16 KB ZIP/native alignment. Details and actual recognition failures are recorded in AUDIO_VALIDATION_0.1.5.md. No phone/emulator is connected here; Android capture, battery/thermal behavior, full ten-minute lifecycle, accessibility/IME insertion and rendered layouts still need phone verification. The user reports the previous editor-only flow works; this patch preserves that flow.

## Reproducing checks

Use JDK 17 and the existing Android SDK setup. The standard build runs core/app JVM tests, assembly and lint. To include actual-audio integration, populate a pipeline directory with the raw transcripts and provenance described in the validation report, then pass:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PaudioSmokeDir=C:/path/to/pipeline
```

Without that property, the four captured-audio transcript tests use committed raw text fixtures and provenance in core/src/test/resources/audio-smoke; default JVM tests do not rerun recognition. Recognition helpers are scripts/audio-smoke.py and scripts/compare-hindi-decoders.py; use an isolated environment with pywhispercpp 1.5.1, NumPy and FFmpeg. The latter can compare both models with --suppress-arabic. Source audio and downloaded model weights are ignored work files, not bundled with the APK or committed.
