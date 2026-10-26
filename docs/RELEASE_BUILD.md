# Signed release APK and signing continuity

The 0.2.0 release uses R8 code optimization/obfuscation, resource shrinking and ARM64-only native libraries. It is a non-debuggable APK signed with a dedicated RSA-4096 release key. Android minimum: API 26. The Motorola Edge 60 Pro uses the ARM64 build. Emulators/x86 phones need a separate build; this APK does not include x86_64 libraries. Local speech models remain explicit downloads, not bundled APK payloads.

## Install alongside the current debug app

Previous development APKs use `dev.localvoice.app` and the standard debug certificate. The proper release uses **`dev.localvoice.app.release`** and a new release certificate. It installs alongside the debug app to preserve existing models, history and settings. Its new teal waveform icon distinguishes it from the old mic icon.

The release starts with separate app data. Download its model and grant its permissions again. Existing encrypted recordings/history do not automatically migrate between packages or Keystore identities. Export anything needed from the old app before deleting it; exported text/WAV files are outside automatic retention. Keep the debug app until the release has been checked. Disable the old app's field bubble/accessibility service before enabling the release service to avoid two competing microphones.

Future releases must keep this release package ID and certificate, with increasing versionCode, so they can upgrade this installation. The package suffix is intentional; it is not a debug flag. There is no requirement to rename it later.

Release signing does not remove Android sideload/accessibility restrictions or guarantee permission approval by a device administrator. Use the existing app permission help when Android restricts an installed APK.

## Build on this machine

```powershell
cd C:\Lakshya\wispr
.\scripts\release.ps1
```

The script loads local signing material, supplies credentials through temporary environment variables, runs core/app JVM tests, assembles the minified release and runs release lint. This AGP setup exposes app unit tests for the debug variant; those test shared app source, not an instrumented/minified release runtime. Release execution on a phone is a separate check.

`-InitializeSigning` is a one-time operation and refuses to overwrite existing material. Do not run it for routine updates. The signing folder is Git-ignored and protected with a Windows ACL for the current user. Passwords are stored using Windows DPAPI through SecureString/CLIXML and are not logged or committed. Credentials are removed from the build process environment afterward. No signing material is included in the APK/asset handoff bundle.

## Preserve the update key

The private key is `signing/localvoice-release.jks`; `signing/release-password.xml` is its DPAPI-protected password. Losing the private key/password prevents future updates signed as this app. Back up signing material privately. **The XML password is bound to this Windows user/machine; copying it alone is not a portable recovery plan.** Before moving machines or reinstalling Windows, recover the password on this machine into a trusted password manager and keep a separate encrypted keystore backup. Do not paste it into chats, commit it, put it in public outputs or include it in the website repository.

To obtain the password privately on this machine, use the same `Import-Clixml` + `System.Net.NetworkCredential` pattern in `scripts/release.ps1` and transfer it directly to your trusted secret store. Public distribution needs only the certificate fingerprint and APK checksum, never the private key or password. A Play Store upload, if chosen later, needs its own distribution/signing setup; none is created by this task.

## Verification and release records

For each release, record package/version, actual size and SHA-256, signing certificate fingerprint, ABI list, non-debuggable manifest, ZIP/ELF alignment, unit test totals and lint result. Keep R8 `mapping.txt` privately with the release so future crash traces can be decoded. A smoke test on the phone should cover cold start, explicit model download/load, a short Whisper transcription, first insertion into an empty field, a second append, cancellation, history/audio playback and launcher shortcuts.

`RELEASE_0.2.0.md` contains measured artifacts/checks and remaining device limits. No phone/emulator is attached to this workstation, so a successful build/static inspection must not be described as runtime validation.
