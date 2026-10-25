# Implementation roadmap, testing and acceptance

Planning baseline: 2026-10-25. No implementation has started. Phase 0 documentation is delivered; approvals and listed experiments remain gates. Project root: `C:\Lakshya\wispr`. Device availability is not decided. Do not convert untested targets into marketing promises or mark physical-device tasks complete from emulator/source evidence.

## Phases and dependencies

| Phase | Deliverables / dependencies | Exit evidence and principal risk |
|---|---|---|
| 0 — Research and architecture | These eight documents; pinned source/license evidence; approvals for stack, recording activity and hardware; no app/model installation yet | Source/link/consistency checks pass; license discrepancies and blocked candidates recorded; real device procured before Phase 1. Source-level feasibility is not measured feasibility |
| 1 — Native end-to-end proof | Kotlin/Compose host, on-demand AudioRecord, manual installed Base model from verified authorized source, whisper.cpp JNI, Silero optional, visible recording activity + microphone FGS, user-controlled bubble in test mode | On a physical 4 GB ARM64 phone: start/stop/cancel, 10 s and 120 s clips, in-app transcript, airplane-mode operation, cleanup, load and peak PSS measured. Bubble may be manually enabled in this phase; no external insertion claim |
| 2 — Cross-app dictation MVP | Accessibility consent/service, eligible-focus bubble, target token, activity return/revalidation, one-shot guarded insertion and explicit Copy fallback; permission/recovery state machine | Complete flow without main-app reopening in supported editors; no loss of pre-existing text or accidental send; focus-change and permission-revocation tests pass. Recording activity is visible and documented; direct background mic start not assumed |
| 3 — Multi-engine/model system | Bundled registry, SHA-256/atomic installs, Range/resume/space handling, model switch/unload, Moonshine Tiny Streaming English adapter and source/AAR parity audit | Both genuinely different engines run from chosen local files offline on device; no hidden downloads/second mic; cross-engine benchmark report; English-only routing enforced. Missing SHA-256 or 16 KB incompatibility blocks second-engine distribution |
| 4 — Product polish | Original bubble states/accessibility, language UI, local encrypted opt-in text history, bounded model warming, fallback/result UX, error localization; hold gesture in owned recording UI | TalkBack/font scale/insets tests; retention/delete-all and key-loss tests; natural mixed-script Hindi/Hinglish review; automated integration suite. Bubble hold remains experimental until safe through activity transition |
| 5 — Optimization and release | Physical device/OEM matrix, battery/thermal profiles, corpus evaluation, native/SDK/weight notices, Play declarations/video/privacy policy, signed AAB/APK/release docs | All applicable release thresholds below met; compatibility list published; crash-free controlled sessions; Play approval for Play channel, signing/update compatibility resolved. APK validation is not Play approval |
| 6 — Expansion | Separate iOS input-extension feasibility; Windows integration; optional higher-quality models and later experiments | Independent platform threat/lifecycle/UX study; reuse contracts/fixtures where justified. No assumption Android overlays/background behavior port to iOS |

No calendar estimate is asserted without staffing/device capacity. The dependency order is intentional: demonstrate local recognition early, validate insertion before polishing, add a second engine before declaring the multi-engine release candidate complete. Obtain legal/policy clarification and packaging evidence before public distribution, not after a launch promise.

## Ten highest-priority implementation tasks

1. Lock the recording-activity permission path, proposed minimum SDK/ABI and the physical proof-of-concept device; write the lifecycle acceptance checklist.
2. Pin a stable Kotlin/Compose/AGP/NDK build compatible with API 36/37 and 16 KB pages; create dependency/license inventory and CI compilation/lint checks.
3. Define the session, PCM, ASR/VAD, model and text-insertion contracts from [Architecture](SYSTEM_ARCHITECTURE.md); implement deterministic state transitions with fakes.
4. Build visible RecordingActivity + microphone FGS + AudioRecord, measured first-sample feedback, immediate Stop/Cancel and the 120 s cap on a real phone.
5. Integrate whisper.cpp CPU/JNI with Base Q5_1 and FP16 comparator; verify no-network transcription and native cancellation/handle safety.
6. Add a user-controlled native bubble, then accessibility field detection with separate consent, sensitive-field rejection, exclusions and permission recovery.
7. Implement target-preserving final insertion, changed-focus protection, one-shot verification and explicit Copy fallback; test multiple keyboards/editors.
8. Implement verified resumable model download/install/delete/switch, disk checks and compatible installed fallback; extend tests to process interruption.
9. Integrate Moonshine Tiny Streaming English using caller-fed audio and the reviewed SHA-256 asset set; verify AAR/API parity, one mic, resource cleanup and offline behavior.
10. Run held-out English/Hindi/Hinglish and OEM/device benchmarks; tune conservative VAD; publish compatibility/performance evidence and complete release disclosures.

## Device and compatibility matrix

Use physical devices for performance and system integration. Emulators supplement API/permission and 16 KB checks but do not prove OEM/background/audio behavior.

