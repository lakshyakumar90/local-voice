# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Implemented foundation: Kotlin/Jetpack Compose, native Android services, Room/DataStore, C/C++/JNI Whisper inference and Moonshine's Android JNI runtime. See `docs/IMPLEMENTATION_STATUS.md` for verification and remaining device gates.

## Users

People dictating messages, email and notes with their existing Android keyboard, including English, Hindi and Hinglish speakers. The user reports testing on a Motorola Edge 60 Pro with Android 17; that device is not connected to this build environment.

## Product Purpose

User-triggered local voice typing into compatible text fields, with honest manual-copy fallback, no required account, and offline recognition after a chosen model download.

## Positioning

Model choice and local processing are central. A guided everyday experience exposes supported phone-tested models without assuming Handy's entire desktop catalog is Android-ready.

## Operating Context

Main setup/settings app, focused-field microphone bubble, non-focusable recording overlay, foreground microphone capture, local formatting, guarded insertion, and inline Copy fallback. In-app entry points retain their recording activity. Desktop/build checks cannot verify Android overlay lifecycle; the user reports the preceding editor-only flow works.

## Capabilities and Constraints

Current request extends the original two-minute MVP limit to ten minutes and adds offline list/paragraph/punctuation formatting inspired by Wispr Flow research. The formatting layer uses conservative text rules with an Original wording option; it does not provide generative rewording or cloud processing. English, Hindi script and Roman Hindi remain separate output choices. Proposed baseline: API 26, ARM64, 4 GB RAM; larger models and long sessions need phone measurements. Recognition, VAD, language identification and cleanup are different capabilities. No universal insertion, fabricated benchmarks, silent recording, required paid API or bundled download of every model.

## Brand Commitments

User confirmed on 2026-10-25: **light mode is the primary direction**; document a companion dark theme. Wispr Flow and Handy are research references, not branding licenses or affiliation. Public app name is undecided; `wispr` is only the project directory. Original visual identity is required.

## Evidence on Hand

Engineering planning documents in `docs/planning/`, attributed design references and a light-first specification, plus the native Android source and build scripts. Debug packaging and JVM behavior tests are available; phone performance and the final on-device composition remain unmeasured. Local Voice is a temporary independent app label.

## Product Principles

- Local-first, free core recognition and explicit model downloads.
- Keep the dictation action easy while making recording status unambiguous.
- Preserve surrounding text and language intent; fall back when uncertain.
- Surface language and device suitability before model popularity.
- State what is measured, proposed or unsupported honestly.

## Accessibility & Inclusion

Native Android conventions, minimum 48 dp targets, TalkBack semantics, system font scaling, English/Devanagari rendering, non-color recording/error cues and reduced motion. Android accessibility-service consent is separate from general usability accessibility.
