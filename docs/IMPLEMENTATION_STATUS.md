# Implementation status

Implementation date: 2026-10-25. Project root: `C:\Lakshya\wispr`.

This document records the initial implementation. Current behavior changes are in `PATCH_0.1.1.md` and `PATCH_0.1.2.md`: mixed mode uses Hindi-led recognition, Roman Hindi spelling includes medial schwa deletion, and unexpected Arabic/Urdu-script output is blocked before delivery. Android's separate Accessibility shortcut can be disabled while keeping the input-field bubble and app shortcuts.

## Implemented

- Native Kotlin/Compose light-first main app, companion dark/system themes, compact navigation and expanded navigation rail.
- Home readiness, model library, language selection, settings, accessibility disclosure, exclusions, history and a visible recording activity.
- Explicit microphone permission, microphone foreground service and notification, level meter, stop/cancel, screen-off cancellation and a two-minute recording bound.
- Actual whisper.cpp native inference; English, Hindi and automatic mixed-language modes. Optional Silero silence trimming with whole-audio fallback.
- Moonshine's Android JNI runtime, English-only gating and streaming partial previews. If streaming cannot keep up, the bounded recording is available for a final local batch pass.
- Four pinned model choices, explicit resumable HTTPS downloads with length/hash checks, Wi-Fi option, pause/cancel/delete and storage checks. Downloads and filesystem deletion are serialized per model.
- Final transcript copy/save/export, encrypted transcript payloads and optional encrypted recordings, history viewing/deletion and text/WAV export through the system document picker.
- Floating microphone, guarded accessibility insertion, manual-copy fallback, package exclusions, draggable position, Quick Settings tile, launcher/pinned shortcuts and an app-focused keyboard shortcut.
- Target checks cover package/window/field identity, original text digest, selection, focus generation and expiry. Partial text is never inserted; Send is never invoked.
- No backend, account, remote transcription or telemetry integration.

## Verification boundaries

The debug Android package and JVM tests are built on this workstation. The test suite covers selection preservation, invalid/unknown selections, emoji boundaries, Hindi spacing, language gating, resampling/alias rejection and verified HTTP transfer/resumption/failure cases. Native ELF load-segment and APK ZIP alignment are checked for 16 KB compatibility. See `VERIFICATION.md` for the final recorded commands and results.

No Android phone or emulator was connected during implementation. Compilation does not prove microphone operation, JNI recognition quality, UI appearance, accessibility insertion or background lifecycle behavior. No phone latency, battery, thermal, RAM or recognition-quality results are claimed. The reference concept image is a design artifact, not a screenshot of the running Android build.

## Decisions and limits

- The initial code uses three cohesive Gradle modules rather than prematurely splitting the plan into many modules.
- `Local Voice` is a temporary independent product label; branding has not been cleared for public release.
- Whisper is final-pass recognition. Moonshine supports English previews, with final-only delivery.
- Local AI performs recognition and optional silence trimming. There is no generative text-rewriting model: normalization only removes unsafe control characters and trims text. Language identification and mixed-script accuracy depend on the ASR model.
- Audio retention is an explicit optional addition to support saving recordings; default sessions retain audio only in bounded process memory. Saved recordings can be exported as WAV; in-app audio playback is not implemented.
- History content is encrypted, but database metadata is not. Seven-day pruning runs at app startup, not an exact timed secure-erasure service. Clear/delete removes files and rows; flash secure erasure is not promised.
- Accessibility writes have no atomic compare-and-set API. The code validates immediately before writing and checks afterward, but an external app can still race a write. Unsupported/unidentified/custom fields use Copy. The app does not implement a custom keyboard/IME.
- Ctrl+Shift+Space works only with the main app focused. Android launcher and Quick Settings shortcuts are the cross-app entry points.
- Model downloads require connectivity; downloaded inference does not. App-private files and backups are configured separately from user-selected exports.
- Native calls are serialized and cancellation-aware where supported. A stuck native operation may continue until it returns; new sessions are gated rather than freeing a live handle. Memory wiping is best-effort and cannot erase copies held by Android/native runtimes.

## Required device acceptance before release

1. Install on ARM64 Android devices including a 4 GB phone. Confirm first launch, model downloads, interrupted/resumed transfer, insufficient storage, switching/deleting models and airplane-mode recognition.
2. Test English, Hindi and mixed speech on all Whisper variants; test English Moonshine previews and final fallback. Measure peak memory, cold/warm load, real-time factor, battery, heat and transcription quality using consented audio.
3. Exercise deny/revoke microphone, notification and overlay permissions; screen lock, rotation, Home/back, incoming call, audio route changes, process death and the two-minute bound. Confirm mic indicator and notification always agree with actual capture.
4. Validate insertion in stock EditText and Compose fields, messaging/email/browser editors, selected text and middle-of-text caret positions. Test changing targets, same-view replacement, passwords, excluded packages, composing text, window changes and manual-copy fallback. Ensure no automatic Send.
5. Verify Keystore-backed storage, optional audio retention, reboot, expiration, deletion, export failures and backup exclusions on actual Android storage.
6. Inspect real Compose screens in light/dark modes, TalkBack, 200% text, large display scaling, narrow/landscape/tablet layouts, keyboard insets and bubble placement. Fix screenshots against the design specification.
7. Audit all native/transitive and model licenses, release signing, Play accessibility policy/declarations, privacy wording and public app identity before distribution.
