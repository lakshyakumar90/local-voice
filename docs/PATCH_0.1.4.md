# 0.1.4 — editor-only dictation and Hindi decoding

Date: 2026-10-26. Upgrade over the existing app; do not uninstall, which removes local models and history.

## Hindi and English

The previous native adapter already set translate=false; there was no application translation stage to remove. The recognition model can still produce English paraphrases despite that task selection. This patch explicitly disables detection, validates the multilingual model for Hindi, adds short Hindi-script transcription context, and uses beam search (width 5) at temperature zero without higher-temperature fallback for both Hindi modes. Hindi is decoded in Devanagari first, then only mixed mode runs the existing deterministic Roman converter. English keeps separate English-language greedy decoding without the Hindi prompt.

Hindi-script mode rejects an English-only model result instead of inserting it. Mixed mode intentionally accepts English speech, so rejecting every English-only result would break that feature. This patch cannot establish that semantic translation has disappeared from mixed speech without device/audio inference tests. Base can still misrecognize words; Small is an available alternative, not a guaranteed fix. Beam search can take longer. No model or transcript is sent to a server.

## Input-field microphone

Removed activity launches from the accessibility field-mic flow, including error/insertion fallback. The gesture attaches non-focusable controls, starts the existing local session directly, then Stop processes and inserts once into the captured original field. No startup activity or automatic Copy dialog opens. Missing permission, unsupported editor, changed field or uncertain acknowledgement stays in a small inline feedback bar with explicit Copy/Done where appropriate. The app never automatically copies or presses Send.

Keyboard/system events now use the focused node's package identity rather than the event sender's package. Empty accessible text values are normalized to empty strings when the editor exposes a valid caret. A target-window lookup handles overlay/IME active-window transitions while preserving package, window, node identity, text digest, selection, protected-field and expiry checks. An uncertain insertion is never retried.

The bound accessibility service and visible overlay are retained for direct foreground-service startup. Android or OEM permission restrictions are still enforced; a denied start remains an inline error and does not force-open the app. Existing in-app/launcher/Quick Settings entry points retain their normal activity UI.

## Settings

Dictation shows one short description for the selected mode. Settings subpages have a mirrored back-arrow icon button with an accessible label and a Settings title.

## Verification

The combined core tests, app unit tests, debug assembly and lint command succeeded in 6 minutes 25 seconds: 26 tests pass, lint has zero errors and 16 warnings. APK signature verification, 16 KB ZIP alignment and every packaged native ELF's 16 KB LOAD alignment pass. The APK is versionCode 5, versionName 0.1.4, with ARM64 and x86_64 libraries and the existing debug signing certificate. This patch builds the debug APK; no new minified release build is claimed.

No Android phone or emulator is attached. On-device Hindi/English recognition, original-editor insertion, Motorola Android 17 microphone eligibility and rendered layouts still require device verification; no screenshot or speech benchmark is claimed.

Device acceptance: upgrade, select each mode and record a fresh utterance. In Hindi mode try “क्या तुम मुझे जानते हो”, and in mixed mode expect “kya tum mujhe jaante ho”. Try an entirely English phrase too. Focus an empty editor, start the field mic, stop, and confirm the app never becomes foreground and text lands at the caret. Repeat with a selected range, cancel, and a deliberately changed target field. A changed/unsupported field must offer inline recovery instead of overwriting it.

## References

- [Android foreground-service restrictions and while-in-use exceptions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [AOSP accessibility binding flags](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java): system binding includes background-activity-start permission and capability propagation. Direct-start eligibility is inferred from this binding and the foreground-service checks; OEM behavior has not been measured.
- [AOSP foreground-service binding checks](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/am/ActiveServices.java)
- Pinned local whisper.cpp source: task prefix explicitly selects transcribe when translate=false; native inference remains probabilistic.
