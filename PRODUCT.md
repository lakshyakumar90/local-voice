# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Implemented foundation: Kotlin/Jetpack Compose, native Android services, Room/DataStore, C/C++/JNI Whisper inference and Moonshine's Android JNI runtime. See `docs/IMPLEMENTATION_STATUS.md` for verification and remaining device gates.

## Users

People dictating messages, email and notes with their existing Android keyboard, including English, Hindi and Hinglish speakers. No physical baseline device has been chosen.

## Product Purpose

User-triggered local voice typing into compatible text fields, with honest manual-copy fallback, no required account, and offline recognition after a chosen model download.

## Positioning

Model choice and local processing are central. A guided everyday experience exposes supported phone-tested models without assuming Handy's entire desktop catalog is Android-ready.

## Operating Context

Main setup/settings app, focused-field microphone bubble, compact visible recording activity, foreground microphone capture, final guarded insertion, and explicit Copy fallback. The activity is the current engineering baseline; a pure overlay recording path remains unverified.

## Capabilities and Constraints

Confirmed earlier: Play plus APK distribution; natural mixed-script Hinglish; two-minute MVP sessions; second engine may be English-only. Proposed baseline: API 26, ARM64, 4 GB RAM. Recognition, VAD, language identification and cleanup are different capabilities. No universal insertion, fabricated benchmarks, silent recording, required paid API or bundled download of every model.

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
