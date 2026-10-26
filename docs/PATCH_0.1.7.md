# 0.1.7 - first dictation into an empty field

26 October 2026. Install over the existing app to retain models and history.

## Fix

Some empty editors expose null/empty accessibility text and selection indices of -1. Previously the recording gesture discarded this target, so completion showed the original-field-unavailable warning and Copy fallback. Existing text typically exposed a valid cursor and worked.

Capture and delivery now use the same normalized field snapshot. An empty editor has one insertion position, zero, even when Android reports a missing selection. A placeholder explicitly marked as hint text is treated as empty content. Real text matching the placeholder is preserved when it is not marked as a hint. A missing selection becoming zero during recording no longer invalidates the empty target.

Nonempty fields still require a valid selection. Package/window/node identity, focus generation, text digest, selection changes, password exclusions and expiry checks remain enforced. Typing into the field during transcription prevents automatic replacement. Insertion still verifies the result and never retries an uncertain write or launches the main app.

## Verification

Seven regression tests cover missing empty selections, null text, placeholder handling, selection metadata transitions, preserving real text, invalid nonempty selections, user edits during recording, and a first insertion followed by another dictation. Existing insertion and audio/formatting tests remain included.

Automated verification: 67 tests pass with no failures or skips. Debug APK assembly, signature verification and 16 KB ZIP alignment pass; lint completes with zero errors and 16 existing warnings; versionCode is 8 and the existing debug signing certificate is retained.

Phone verification: focus a completely empty message/note field, tap its mic, dictate and Stop. Text should appear directly without Copy. Repeat to append; clear all text and repeat with a visible placeholder. Also type manually during processing: the app should retain the safe Copy fallback rather than overwrite that edit.

No phone or emulator is connected; actual editor interoperability still needs device verification.
