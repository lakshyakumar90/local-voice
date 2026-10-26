# Local Voice — Android

An offline-first Android voice typing app built in Kotlin and Jetpack Compose. `Local Voice` is a temporary independent name; Wispr Flow and Handy are research references, with no affiliation.

## Try it

1. Build and install the debug APK, then open Local Voice.
2. Open **Models** and explicitly download Whisper Base Q5_1 (recommended starting point for English, Hindi and mixed speech). Downloads require a connection; recognition runs on the phone afterward.
3. Tap **Try dictation**, grant microphone permission, speak, then stop. Copy, save or export the resulting text.
4. For other apps, open **Settings → Microphone in other apps**, read the accessibility disclosure, grant overlay permission and enable the service. Tap the mic beside a focused field. A non-focusable recording bar stays over the editor without launching the app. Stop transcribes and inserts into the original validated field; changed or unsupported fields offer Copy in the same bar.
5. Focus a supported input field to show the microphone bubble. If Android shows its own separate Accessibility shortcut button, turn off only **Local Voice shortcut / Accessibility button** in system Accessibility settings; keep the service enabled for the input-field bubble. The launcher, keyboard and Quick Settings app shortcuts remain available.

Recording is explicit and limited to ten minutes. In-app entry points use a recording activity; the field mic starts the foreground service from the bound accessibility flow with visible overlay controls. The app never presses Send. Password fields and excluded apps are not eligible for insertion.

**English + Roman Hindi** uses Hindi-led Whisper recognition and converts recognized Devanagari into Roman letters. Select **English** for English-only recordings. Hindi uses a fixed transcription task, beam search, a regular Arabic-token exclusion and final script validation. Hindi-script mode rejects English/Roman-only output. Recognition and Roman spelling can still make mistakes; public Hindi tests show substantial remaining word errors. See `docs/PATCH_0.1.5.md` and `docs/AUDIO_VALIDATION_0.1.5.md` for evidence and limits.

**Settings → Dictation → Smart formatting** adds local numbered/bulleted lists, paragraph breaks, spoken punctuation, light filler cleanup and restricted numeric corrections. Say “first… second…”, “bullet point”, “new paragraph” or “new line”; supported Hindi commands work before Roman conversion. Long prose is grouped into readable paragraphs. Choose **Original wording** to bypass formatting. This is a local rule-based layer, not a generative rewrite model. See `docs/WISPR_FLOW_FORMATTING_RESEARCH.md` for research, examples and boundaries, and `docs/PATCH_0.1.5.md` for tests.

Version 0.1.6 also recognizes natural requests such as “I want these features: UI, front end, back end, database” as a bulleted list. The floating mic uses audio-responsive listening dots above its timer. See `docs/PATCH_0.1.6.md`.

## Local models

| Choice | Purpose | Download |
|---|---|---:|
| Whisper Tiny Q5_1 | Smallest multilingual option | ~32 MB |
| Whisper Base Q5_1 | Default multilingual starting point | ~60 MB |
| Whisper Small Q5_1 | Larger multilingual option | ~190 MB |
| Moonshine Tiny Streaming English | English-only partial previews | ~45 MB |
| Silero VAD | Optional Whisper silence trimming | <1 MB |

The catalog pins artifact sizes and SHA-256 hashes. Downloads support verified resumption, pause/cancel and explicit network choice. Models are not bundled with the APK. Device suitability and accuracy still need phone measurements; recommendations are starting points, not benchmark claims. Moonshine previews stay inside the recording UI; insertion uses the final result.

## History and recording storage

Automatic history and audio retention are off by default. **Save** explicitly keeps a transcript locally. Optional audio retention requires separate consent and enables history. Transcript payloads and retained WAV files use Android Keystore-backed AES-GCM encryption; metadata such as date, model and duration remains in Room. History older than seven days is pruned when the app starts. Android backup is disabled.

History supports full-page transcript details, in-app play/pause/seek for saved recordings, explicit Copy, text/WAV export, individual deletion and clearing all history. Playback uses decrypted memory, without a plaintext playback file. Text-only entries cannot recover audio that was never saved. Enable **Settings → History & recordings → Keep recordings with history** for future recordings. Exports use Android's document picker; exported files are outside app retention rules. Unsaved results expire from app state after five minutes. The app has no account, analytics service or cloud inference endpoint. Internet permission is used for requested model downloads.

## Build

Requirements: JDK 17, Android SDK platform 37, build-tools 37.0.0, NDK 27.1.12297006, CMake 3.22.1, Git and network access for first-time dependencies. Minimum Android version is 8.0 (API 26); target API 36. Native binaries are built for ARM64 phones and x86_64 emulators.

```powershell
cd C:\Lakshya\wispr
.\scripts\bootstrap-native.ps1
.\scripts\build.ps1
```

Set `JAVA_HOME` to a JDK 17 installation and `ANDROID_HOME` to the SDK (or set `sdk.dir` in an untracked `local.properties`). The build script can find the installed Temurin 17 path on this workstation. The Gradle wrapper pins Gradle 9.4.1. Bootstrap checks out the pinned whisper.cpp commit in ignored `vendor/whisper.cpp`.

Debug output: `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug
```

`scripts/build.ps1 -Release` builds an unsigned, minified release APK. Supply your own release signing configuration before distribution; no private signing key is committed.

## Source map

- `app`: Compose screens, Android permissions/services, model management, encrypted history and exports.
- `core`: Android-independent contracts, language gating, text insertion validation and audio resampling.
- `engine`: Whisper C++ JNI adapter and Moonshine Android JNI adapter.
- `DESIGN.md`, `docs/design/`: light-first design specification, attributed visual references and concept image.
- `docs/planning/`: original plan and model research.
- `docs/IMPLEMENTATION_STATUS.md`: actual verification, limitations and phone acceptance checklist.

This is an implemented development build, not a validated public release. Read the implementation status before relying on cross-app behavior or device performance. Third-party notices are included in the app; upstream licensing and Play accessibility disclosures need a distribution review.