| Tier | Proposed minimum sampling | Purpose |
|---|---|---|
| Low memory | One 3 GB ARM64 phone, oldest available supported Android | Stress/unsupported-model handling; passing speed on this tier is not an initial promise |
| Baseline | One 4 GB ARM64 phone on Android 12–14 | Initial quality/PSS/capture target; record exact SoC/RAM/OS/build |
| Midrange | One 6–8 GB phone, recent Snapdragon or MediaTek, Android 15/16 | Primary latency/energy gate |
| Flagship | One modern 8+ GB phone, Android 16/17 | High-end profile, thermal regression and current restrictions |
| Old API | Physical API 26–30 device if support is advertised; emulator supplements | Permissions/storage/runtime loading; if unavailable mark old physical support unverified and narrow release support |
| OEM spread | Stock Pixel, Samsung, and at least one Xiaomi/Redmi or Oppo/OnePlus/Vivo family | Recents, background restriction, service reconnect, Settings and microphone routes |

Use Gboard and at least one Samsung/SwiftKey keyboard, gesture and three-button navigation, small/large displays, portrait/landscape and split-screen. Test WhatsApp, Telegram, Chrome normal inputs + contenteditable, Gmail recipient/body, and a plain-text notes app. Record app/keyboard versions and field type. Test standard EditText and Compose test fixture fields, WebView, password, read-only, rich-text and deliberately inaccessible custom fields. Publish success/fallback/unsupported per editor rather than aggregating away failures.

Built-in mic is mandatory; test wired and Bluetooth input, connect/disconnect, incoming calls, audio focus interruption, mic privacy toggle and route sample-rate changes before advertising external-mic support. If Bluetooth routing permissions/APIs are not implemented, label it unsupported rather than assuming system routing works.

## Corpus and scoring protocol

Create a consented local corpus with **at least 600 utterances** from at least 12 speakers, balanced across English, Hindi and Hinglish (200 each). Include multiple Indian accents and genders, clean/noisy environments, names/places, technical vocabulary, short phrases, numbers, abbreviations, punctuation, 0.5–3 s natural pauses and 120 s sessions. Category labels can overlap. Use licensed public fixtures for CI plus consented private data stored outside release artifacts; record dataset licenses and collection consent.

Split 20% development/80% held-out by speaker, with at least eight speakers unseen in tuning. Record references before looking at candidate outputs. Two bilingual annotators review Hindi/Hinglish disagreements and script expectations. Include real phone microphone captures plus a fixed PCM set for fair engine comparisons; report those tracks separately.

Compute raw and normalized WER separately; normalization rules are versioned and frozen before the held-out run. Report Devanagari-aware CER and mixed-script WER, with a secondary transliteration-normalized diagnostic that must not hide wrong-script output. Track English/Hindi named entities, number preservation and catastrophic hallucinations separately. WER with ambiguous code-switch word boundaries cannot be the sole acceptance measure. Silence clips must not insert hallucinated text.

For each device/model/runtime build, measure a cold-load run after process/context reset, then at least 30 warm runs per 1/10/30/120 s length class where present. Keep decoding settings, thread count, language mode and PCM identical across comparable models. Begin CPU threads at min(4, available cores), then profile 2 vs 4 with thermal controls. Document battery level/temperature, charging state, screen brightness, route, background load and thermal status. Alternate model ordering and allow cool-down; do not benchmark only once on a charging flagship.

## Proposed acceptance thresholds — unmeasured

These are engineering gates to validate/revise after Phase 1; none is a measured result or guaranteed achievable. A miss requires a recorded ADR/product decision or further work, not relabeling the target as passed.

