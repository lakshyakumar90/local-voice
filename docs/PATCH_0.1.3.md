# 0.1.3 — editor recording controls, focused settings and audio history

Date: 2026-10-26. This implements the user's UI feedback, preserving the light-first palette and existing local model/storage settings.

## Editor dictation

Tapping the eligible input-field mic opens a compact, non-dimmed microphone-start handoff. Once actual capture starts and the floating controls are attached, the activity finishes and returns focus to the editor. A non-focusable, draggable recording bar provides elapsed time, Stop and Cancel. Processing replaces Stop with a local transcription status; Cancel remains available. The full recording dialog no longer stays open during normal input-field dictation.

A visible startup step remains for Android permission/foreground-service eligibility and model loading; cold model loading can take longer. It is not a transparent activity or an attempt to bypass microphone restrictions. If controls cannot attach, the visible recorder remains available. In-app and explicit shortcut recording keep their owned recording interface.

The microphone service switches to the dataSync foreground-service type for local processing after AudioRecord stops, with a processing notification and Cancel action. It stops after result/error/cancel. The normal `FOREGROUND_SERVICE_DATA_SYNC` permission is added; no new sensitive runtime permission or remote synchronization is introduced. Android explicitly includes local file processing in this type: [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), checked through Agent Reach's Jina reader.

Final text is automatically inserted only into the original validated editor. Node identity now uses the Android node's equality semantics, including fields without a resource ID, and geometry changes alone no longer invalidate it. Package/window/class, text digest, caret/selection, focus generation, password/exclusion, expiry and supported-action checks remain. Nodes are released at completion/cancel/expiry. No Send action is invoked. An uncertain or unsupported insertion still opens the explicit Copy fallback; automatic writes cannot be made atomic against another app's edits.

## Settings

The long single page becomes a six-topic list: Dictation, Appearance, Microphone in other apps, History & recordings, Shortcuts, Privacy & about. Individual pages hold their relevant controls, and app exclusions are separate from basic mic setup. Existing permission disclosures, opt-in recording storage and troubleshooting remain available. Entering a section resets its scroll position.

## History and playback

Selecting a saved transcript opens a full content page under the app bar instead of an AlertDialog. Date/model/language, audio controls, transcript and actions have distinct vertical groups. Copy, Export text and Export recording use full-width buttons; Delete is separated and requires confirmation. Back returns to History; there is no competing Done button in a cramped dialog.

Entries containing audio expose play/pause, seeking and elapsed/total time. Playback decrypts the existing encrypted WAV into a bounded, owned in-memory MediaDataSource; it does not write a plaintext playback file. It requests transient audio focus, pauses on focus loss/app backgrounding and releases the player/source on leaving the detail view. Closing the source wipes the owned audio byte buffer. Native/runtime copies are outside that best-effort wipe.

Text-only entries explicitly state that no audio was saved and link to recording-storage settings. Existing missing audio cannot be reconstructed. Audio retention remains an explicit opt-in for future dictations; enabling playback does not silently turn on recording storage or modify old entries.

## Verification and limits

Final `:core:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` verification succeeded in 1 minute 51 seconds. All 24 tests pass, including two new tests for random-access/partial/EOF playback reads and wiping/refusing reads after close. Lint reports zero errors and 16 warnings (dependency/API/style/localization suggestions plus the existing bubble drag-listener warnings). APK signature, same-certificate upgrade and 16 KB ZIP alignment checks pass. The packaged manifest includes the processing foreground-service permission and supports ARM64/x86_64. This is a debug APK; release 0.1.3 was not rebuilt.

No phone or emulator was attached (`adb devices -l` empty). Real screenshots, overlay focus/IME behavior, microphone handoff, processing lifecycle, audio playback/seek/focus and large-font layouts have not been executed on Android. The earlier design concept remains an illustration, not evidence of this build's rendering. Required device acceptance: light/dark and large text; start/stop/cancel/lock; original-field insertion and unsupported editors; headphone/call/background playback; saved-audio export/delete and text-only history. No audio/model accuracy changes are part of this patch.
