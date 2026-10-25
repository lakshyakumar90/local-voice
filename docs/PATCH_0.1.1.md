# 0.1.1: system bars, restricted settings and Roman Hindi

User device reported: Motorola Edge 60 Pro, Android 17. No device was attached to ADB during this patch; these changes still need confirmation on that phone.

## Changes

- System status/navigation icon appearance now follows the selected app theme, including light mode while the phone itself uses dark mode. Previously `enableEdgeToEdge()` followed the system theme independently of Compose's app theme, which could put white icons over the app's light background.
- Settings includes an explicit restricted-permission troubleshooting dialog and App info link. Overlay navigation falls back to the general Android overlay list if a package-specific screen cannot open. The existing accessibility disclosure and manual grants remain required.
- Mixed mode is now labelled **English + Roman Hindi**. Final recognized Devanagari text is romanized locally before showing, saving, exporting or inserting it. Existing English/Latin text is kept exactly as recognized. **Hindi script** mode preserves Devanagari. English mode remains English-only.
- Romanization uses deterministic, offline character/word rules, including vowel marks, nukta, conjuncts, common Hindi spellings and Devanagari digits. Spellings and schwa deletion are approximate for unfamiliar words and names; this is not a learned Roman Hindi model. No translator or network service is involved.
- The Whisper adapter already used `translate=false`; that remains unchanged. A recognition model can still misrecognize or normalize code-switched speech. Romanization cannot recover Hindi words that the model has already output as English. Use a multilingual Whisper model and mixed mode; Whisper Small is worth trying when Base misses Hindi. Moonshine remains English-only. No phone ASR-quality improvement is claimed without audio testing.
- Quick Settings dictation waits for unlock when the device is locked. Ctrl+Shift+Space is handled directly and key repeats are consumed to avoid opening duplicate recording windows. Static and pinned shortcuts still route through MainActivity's dictation action; hardware interception remains app-focused only.

## Android restrictions

The app cannot grant itself accessibility/overlay access, remove Android's warning or override a device administrator/security policy. If the user trusts the installed build, Android's documented path is **Settings → Apps → Local Voice → ⋮ → Allow restricted settings**, followed by enabling the service in Accessibility. Display over other apps is a separate grant. If the menu is unavailable or access stays disabled, investigate device/work-profile/security restrictions rather than trying to bypass them. In-app recording and explicit Copy/export work without accessibility or overlay access.

Source: [Google's restricted settings guidance](https://support.google.com/android/answer/12623953?hl=en), checked 2026-10-25 using Agent Reach's Jina reader. This is general Android guidance, not a verified Motorola Android 17 walkthrough.

## Verification

The patch adds three JVM regression tests: mixed Hindi/English without translation, unchanged English/URLs/emoji, and Devanagari marks/nukta/conjuncts/digits. `:app:assembleDebug :core:test :app:testDebugUnitTest :app:lintDebug` passed on the final run (53 seconds): 18 tests, zero failures; lint zero errors and 14 warnings. APK signature verification and 16 KB ZIP alignment passed; the signing certificate matches 0.1.0 for an in-place update. Release 0.1.1 was not built. Theme appearance, system permission screens and shortcut behavior have not been exercised on the user's phone.