| Metric | Proposed gate | How to measure |
|---|---|---|
| First UI acknowledgement | p95 ≤100 ms from user action | Instrument action and first rendered state; independent of mic/model readiness |
| Warm action-to-capture | p95 ≤700 ms on midrange, ≤1.5 s baseline | First valid AudioRecord samples, not start-button animation; report cold load separately |
| Cold Base load | p95 ≤5 s midrange, ≤10 s baseline | Native init/warm-up elapsed; visible Loading; no recording before readiness |
| Base Q5_1 stop-to-final, 10 s utterance | p95 ≤5 s midrange, ≤10 s baseline | Stop timestamp to final normalized result; exclude insertion only in this metric |
| 120 s utterance completion | Real-time factor ≤1.0 midrange, ≤2.0 baseline | Decode wall time / audio seconds; report if cold load included; watchdog max 240 s |
| Moonshine partial/final | p95 partial within 1 s of sustained speech; final ≤2 s after stop | Source audio/sample timestamps + engine events; own surface only |
| Memory | Whole-app peak PSS ≤600 MiB W-B; ≤450 MiB M-TS; no OOM in matrix | Include Java/native/graphics and all app processes; context/load/decode peaks; no sum of file sizes |
| Cancel safety | Mic closes within 250 ms p95; native work stops or is safely quarantined within 2 s | Trace AudioRecord/FGS/native worker lifecycle; no post-cancel delivery |
| Model footprint | Default model ≤65 decimal MB including VAD; per-ABI compressed app ≤80 MiB initial, ≤120 MiB multi-engine | Exact release build bytes, separate model/native/UI totals; revisit gate if upstream libraries exceed it |
| English clean | Normalized held-out WER ≤15% | Same fixed normalization/corpus; report noisy English separately, target ≤25% |
| Hindi clean | Normalized WER ≤25%, CER ≤15% | Bilingual-reviewed references; no result claimed before measurement |
| Hinglish | Mixed-script WER ≤30%; ≥90% utterances rated meaning-preserving by two bilingual reviewers | Report script compliance and named-entity errors separately; revise product/model if failed |
| Safe insertion | ≥95% verified insertion on supported plain-text/editor matrix; ≥99% insertion-or-usable-fallback | Minimum 500 trials overall with app/field breakdown; fallback isn't counted as insertion |
| Data safety | Zero wrong-field edits, lost surrounding text, unsolicited send or sensitive-field insertion in test suite | Deterministic adversarial cases plus manual app trials; any observed failure blocks release |
| Reliability | Zero crashes/ANRs in 500 controlled sessions; release target ≥99.5% crash-free sessions once enough consented measurement exists | Controlled stress logs; statistical limitations stated; no transcript telemetry |
| Idle energy | No mic/wake lock when idle; ≤1 percentage point extra battery over an 8 h matched idle run | Paired device runs with/without enabled idle feature; repeat ≥3 times, report noise |
| Active energy | ≤3 percentage points per 10 min standardized session workload on midrange; no severe thermal state | Repeated fixed utterance/processing workload, fixed brightness; battery gauge is coarse, use power tools where available |

CPU utilization is reported as core-seconds and time series, not a universal pass percentage. Battery percentage points depend on battery capacity/health; record mWh when a calibrated power monitor is available. The quality/energy targets may conflict with low-end hardware; resolution is a published supported-model tier, not hidden cloud fallback.

## Automated tests versus physical tests

| Layer | Required scenarios | Environment |
|---|---|---|
| Pure unit | Session ordering/generations, double start/stop, language gating, timeout/cancel, safe whitespace/UTF-16 handling, model fallback selection | JVM CI with fakes |
| Audio/native | Resampler golden fixtures, sample continuity, channel conversion/clipping, silence/quiet-speech fixtures, native load/unload/abort, no use-after-free, repeated sessions | Native tests + Android instrumentation; real mic for route claims |
| Downloads | Resume valid Range, ignored Range/200 restart, wrong range/416, truncation/oversize/hash failure, changed ETag, low disk, pause/cancel/retry, corrupt bundle/path traversal, process kill, atomic old-version preservation | Mock HTTP server/filesystem + Android process tests; no real model download in ordinary unit CI |
| Insertion | Start/middle/end caret, selected text, emoji/Devanagari UTF-16, empty field, composition, changed focus/text, node reuse, rich content, false ack, disconnect and duplicate callback | Instrumented fixture apps + manual target-app matrix |
| Lifecycle/permissions | First deny/permanent deny/one-time mic, notification denial, overlay/accessibility revocation, privacy toggle, Recents, Task Manager Stop, low-memory kill, force-stop, reboot and locked screen | Instrumented emulator where possible + physical/OEM manual runs |
| Privacy | No audio/transcript/network requests during airplane-mode inference; no logs/backup content; history off/on/delete/expiry/key loss; clipboard marked sensitive and only explicit | Automated network/log/storage assertions + manual inspection |
| UX | TalkBack labels/focus order, 200% font, contrast/states, touch target, rotation/insets, drag vs tap, no bubble over sensitive/test system UI | Compose tests + physical accessibility review |

Normal CI uses tiny licensed synthetic audio and fake download files; real model/runtime benchmarks are opt-in device jobs with exact artifact pins. Do not add snapshots mirroring implementation as a substitute for behavioral tests. Run broader profiling only when a new engine/build/OS or regression warrants it.

## Release evidence and monitoring

Store future benchmark records under `work/benchmarks/<build>/<device>/` with source/model SHA, settings, latency, PSS, energy, accuracy and failures; curate content-free summaries into versioned documentation. Review the complete browser/editor → permission → mic → model → transcript → insertion/fallback flow, not isolated SDK demos.

Use staged internal/closed testing before public rollout; start a small Play rollout only after approval. With no analytics default, rely initially on controlled lab sessions and user-initiated redacted diagnostics; Play vitals, where available, supplies aggregate stability signals without our uploading transcripts. Define a release owner to pause rollout on wrong-field insertion, data exposure, crash/ANR spikes or model corruption. Preserve the last known good app/model versions; do not silently change model artifacts remotely. APK users receive clear manual update notes and signatures; no unapproved self-updater.

Before application implementation begins: approve the ADR items, confirm device access, verify the current toolchain/permission path and engine licenses, and accept that detailed phone performance remains a Phase 1 experiment. Before shipping: all exact distributed weights, checksums, dependency notices, native alignment, app compatibility and policy declarations must be cleared.
