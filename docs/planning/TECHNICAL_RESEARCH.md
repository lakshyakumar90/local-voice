# Technical research and source evidence

Access date: **2026-10-25**. This is source/documentation research, not a successful Android build or device benchmark. Facts below refer to the pinned main-branch snapshots, not necessarily the latest published binary.

## Evidence method and reproducibility

Used the Agent Reach skill's Exa search through `mcporter`, Jina Reader for web documentation, and public upstream GitHub/Hugging Face APIs/raw source. GitHub CLI required unconfigured authentication; Jina's github.com backend returned an abuse-block response. Public upstream APIs/raw endpoints supplied source without changing credentials. One Jina API-reference request failed; the official accessibility guide supplied the relevant documented workflow. Firecrawl was unnecessary.

Fetched source and metadata only: no model weights, installation, dependency build, or product implementation. Raw research inputs are in `../../work/research/` with URL/status/access-date indexes; source code there belongs to upstream reference material. Missing guessed paths are recorded as failures and are not evidence of support. Exa results were used for discovery and checked against primary sources before substantive claims.

| Project | Snapshot inspected | Evidence |
|---|---|---|
| Handy main | `f81e7e70fb837a2d24db96ad69abc7969762876a` | [Tree](https://github.com/cjpais/Handy/tree/f81e7e70fb837a2d24db96ad69abc7969762876a) |
| transcribe.cpp main | `8facde6fcd8fb9caa585a5788ab450df6e8ec7db` | [Tree](https://github.com/handy-computer/transcribe.cpp/tree/8facde6fcd8fb9caa585a5788ab450df6e8ec7db) |
| whisper.cpp master | `231638a0717b7822784297e5980965ffcec05810` | [Tree](https://github.com/ggml-org/whisper.cpp/tree/231638a0717b7822784297e5980965ffcec05810) |
| Moonshine main | `234f60faa0eb388b01cdf7e60aca232af37aefda` | [Tree](https://github.com/moonshine-ai/moonshine/tree/234f60faa0eb388b01cdf7e60aca232af37aefda) |
| sherpa-onnx master | `532f1b5a8a207bf53b116b747207c1506696c843` | [Tree](https://github.com/k2-fsa/sherpa-onnx/tree/532f1b5a8a207bf53b116b747207c1506696c843) |

## Handy implementation, beyond the README

### Architecture and engine selection

The inspected [Cargo manifest](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/Cargo.toml) is a Tauri 2/React desktop application with Rust orchestration. `Cargo.lock` resolves `transcribe-cpp` 0.4.0, `handy-recorder` 0.1.0, and `earshot` 1.2.2. These are source-snapshot dependencies. The latest release inspected, [v0.9.8](https://github.com/cjpais/Handy/releases/tag/v0.9.8), was published 2026-10-03 and its notes refer to transcribe-cpp 0.2.4. Do not equate the release with current main.

All current inference routes through transcribe-cpp; older accounts of multiple separate ONNX engines do not describe this snapshot. The [catalog](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/catalog/catalog.json) schema is 2, with engine minimums, exact HF repositories/revisions, model family/role, languages, capabilities, quantizations, file bytes and SHA-256. It includes ASR, language-ID, VAD, and diarization roles, which must not be presented as equivalent transcription engines.

The catalog contains Whisper, several Parakeet variants, Moonshine batch/streaming, Canary, Qwen3-ASR, SenseVoice, Voxtral, Cohere, and other families. Support in this catalog is a desktop integration fact, not our Android deployment claim. Example distinctions: Parakeet v2 is English; v3 supports 25 European languages without Hindi; unified English streams; Nemotron-family entries have separate coverage and licenses. A Parakeet-family label does not establish identical languages or streaming behavior.

The [model manager](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/managers/model.rs) supports direct HTTP, revision-pinned Hugging Face sources/shared cache, and local models; it probes GGUF metadata/capabilities. The loaded engine's capabilities guide actual transcription behavior. Do not load Handy's GGUF Whisper artifacts in whisper.cpp: its custom GGML `.bin` format is different.

### Downloads and verification

The [HTTP downloader](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/managers/model/download.rs) preserves resumable partials, requests byte ranges, validates the returned range, restarts if a server ignores Range with HTTP 200, handles 416 conservatively, verifies configured SHA-256, reports progress/rate, and detects stalls/cancellation. Hash mismatch deletes the partial. Hash verification is optional for some upstream source types; our curated registry makes cryptographic hashes mandatory for shipped artifacts.

The [downloader tests](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/managers/model/download/tests.rs) exercise good/bad hash, valid/invalid range, ignored range, full-size partial, length conflicts, oversized bodies, cancellation before headers and during stalled bodies. These are reusable test scenarios; they were inspected, not executed here.

### Audio and VAD

The [audio manager](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/managers/audio.rs) delegates capture to handy-recorder. Source/tests specify 16 kHz mono output with 480-sample chunks and channel mixing, readiness generations, recording states and cancellation generations. Desktop microphone modes include AlwaysOn and OnDemand; our Android design uses OnDemand only. The capture sink owns speech routing and levels independently of UI.

The [VAD abstraction](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/audio_toolkit/vad/mod.rs) separates detector, scorer, and smoothing. Silero uses transcribe-cpp's VAD session and bundled `silero-vad-v6.2-F32.gguf`; Earshot is an alternative. At the snapshot, Silero uses 32 ms frames and a 0.3 threshold, Earshot 16 ms/0.5. Time-based smoothing has 450 ms prefill, 450 ms offline hangover, 1650 ms streaming hangover, and 60 ms onset. State resets between sessions. These values are desktop implementation facts, not validated Android/Hindi defaults.

### Streaming, concurrency, memory, and cancellation

The [decode policy](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/managers/transcription/policy.rs) selects native streaming when advertised, otherwise optional long-form chunking, otherwise batch. Whisper is treated as long-form capable, not native streaming. [Live decode](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/managers/transcription/live.rs) emits committed/tentative preview text; stream failure or empty final text can fall back to batch with retained audio. Cancellation remains distinct from failure.

The [supervisor](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/engine_supervisor/supervisor.rs) serializes native work through a worker process, tracks cancellation epochs, timeouts, load/unload, backend probing and worker crashes. Cancellation can terminate the worker; this is not equivalent to canceling a Kotlin coroutine around JNI. The protocol carries load/run/stream/feed/finalize/reset, PCM, and device metadata. Desktop process-spawn behavior must be redesigned for Android services, not copied.

Per-target Cargo configuration enables Metal on macOS, Vulkan/dynamic backends on Linux and Windows x86_64, and CPU-only Windows ARM. No equivalence to Android GPU/NPU acceleration is established. Catalog benchmark methodology names FLEURS and a Ryzen 4750U CPU/Vulkan rig; these scores do not establish Android speed or RAM. Model unload timers exist; Android memory pressure needs separate measurement.

### Languages, text, history, and settings

The [transcription manager](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/managers/transcription.rs) gates language/translation against loaded capabilities, tracks selected/detected/model-constrained language evidence, normalizes output, and optionally applies custom words, language-gated filler removal, and Chinese script conversion. A separate VoxLingua107 ECAPA catalog entry is language identification, not ASR. Do not silently adopt Handy's unsupported-language fallback for an explicit Hindi request; fail visibly instead.

The [text utilities](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/audio_toolkit/text.rs) include fuzzy custom-word matching, filler/stutter cleanup and normalization. Release fixes concerning “Ha” demonstrate why language-sensitive cleanup can remove legitimate speech. Our MVP keeps lexical cleanup off and uses only conservative whitespace/Unicode handling.

The [history manager](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/src-tauri/src/managers/history.rs) uses SQLite migrations and recording files, with original/postprocessed text and metadata. Settings are persisted through the desktop stack. Our product intentionally chooses history off, text-only opt-in storage, and no retained raw audio. Tauri UI, native capture/inference, and desktop paste/shortcut integrations are separate boundaries worth preserving conceptually.

## Underlying runtimes versus realistic Android integration

[transcribe.cpp](https://github.com/handy-computer/transcribe.cpp/blob/8facde6fcd8fb9caa585a5788ab450df6e8ec7db/README.md) exposes a C API and several language bindings over C++/GGML/GGUF. Its inspected build guide documents desktop native backends; this research did not establish a maintained Android JNI/AAR integration. Android NDK CPU compilation is a plausible engineering experiment, not a verified production path. Vulkan/Hexagon code within dependencies does not establish working device support, weight compatibility, or acceptable thermals.

[whisper.cpp's Android sample](https://github.com/ggml-org/whisper.cpp/tree/231638a0717b7822784297e5980965ffcec05810/examples/whisper.android) establishes a native Android deployment path and recommends Tiny/Base. Its [header](https://github.com/ggml-org/whisper.cpp/blob/231638a0717b7822784297e5980965ffcec05810/include/whisper.h) exposes abort callbacks and independent VAD APIs. Use CPU/ARM64 first. Vulkan is an optional profiled backend, not an MVP dependency; Apple's Metal/Core ML and desktop OpenVINO figures are irrelevant to baseline Android.

[Moonshine's Android Transcriber](https://github.com/moonshine-ai/moonshine/blob/234f60faa0eb388b01cdf7e60aca232af37aefda/language-bindings/android/java/main/java/ai/moonshine/voice/Transcriber.java) exposes explicit local loading, caller-supplied float audio/sample rate, stream management, and deterministic close. Its sample references Maven `ai.moonshine:moonshine-voice:0.1.5`, but source main may contain newer methods than that artifact. Verify published-AAR/source parity, transitive native libraries, and 16 KB alignment before choosing the integration version. Do not use a helper that opens a second microphone or downloads a default model implicitly.

[sherpa-onnx Android](https://k2-fsa.github.io/sherpa/onnx/android/index.html) has maintained examples and JNI integration for multiple ASR families. [Vosk Android](https://alphacephei.com/vosk/android) provides another established local path. Neither runtime confers its own license or language coverage on every supported model. See [Model comparison](MODEL_COMPARISON.md).

## Stack comparison and recommendation

| Dimension | Kotlin + Compose | React Native + Expo development tooling | Flutter |
|---|---|---|---|
| Overlay/accessibility | Direct Kotlin services and WindowManager | Same Kotlin components plus native module bridge; Expo Go insufficient | Same Kotlin services through platform channels |
| Background recording | Native lifecycle/state machine | Must function without JS runtime; bridge cannot own capture lifecycle | Must function without Dart UI isolate |
| Inference | Direct JNI/CMake and Java adapters | Same native adapters; keep PCM off JS bridge | FFI/platform adapters; avoid per-frame channel traffic |
| Memory/performance | One UI runtime plus inference | Additional JS/Hermes runtime and bridge startup | Additional Flutter engine/isolate |
| Debugging | One Android UI/service toolchain plus native profiler | Android + JS lifecycle/toolchain | Android + Dart + FFI lifecycle/toolchain |
| Maintenance | Lowest integration surface for this platform-heavy product | More glue/codegen/build compatibility | More channel/FFI/plugin compatibility |
| Existing TS experience | Kotlin learning cost | Familiar settings/onboarding UI | Dart learning cost |
| Future iOS | Reuse contracts/fixtures/native inference selectively | Can share main UI; iOS keyboard/system integration remains separate | Can share UI; same iOS restrictions |

Recommend Kotlin, Compose, coroutines/StateFlow, DataStore, Room, WorkManager for nonurgent downloads/maintenance, AudioRecord, and CMake/JNI for whisper.cpp. Native inference and OS integration dominate the difficult work. React Native would save learning effort mainly in onboarding/settings, not overlay, accessibility, microphone, download lifecycle or native inference. No credible like-for-like measurements justify a fabricated MB/percent overhead estimate; prototype and measure if revisiting. Rust is unnecessary for the MVP; no Tauri desktop port.

## Official Android conclusions

- [Background FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) and [microphone service type](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone) impose independent start/while-in-use checks. A visible overlay exemption for an FGS launch is not itself a microphone exemption.
- Use a user-initiated visible recording activity to start the microphone FGS, not a hidden trampoline. [Background activity rules](https://developer.android.com/guide/components/activities/background-starts) permit specific cases including overlay special access, with evolving PendingIntent opt-ins. Validate on target OS/OEMs; a notification action is an explicit recovery route.
- [Accessibility guide](https://developer.android.com/guide/topics/ui/accessibility/service) and [actions reference](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction) support discovery/actions, not unrestricted input injection or guaranteed custom-editor access.
- [Play accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491) requires declarations and, for this general productivity app, separate prominent disclosure and affirmative consent. Do not declare `isAccessibilityTool=true` on the basis that dictation is convenient.
- [Current target API policy](https://support.google.com/googleplay/android-developer/answer/11926878) says API 36 for new phone apps/updates from August 31, 2026. Plan `targetSdk=36`, `compileSdk=37` for current API checks, `minSdk=26`; reverify at build/release. Review [Android 17 changes](https://developer.android.com/about/versions/17/behavior-changes-17) and all-app changes separately; do not confuse target-specific behavior with device behavior.
- All native dependencies must pass [16 KB page-size requirements](https://developer.android.com/guide/practices/page-sizes), not just the app's own JNI library.

Detailed lifecycle, clipboard, and Play requirements are in [Permissions and policies](ANDROID_PERMISSIONS_AND_POLICIES.md). No approval by Google Play or device compatibility has been obtained.

## Reuse and license obligations

[Handy MIT](https://github.com/cjpais/Handy/blob/f81e7e70fb837a2d24db96ad69abc7969762876a/LICENSE) credits `Copyright (c) 2025 CJ Pais`. Preserve the copyright and full permission/disclaimer notice in copies/substantial reused portions, including distributed notices. It does not require publishing our app's source. Preserve modifications/provenance in a reuse ledger; app license remains a separate owner decision. Avoid direct source reuse in the first Android implementation; independently reproduce abstractions and downloader test cases.

[transcribe.cpp notices](https://github.com/handy-computer/transcribe.cpp/blob/8facde6fcd8fb9caa585a5788ab450df6e8ec7db/THIRD-PARTY-LICENSES.md), whisper.cpp, Moonshine and native/transitive dependencies each need their own notices. Apache-2.0 requires license/attribution/NOTICE where supplied and marking modified files; CC-BY weights require attribution and indication of conversions/changes. No code license automatically grants rights to every weight. A notable discrepancy: Handy's catalog labels legacy non-English Moonshine weights MIT, while [Moonshine's upstream license](https://github.com/moonshine-ai/moonshine/blob/234f60faa0eb388b01cdf7e60aca232af37aefda/LICENSE) enumerates Community-license exceptions. Prefer verified upstream terms and exclude those artifacts from initial distribution.
