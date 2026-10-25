# Architecture decisions and approval record

Date: 2026-10-25. “Proposed” means a recommendation for approval, not implemented/benchmarked. User-confirmed scope choices are recorded separately below. References: [Research](TECHNICAL_RESEARCH.md), [Model comparison](MODEL_COMPARISON.md), [Architecture](SYSTEM_ARCHITECTURE.md), [Policies](ANDROID_PERMISSIONS_AND_POLICIES.md).

## Confirmed user choices

- Project root `C:\Lakshya\wispr`; planning files in `docs/planning/`; future source/tests/build work remains inside this root.
- Google Play plus APK distribution.
- Natural mixed-script Hinglish, not a required Romanized-Hindi mode.
- Physical hardware not yet chosen; use a proposed 4 GB ARM64 planning baseline.
- Second initial engine may support English only; Hindi must remain covered by the first engine.
- MVP recording duration up to two minutes.
- This stage delivers research and eight planning documents, not product implementation. No models/dependencies installed during this stage.
- Design follow-up confirmed on 2026-10-25: light mode is the primary theme, with a companion dark scheme. [DESIGN.md](../../DESIGN.md) owns the proposed visual system; this supersedes the earlier dark-primary styling idea without changing capture/privacy architecture.

## ADR-001 — Native Kotlin and Compose

**Status: proposed.** Adopt Kotlin/Compose UI, Android services/AudioRecord/WindowManager, coroutines/StateFlow, DataStore, Room, OkHttp and WorkManager, with native inference adapters.

React Native/Expo custom development builds would retain familiar TypeScript for main UI, but overlay/accessibility/microphone/inference must still be native and independent of JS runtime availability. Expo Go cannot supply these integrations. Flutter has the same native-service burden plus platform channels/FFI. Kotlin minimizes independent lifecycles and glue for the core product. No unmeasured performance percentage is asserted. Consequence: budget time for Kotlin/Android lifecycle learning; no promise of shared Android/iOS overlay code. No Rust/Tauri desktop port.

## ADR-002 — Visible recording activity starts microphone service

**Status: proposed, high-impact approval item.** A bubble action opens a small visible recording activity; while resumed it validates permission/model readiness and starts a microphone FGS. It stays visible during capture/processing. The main app need not be reopened, but an owned activity temporarily takes focus.

Alternative: launch mic directly from the overlay/accessibility service. Rejected as a baseline because Android's FGS-launch and while-in-use microphone eligibility are separate; the currently documented overlay exemption does not alone establish mic eligibility. A notification action is a recovery entry, not an invisible auto-start. Do not keep an idle mic FGS alive as a workaround. Direct bubble recording requires a separately verified applicable exemption and device evidence.

Consequence: explain the interaction compromise in onboarding and test returning to the original editor. Bubble hold-to-talk may lose touch-up during an activity transition, so tap-to-toggle plus hold in the recording UI is the committed MVP. If this compromise is unacceptable, reopen the product decision before implementing cross-app recording.

