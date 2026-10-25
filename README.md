# Local Voice — Android

An offline-first Android voice typing app built in Kotlin and Jetpack Compose. `Local Voice` is a temporary independent name; Wispr Flow and Handy are research references, with no affiliation.

## Try it

1. Build and install the debug APK, then open Local Voice.
2. Open **Models** and explicitly download Whisper Base Q5_1 (recommended starting point for English, Hindi and mixed speech). Downloads require a connection; recognition runs on the phone afterward.
3. Tap **Try dictation**, grant microphone permission, speak, then stop. Copy, save or export the resulting text.
4. For other apps, read the accessibility disclosure in Settings, grant overlay permission and enable the accessibility service. Tap the floating microphone beside a supported focused text field. Text is inserted only after transcription and target validation; otherwise use Copy.
5. Add the **Local dictation** Quick Settings tile, use the launcher long-press shortcut or pin its shortcut. Ctrl+Shift+Space also opens dictation while the main app is focused; this is not a system-wide keyboard hook.

Recording is explicit and limited to two minutes. A visible recording activity starts microphone capture through a foreground service. The app never presses Send. Password fields and excluded apps are not eligible for insertion.

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

History supports viewing, explicit Copy, text export, optional WAV export, individual deletion and clearing all history. Exports use Android's document picker; exported files are outside the app's retention rules. Unsaved results expire from app state after five minutes. The app has no account, analytics service or cloud inference endpoint. Internet permission is used for requested model downloads.

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
