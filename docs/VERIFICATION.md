# Build verification

Workstation verification on 2026-10-25. These checks validate source/build behavior; they are not phone acceptance results.

## Toolchain

- JDK: Temurin 17.0.18
- Gradle wrapper: 9.4.1, distribution SHA-256 pinned
- Android Gradle Plugin: 9.2.1
- Kotlin and Compose compiler: 2.2.10 (aligned with AGP's Kotlin toolchain)
- Compile SDK 37; target SDK 36; minimum SDK 26
- NDK 27.1.12297006; CMake 3.22.1
- whisper.cpp: `231638a0717b7822784297e5980965ffcec05810`
- Moonshine Android artifact: `ai.moonshine:moonshine-voice:0.1.5`

The build currently opts out of AGP's built-in Kotlin/new DSL while using the external Kotlin Android plugin. AGP reports migration deprecations; migration is needed before AGP 10. Dependency versions are pinned rather than automatically upgraded during verification.

## Automated behavior coverage

- Core: 8 tests covering surrounding text/selection preservation, invalid selection fallback, UTF-16 emoji boundaries, Hindi spacing, English-only model gating, valid resume ranges, audio duration/conversion and high-frequency alias rejection.
- Model downloader: 7 tests covering full verified transfer, correct range resumption, ignored-range restart, invalid range rejection, invalid checksum rejection, complete-partial verification without network and cancellation of a blocked network call.

Tests use synthetic audio and a local mock HTTP server. They do not invoke Android Keystore, a microphone, an accessibility service or native ASR.

## Final results

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :core:test :app:lintDebug
```

**BUILD SUCCESSFUL** (4 minutes 32 seconds on the final run). Core: 8 tests, zero failures/errors. Downloader: 7 tests, zero failures/errors. Debug lint: zero errors, 13 warnings. Release vital lint also passed. Warnings cover target API 36 versus compile API 37, newer dependency versions, an API-31-only XML attribute, KTX style suggestions and the drag listener's delegation of ordinary clicks to ImageButton. No blanket lint baseline or disabled error category is used. The old tile API has a scoped suppression only in the pre-API-34 compatibility helper; API 34+ uses PendingIntent.

Both debug and unsigned release APKs pass native ELF and ZIP 16 KB alignment checks. The debug APK passes Android SDK signature verification (v2). Packaging inspection confirms only `arm64-v8a` and `x86_64`; no unsupported 32-bit ABI is included. The Whisper library's dynamic dependencies are Android system libraries, with no missing external C++ runtime dependency.

Outputs in the source tree:

- `app/build/outputs/apk/debug/app-debug.apk` — installable development build.
- `app/build/outputs/apk/release/app-release-unsigned.apk` — minified and resource-shrunk, requires release signing before installation/distribution.

Earlier verification exposed a Kotlin/lint toolchain conflict and an incorrectly propagated socket-close exception during cancellation. Those were fixed; the final results above are from the aligned toolchain and the passing cancellation test.

## Native packaging checks

`scripts/check-native-alignment.py` checks every 64-bit ELF `PT_LOAD` segment in the APK. Android SDK `zipalign -c -P 16 4` checks APK entry alignment. Android SDK `apksigner verify --verbose` checks the debug APK signature. Debug signing is for development only; the release artifact is unsigned.

## Runtime verification

`adb devices -l` returned no attached devices. No emulator/system image is installed. No real microphone recording, on-device model inference, visual screenshot review, cross-app insertion, permission lifecycle or phone benchmark has been executed. Follow the device acceptance checklist in `IMPLEMENTATION_STATUS.md` before release.