Sources: [FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [background activity rules](https://developer.android.com/guide/components/activities/background-starts).

## ADR-003 — Independent ASR adapters, Whisper first

**Status: proposed.** Start with whisper.cpp + multilingual Base Q5_1 (~59.7 MB), Tiny Q5_1 optional, Small gated. Add Moonshine Voice + Tiny Streaming English (~45.23 MB) as a genuinely different runtime/architecture in Phase 3. One ASR context loaded at a time. Only engine modules know JNI/runtime details.

Alternatives: port all of Handy/transcribe.cpp, ship every available family, or use a cloud API. Reject the full desktop port: no verified maintained Android adapter at the inspected revision, desktop backend claims do not establish phone performance. sherpa-onnx is an established Android multi-model runtime and a strong later candidate, but shipping it immediately adds another runtime/license/package surface. Vosk is a credible low-resource Hindi comparator, not a proven Hinglish quality winner. No compulsory paid cloud fallback.

Consequences: initial Hindi/Hinglish quality depends on measured multilingual Whisper behavior. Moonshine cannot be the Hindi fallback. If Whisper misses quality/latency gates, evaluate Small, then Vosk Hindi or an independently verified sherpa Hindi-capable model; publish the trade-off and revise ADR before adding a third shipping engine.

## ADR-004 — Model format and immutable registry are explicit

**Status: proposed.** The registry identifies engine/artifact schema and immutable origin/version, supported languages, actual files/bytes/SHA-256, code/weight licenses and device recommendations. Bundled registry updates ship with app updates. Installed models live in app-private no-backup storage; partials are separate and installation atomic. No arbitrary model imports in MVP.

Handy's GGUF Whisper model is not whisper.cpp's custom GGML `.bin`. Moonshine uses ORT multi-file components. No file-extension-only compatibility detection or unconditional “all GGUF works” claim. Moonshine source currently offers CRC32C metadata; create reviewed SHA-256 manifest before shipping. CRC32C detects accidental errors but is not a cryptographic trust anchor.

## ADR-005 — VAD is optional, separate, and conservative

**Status: proposed.** Silero v6.2.0 through whisper.cpp VAD C API; bundle/download the verified ~0.885 MB model with attribution. Use 16 kHz, proposed threshold 0.3 and 450 ms context padding, preserve internal pauses. Manual Stop/120 s cap ends the session; VAD never alone terminates it. Disable cropping or retry full bounded audio if it harms completeness.

Alternative WebRTC VAD is a smaller-runtime candidate but needs wrapper/license review and noise/quiet-speech evaluation. Earshot is present in Handy but would introduce Rust integration without measured Android benefit. ASR, VAD, language ID and cleanup remain separate contracts; no language-ID model or LLM required for MVP.

## ADR-006 — Single-session bounded memory and native lifetime ownership

**Status: proposed.** Two-minute PCM16 mono buffer, serial inference executor, at most one recording and one ASR context; no PCM through JS or high-volume Binder copies. Retain raw PCM only until final result/cancel/error, in RAM only. Keep ASR warm at most 60 s idle, unload under pressure.

Whisper cancellation uses an atomic native abort flag; Moonshine adapter must demonstrate safe completion/cancel without free-while-running. Coroutine cancellation alone is insufficient. Native process isolation remains a mitigation if profiling shows unbounded hangs/crashes; it is not required without evidence, and no desktop worker executable is spawned on Android.

## ADR-007 — Guarded final insertion, explicit fallback

**Status: proposed.** Accessibility identifies a short-lived original target. After the recording activity closes, reacquire and validate before a single final text action. Preserve complete current text/selection; unknown rich/composing content or changed target uses fallback. Never send messages or blindly insert into a newly focused field.

`ACTION_SET_TEXT` replaces the value, so surrounding text/selection preservation is mandatory. A success acknowledgement is not always proof; do not auto-retry uncertain delivery. Copy requires an explicit action from a visible owned surface and is marked sensitive. No silent clipboard restoration or background clipboard scanning. No universal insertion claim; app/field compatibility list is a release artifact.

## ADR-008 — Private defaults and minimal persistence

**Status: proposed.** History off; raw audio never retained in MVP; no analytics SDK/account/backend. Opt-in history is text-only, encrypted with Keystore AES-GCM and seven-day default retention, excluded from backups, with delete/all/key-loss handling. Unsaved final text is memory-only and expires after five minutes with warning. Model download hosts still see ordinary request metadata; privacy copy must say so.

Postprocessing only preserves safe text representation/whitespace, with no filler removal, automatic transliteration, translation or generative cleanup. Optional cloud/LLM functionality would require a separate feature/consent design and cannot be necessary for core dictation.

## ADR-009 — Platform and distribution baseline

**Status: proposed.** API 26 minimum, ARM64 production, 4 GB RAM proposed minimum; target SDK 36/compile SDK 37 at this research date, rechecked at build time. CPU inference first; GPU/NPU optional only with measured device benefit and fallback. Test older supported Android physically before advertising it. Runtime support of an API level does not establish usable inference performance.

Google Play accessibility/FGS declarations, prominent disclosures, Data safety, signed release/update strategy and 16 KB native support are gates. APK distribution gets the same privacy/disclosure safeguards and OS restricted-settings guidance. App identity/signing compatibility is resolved before first public release; public brand and app-source license remain owner decisions, not implicit permission to reuse Handy identity.

## ADR-010 — Physical evidence, not desktop extrapolation

**Status: proposed.** Use the held-out English/Hindi/Hinglish corpus and device matrix in [Roadmap](IMPLEMENTATION_ROADMAP.md). All latency, PSS, battery, quality and insertion thresholds there are proposed. Record source/model revisions, formats, decode/thread settings and thermal state for each experiment. Cold/warm and partial/final measurements are separate.

No benchmark run was performed while creating these documents. A source sample proves an integration path exists; it does not prove release quality. A failed target must cause tuning, scope/tier adjustment or an explicit ADR revision; do not silently replace it with an invented result.

## Decisions requiring approval before product work

| Approval | Recommended choice | Effect of choosing differently |
|---|---|---|
| Android stack | Kotlin/Compose + native inference | Cross-platform UI choice changes build/runtime/module plan but still needs native services |
| Bubble-to-record UX | Visible compact recording activity, tap-first MVP | Direct overlay-only recording requires an applicable exemption/physical proof before commitment |
| Supported floor | API 26, ARM64, proposed 4 GB baseline; device matrix first | Lower RAM/32-bit support adds packaging/quality/performance validation; higher minimum narrows users |
| Engine sequence | Whisper Base Q5_1 then Moonshine Tiny Streaming English | A different second engine reopens native packaging, artifact and weight-license review |
| Retention | History off; text-only opt-in, seven days | Longer/default-on retention changes disclosures, encryption and deletion tests |

Public branding/app ID, source-code license and signing ownership need owner decisions before public distribution, but do not block a private package-neutral proof of concept. A specific physical phone and budget are prerequisites for making measured performance claims, not questions source inspection can settle.
