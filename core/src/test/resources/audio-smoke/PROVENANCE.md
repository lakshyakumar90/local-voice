# Captured ASR text fixtures, 26 October 2026

These are actual desktop Whisper hypotheses captured during testing, not manually authored recognition results. No source audio or model weights are committed. Tests exercise final Kotlin formatting and rejection policies over these captured hypotheses; they do not rerun ASR.

- jfk.raw.txt: Base Q5_1, JFK sample from whisper.cpp commit 231638a0717b7822784297e5980965ffcec05810, English greedy decoding. Source: https://github.com/ggml-org/whisper.cpp/blob/231638a0717b7822784297e5980965ffcec05810/samples/jfk.wav
- english-long.raw.txt: Base Q5_1, 12 concatenated copies of that 11-second clip, constructed 132-second case.
- formatting.raw.txt: Base Q5_1; locally synthesized Windows speech saying first buy milk, second send the report, third call Alex, new paragraph, these are the tasks for tomorrow, new paragraph, thank you for your help. ASR misheard buy as by; that error is deliberately retained.
- hindi-0.raw.txt and hindi-1.raw.txt: Small Q5_1, Google FLEURS hi_in validation rows 0 and 1. Hindi transcription, beam 5, no example prompt, standard temperature fallback, regular Arabic UTF-8 token exclusion. Word-error ratios were 0.5333 and 0.5556; these are not accuracy-success fixtures. Tests check scripts and output handling. Source/attribution: Google FLEURS, https://huggingface.co/datasets/google/fleurs, CC BY 4.0 https://creativecommons.org/licenses/by/4.0/ . These hypotheses are modified machine transcriptions of those samples.
- base-prompt-loop.raw.txt: actual Base hypothesis for Hindi row 0 with the preceding example prompt and disabled fallback; contains a repeated nonsense fragment.
- base-mixed-alphabet.raw.txt and base-roman-only.raw.txt: actual Base hypotheses for Hindi rows 0 and 1 with the final constrained policy. They are rejected for Hindi-script delivery, not presented as successful recognition.

Desktop: pywhispercpp 1.5.1, NumPy, CPU; English one thread, Hindi comparison two threads. The binding strips segment whitespace, so the harness joins segments with a space. Its Arabic-byte regex is equivalent to the app's precomputed native mask; scripts/test-script-policy.cpp checks byte-policy equivalence. Android hardware/native build differs; no phone performance claim is made.

Weights match app/src/main/assets/models.json: Base SHA-256 422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898; Small SHA-256 ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb.
