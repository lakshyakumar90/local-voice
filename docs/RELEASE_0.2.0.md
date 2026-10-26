# Local Voice 0.2.0 — signed release and identity kit

26 October 2026. Version code **9**. This is a signed, non-debuggable release APK, not a debug build.

## Download artifact

- File: `LocalVoice-0.2.0-arm64-release.apk`
- Package: `dev.localvoice.app.release`
- Size: **22,702,318 bytes / 22.70 MB / 21.65 MiB**.
- Previous local debug artifact: 65,973,122 bytes; this release is **65.6% smaller**. R8/resource shrinking and removing emulator native libraries account for the reduction. Speech models are still separate explicit downloads.
- ABI: ARM64 only (`arm64-v8a`), suitable for the Motorola Edge 60 Pro. Android 8/API 26 minimum; target API 36.
- APK SHA-256: `2472b23efbf689277c3d94551e5af11323b2356944dff29d89d9a0ea893b2bfa`
- Release certificate SHA-256: `19831053b5bc70a9c7063076cf5ba2106670250632ff2c5a44560296bfa0cf93`
- Dedicated RSA-4096 release key, verified APK Signature Scheme v2.

## Installation transition

Install alongside the current debug app. The new teal waveform icon identifies the release. Do not uninstall the old app until you have exported anything needed and checked the release. Existing debug models/history/settings remain in the old app; the release has separate storage and requires its own model download and permissions. Disable the old accessibility/bubble service before enabling the release's field mic to avoid duplicate controls. There is no automatic encrypted-data migration.

Future updates will reuse this release package and key. Signing material is stored privately under the ignored `signing/` directory, not in the public output/asset bundle. Read `RELEASE_BUILD.md` before moving machines: the password file is Windows-user/machine bound, so a portable private recovery backup needs preparation while this machine is available. The R8 mapping is archived privately under `signing/releases/0.2.0/`.

## Included changes

The functioning 0.1.7 app behavior is retained, including first insertion into empty fields, local models, language/script controls, formatting, field-only recording controls, history and optional saved audio. New original waveform branding replaces the generic launcher mic: adaptive, round, Android themed monochrome and density-specific fallback icons. Functional microphone controls keep their familiar symbols.

The separate identity kit includes vector/PNG logos, a generated hero illustration, original SVG explanatory graphics, social images, an illustrative motion loop, self-hostable Manrope fonts/license, palette/type/motion tokens, review boards and credited visual research. Marketing imagery/fonts are not bundled in the APK. `WEBSITE_DESIGN.md` is a design/content handoff for later; no Next.js website or deployment was created.

## Verification

- Final signed release assembly succeeds; 67 JVM tests pass, zero failures/errors/skips. App tests exercise shared source through the debug unit-test variant available in this AGP setup, not an instrumented release runtime.
- Release lint: 0 errors, 17 warnings; **no launcher-icon warnings**. Remaining warnings cover dependencies, API/style/accessibility guidance; build success is not a complete accessibility audit.
- Actual APK identity/version, ARM64-only contents and non-debuggable application verified.
- Signature and 16 KB ZIP alignment pass. All 6 packaged native libraries have 16 KB-compatible ELF LOAD segment alignment.
- Verified logo/board artwork visually, parsed SVGs, decoded raster assets, checked representative text color contrast, and recorded asset checksums/dimensions/frame counts.
- No phone/emulator is connected. Minified-release cold start, model load, actual dictation/insertion, audio playback and system shortcut interaction remain phone checks; they are not claimed as tested.

On the phone, check a short recording, first insertion into an empty field, another append, cancellation, saved recording playback and the launcher shortcut before removing your debug installation. Release signing does not bypass Android's sideloaded-app permission restrictions.
