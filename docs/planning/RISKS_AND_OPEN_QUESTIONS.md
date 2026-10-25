# Risks, unresolved questions and requirement coverage

Date: 2026-10-25. This register distinguishes known limitations from future experiments. Owners below are role assignments for implementation, not claims that a team has been hired. Related: [ADRs](ARCHITECTURE_DECISIONS.md), [Roadmap](IMPLEMENTATION_ROADMAP.md).

## Ranked risk register

| Priority / risk | Evidence and impact | Mitigation / validation gate | Owner / due |
|---|---|---|---|
| P0 — Background mic eligibility | Overlay/FGS exemption does not by itself satisfy microphone while-in-use permission; direct-bubble promise may be infeasible | Commit visible RecordingActivity path; test launch, resumed state and mic on API 26–37/OEMs; notification/in-app recovery if blocked | Android lead, Phase 1–2 |
| P0 — Wrong-field/overwrite insertion | Focus can change during owned-activity transition; SET_TEXT replaces complete value; custom editors may expose partial text; accessibility has no atomic compare-and-set against racing external edits | Original target token, start/current salted text-digest and selection guard, immediate recheck, post-action verification, no automatic uncertain retry; zero wrong-field/lost-text failures in tests; residual race explicitly documented | Android lead + QA, Phase 2 |
| P0 — Hindi/Hinglish quality versus latency | Language support is not accuracy; small quantized models can fail names/scripts/code-switching | Held-out bilingual evaluation, FP16/Q5 comparison, optional Small tier; revise supported claims if targets fail | ML lead, Phase 1/3/5 |
| P0 — Native cancellation/handle safety | Coroutine cancel cannot abort JNI; native close while running can crash | Whisper abort callback; serialized Moonshine calls; cancel/timeout stress; quarantine on hung work; investigate Android process isolation if needed | Native/ML lead, Phase 1/3 |
| P0 — Play accessibility review | General productivity app is not automatically an accessibility tool; approval not assured | Separate affirmative disclosure, narrow data use/actions, isAccessibilityTool=false, accurate declaration/video and review gate | Release/privacy owner, Phase 5 |
| P1 — Weight/provenance mismatch | Handy catalog differs from Moonshine upstream legacy-language license terms; custom SenseVoice terms | Original weight cards/terms take precedence, exact artifact inventory; exclude unresolved/noncommercial artifacts from release | License owner, before model distribution |
| P1 — Model identity/integrity | GGUF/GGML/ORT incompatible; CRC32C insufficient as trust anchor; redirects/mutable downloads | Immutable origin + engine format/version + SHA-256 per file; no model install without reviewed manifest | Model-manager owner, Phase 3 |
| P1 — Moonshine artifact/version parity | Sample Maven 0.1.5 may not expose all current source-main APIs; bundled libraries may be misaligned | Inspect exact AAR/source tag/API, native dependencies/ABI, 16 KB page tests; source-build matched version if justified | Build/native lead, Phase 3 |
| P1 — Memory/thermal/battery | Weight file size isn't app memory; no device benchmarks performed | One ASR context, idle unload, 2 vs 4 threads, fixed thermal protocol, tiered models and PSS/energy gates | ML/performance lead, Phase 1/5 |
| P1 — OEM service loss | Recents/battery rules differ; users can force-stop/revoke | Honest state/status UI, reconnect only when allowed; no idle mic keepalive, no boot capture; physical OEM matrix | Android QA, Phase 2/5 |
| P1 — Sensitive-screen classification | Password metadata may be missing; FLAG_SECURE is not universally observable | Fail closed on unknown eligibility, package exclusions, no broad screen retention; document residual limitation | Android/privacy lead, Phase 2 |
| P1 — Recovery loses unsaved audio/text | RAM-only privacy means process death discards work; no history by default | Clear UI expectations, bounded processing, explicit Copy/result expiry; no covert persistence | Product lead, Phase 2/4 |
| P1 — Signing/updates across Play/APK | Different certs can prevent updates or cause separate-install expectations | Decide application ID/key ownership/Play App Signing before first public release; test upgrade/downgrade paths | Release owner, Phase 5 |
| P2 — Download hosts/availability | HF/CDN can rate-limit/change URLs; default downloads may select larger models | Pinned authorized endpoints, metered consent, resume/backoff, progress/errors; no unlicensed mirror; manual retry | Model manager, Phase 3 |
| P2 — VAD clips quiet/short speech | VAD can save compute while dropping legitimate sounds/pauses | Outer padding, preserve pauses, manual stop, VAD-disabled/full-audio comparison; no entire-session silence auto-stop | ML QA, Phase 1/5 |
| P2 — Bluetooth capture variability | Route, permissions and effective sample rate vary; mic helper may conflict with AudioRecord | One capture owner, actual-rate logging, interruption tests; advertise only tested routes | Audio lead, Phase 4/5 |
| P2 — New Android/policy changes | API 36 target required as of date; Android 17 and Play requirements evolve | Reverify official sources on implementation/release; current OS tests and dependency update policy | Build/release lead, every release |
| P2 — Branding and source license | Working directory name is not cleared branding; MIT dependency licenses do not choose app license | Distinctive public identity/trademark review; owner chooses application source license before publication | Product/release owner, Phase 5 |

