# 0.1.6 — natural feature lists and listening dots

26 October 2026. Install as an upgrade; local models/history remain in the existing app data.

## Natural lists

Smart formatting now recognizes a list introduction followed by short comma/semicolon-delimited items, without requiring “first/second” or “bullet point.” It supports feature/requirement/task/item/option and related list cues, strong requests without a colon, supported Hindi introductions, final conjunctions, and a following prose sentence outside the list.

The user's example:

```text
I want these features: UI, front end, back end, database.
```

becomes:

```text
I want these features:
• UI
• Front end
• Back end
• Database.
```

Protected quoted material and nested parentheses keep their internal commas. Numeric thousands separators, technical names and common compound items are retained. Ordinary comma-separated prose without a recognized list introduction is left as prose. Existing numbered/bulleted output is preserved. Original wording still bypasses formatting. This extends local rules; it does not add a generative interpretation model or guarantee recognition of every implicit list.

## Floating recording controls

The visible “Recording” label is replaced by five dots that grow with the actual microphone level, with smoothed attack/release. Silence settles to still dots. The elapsed/maximum time remains below, with Stop and Cancel beside it. The drag area includes both the dots and timer. The overlay remains non-focusable and does not launch the app.

Animation runs only while recording, visible and receiving a nonzero level or settling. Hidden/detached and non-recording states stop it. Android's Remove animations setting uses stationary dots with sound-level opacity feedback. TalkBack still gets a recording/timer description; the decorative dots do not produce repeated announcements. Existing light/dark teal colors are retained.

## Verification

The combined core/app tests, debug assembly and lint pass in 1 minute 24 seconds: 60 tests, no failures/skips, zero lint errors and 16 warnings. Eight new tests cover the exact request, optional colon, final conjunctions, following prose, ordinary prose preservation, nested commas, quotes, compound items, thousands separators, technical names, Hindi/Roman Hindi, idempotence and Original wording. Captured-audio regression tests from 0.1.5 remain included. The APK supports ARM64/x86_64, retains the existing debug signing certificate and passes 16 KB ZIP alignment.

No Android device/emulator is connected. Microphone-level animation, actual rendering, system Remove animations and TalkBack/drag interaction still need phone verification. This patch changes formatting and overlay feedback; it does not claim improved acoustic recognition accuracy.