## Questions resolved and remaining

Resolved with user: root directory, Play+APK distribution, mixed-script output, two-minute sessions, second engine may be English-only, hardware not yet selected. No repository existed when inspected; no source migration is planned.

Remaining product approvals: native Kotlin direction, the visible recording activity UX compromise, proposed API 26/ARM64/4 GB floor, engine sequence, and off-by-default history policy. Recommended choices and alternatives are in [ADRs](ARCHITECTURE_DECISIONS.md). These are not requests for permission to complete documentation.

Remaining engineering experiments: (1) activity launch/mic path and target return on physical OEMs; (2) real Base Q5_1 quality/latency on a chosen baseline phone; (3) exact Moonshine AAR/source parity and complete SHA-256 manifest; (4) native cancellation bound; (5) safe plain-text insertion scope and custom-editor fallbacks; (6) tested old-Android floor and 16 KB libraries. These cannot be resolved by source review alone. Phase 1–3 explicitly produces this evidence.

Distribution gates: original licenses/provenance for exact distributed weights and all AAR dependencies; app identity/source license/signing strategy; Play declaration/privacy video and review. Experimental models with unresolved licenses are not in the initial shipped registry. No assertion of Google Play approval, all-app insertion, indefinite uptime, or guaranteed script behavior is made.

## Before implementation checklist

- [ ] Owner approves the proposed ADR choices and visible activity compromise.
- [ ] Choose at least one physical ARM64 baseline phone; record SoC, RAM, Android and OEM build; reserve broader matrix access.
- [ ] Recheck target SDK, FGS/while-in-use, background activity and Play accessibility requirements at implementation date.
- [ ] Select a stable compatible build/NDK toolchain and exact runtime version/tag; retain source-revision/API-parity evidence.
- [ ] Carry the inspected MIT code/weight notices into the dependency/reuse inventory; exclude uncleared artifacts.
- [ ] Confirm session/engine/model/target contracts and acceptance gates before feature code.
- [ ] Agree that performance/quality numbers are targets until physical evidence exists, and that a failed target can require a narrower model/device tier.

The planning stage did not install dependencies or download weights. A controlled verified model download/build is authorized only as a subsequent product implementation task, not retroactively claimed here.

## Before release checklist

- [ ] Verified origin/revision/format/size/SHA-256, license and notices for every actual shipped/downloadable file.
- [ ] Both selected ASR engines proven offline, no hidden helper downloads and no second microphone owner.
- [ ] Physical capture/latency/quality/PSS/energy/thermal evidence meets approved thresholds or has an explicit ADR revision.
- [ ] Permission revocation, force-stop, process death, reboot and OEM restrictions tested with honest recovery.
- [ ] Zero wrong-field/lost-text/send/sensitive-field insertion defects in the required test set; usable manual fallback.
- [ ] History/temporary audio/clipboard/log/backup/network privacy tests pass.
- [ ] 16 KB native alignment and supported ABI/SDK matrix pass; AAB/APK signatures and upgrade path tested.
- [ ] Accessibility and FGS declarations/video, privacy policy and Data safety reflect actual behavior; Play approval for that channel.
- [ ] Public identity, app license and model attribution resolved; compatibility/limitations/support documentation published.

## Requirement traceability

This maps every numbered section of the supplied request to the primary deliverable; cross-links supply implementation detail.

| Request section | Coverage |
|---|---|
| 1. Product vision | [Product specification](PRODUCT_SPEC.md): audience, setup/cross-app journeys, offline/account-free defaults and explicit limits |
| 2. Handy source/reuse | [Technical research](TECHNICAL_RESEARCH.md): pinned source, dependencies, catalog, downloads/tests, VAD, streaming, supervisor, text/history and licenses |
| 3. Android capabilities | [Permissions and policies](ANDROID_PERMISSIONS_AND_POLICIES.md): permissions, two microphone eligibility checks, lifecycle matrix, clipboard/Play |
| 4. Technology stack | [Technical research](TECHNICAL_RESEARCH.md): Kotlin/React Native/Flutter comparison; ADR-001 rationale |
| 5. Pluggable ASR and VAD | [Architecture](SYSTEM_ARCHITECTURE.md): contracts/adapter boundaries; [Model comparison](MODEL_COMPARISON.md): families and separate roles |
| 6. Comparison matrix | [Model comparison](MODEL_COMPARISON.md): joined matrices, exact variants/formats/languages, sizes, memory/latency caveats, licenses/recommendations |
| 7. Model manager | [Architecture](SYSTEM_ARCHITECTURE.md): registry/install state/download recovery/integrity/storage/load/switch/fallback |
| 8. Pipeline/metrics | [Architecture](SYSTEM_ARCHITECTURE.md): ownership/rates/cancel/retention; [Roadmap](IMPLEMENTATION_ROADMAP.md): proposed measurable gates |
| 9. Floating UI | [Product specification](PRODUCT_SPEC.md): states, gestures, sensitive-screen limits, insets/keyboard/accessible controls |
| 10. Privacy/security | [Permissions and policies](ANDROID_PERMISSIONS_AND_POLICIES.md): separate disclosure drafts, minimization/encryption/retention/logs/release checks |
| 11. Modules/interfaces | [Architecture](SYSTEM_ARCHITECTURE.md): diagram, modules, contracts, Kotlin/C++/JNI boundaries and portability |
| 12. Testing | [Roadmap](IMPLEMENTATION_ROADMAP.md): devices/OEM/apps/keyboards/routes, corpus, metrics, proposed gates and automatic/manual tests |
| 13. Phases | [Roadmap](IMPLEMENTATION_ROADMAP.md): phases 0–6, dependencies, risks, exits, rollout and ten priorities |
| 14. Eight deliverables | Eight Markdown files in this directory; source evidence under `../../work/research/` |
| 15. Final checks/recommendations | [ADRs](ARCHITECTURE_DECISIONS.md), this register/checklists and [Roadmap](IMPLEMENTATION_ROADMAP.md): decisions, tasks and unresolved gates |

## Recommended conclusion

Native Kotlin/Compose best matches the Android integration burden. Begin with whisper.cpp multilingual Base Q5_1, then Moonshine Voice Tiny Streaming English as a distinct engine. Use independent conservative Silero v6.2 VAD, manual-stop-first sessions and no required cleanup/LID model. Propose Android 8/API 26, ARM64 and 4 GB RAM, with current API 36 target compliance and physical proof before support claims. The ten priority tasks are in [Roadmap](IMPLEMENTATION_ROADMAP.md). The principal unresolved risks are microphone-start/return-focus behavior, safe insertion, Hindi/Hinglish quality and phone resource use, Moonshine packaging/integrity, and Play approval. No application implementation has begun.
