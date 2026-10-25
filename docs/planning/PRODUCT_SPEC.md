# Offline-first Android voice typing — product specification

Status: proposed engineering baseline, pending the approval items in [Architecture decisions](ARCHITECTURE_DECISIONS.md). Research accessed 2026-10-25. No application, models, or benchmarks have been built or run.

## Product and audience

An Android-native, account-free dictation utility for people who write messages, notes, and email with their existing keyboard. English, Hindi, Indian accents, and English/Hindi code-switching are first-class evaluation categories. Core recognition is free and local after a user-selected model download. Network use is limited to explicitly requested downloads and optional future features with separate consent.

The project root is `C:\Lakshya\wispr`. The directory name is a working identifier, not a proposed public brand. Select a distinctive name and assets before release; do not use Wispr Flow or Handy branding or imply affiliation.

The interaction reference is [Wispr Flow Android](https://wisprflow.ai/android) and its [setup walkthrough](https://docs.wisprflow.ai/articles/8858845757). These establish observable onboarding, bubble, hold-to-talk, and clipboard behavior; they do not establish its internal implementation or prove our Android microphone design.

## Confirmed constraints

| Decision | Baseline |
|---|---|
| Distribution | Google Play plus signed APK; same privacy guarantees; Play acceptance is a release gate |
| Platform | Android first; proposed minimum Android 8.0/API 26; ARM64 production ABI |
| Hardware | Proposed 4 GB total RAM baseline; test a 3 GB stress tier and 6–8 GB mainstream tier |
| Languages | English, Hindi, and mixed-script Hinglish; Hindi normally Devanagari, English normally Latin |
| Session duration | Up to 120 seconds; stop with a warning near the cap, retain captured audio for processing |
| Initial engines | whisper.cpp multilingual Base Q5_1; distinct Moonshine Voice Tiny Streaming English adapter in Phase 3 |
| Privacy | History off; no raw recording retention; no mandatory analytics or account |
| Connectivity | Initial model download required; all transcription/insertion usable in airplane mode afterward |

Language support is not a quality guarantee. Neither Whisper nor a separate language detector guarantees the preferred script on every code-switch. Do not silently translate Hindi into English, romanize Hindi, or promise Hinglish accuracy before the held-out evaluation passes. English-only Moonshine cannot be selected for Hindi/Hinglish mode.

## Journeys and requirements

### Setup

1. Explain local processing and initial download size before asking for access.
2. Select English, Hindi, or mixed English/Hindi. Recommend Base Q5_1 (~59.7 decimal MB) for all three; offer Tiny Q5_1 (~32.2 MB) as a lower-resource option with a quality caveat. Display exact artifact sizes from the registry.
3. Download the chosen model with metered-network confirmation, progress, pause/resume, cancel, integrity verification, and insufficient-space handling.
4. Run an in-app practice session. Request microphone permission immediately before that action; request notification permission contextually on Android 13+.
5. Offer cross-app mode. Show a separate, affirmative accessibility disclosure, then open the relevant system setting. Explain display-over-apps separately and request special access through Settings. The app remains usable for in-app transcription if either grant is refused.
6. Test a normal field in a notes app. Explain the recording activity, final insertion, and manual-copy fallback. Do not require broad battery exemptions during setup; offer targeted troubleshooting after an observed restriction.

### Cross-app dictation

1. A connected accessibility service identifies the focused, enabled, visible, non-password editable field. A native overlay appears beside a safe screen edge.
2. A bubble tap creates a session and captures a short-lived target descriptor. It opens a compact, clearly visible recording activity. That activity starts the microphone foreground service after permission validation. This is a deliberate MVP interaction compromise: the main app is not reopened, but a small owned activity is visible during recording.
3. Recording starts only after the system grants access and capture actually begins. Show a timer, level meter, Stop and Cancel; play an optional cue after first samples. While loading a model, show Loading, never Recording.
4. Stop closes microphone capture immediately, then runs local ASR. The activity remains visible for bounded processing, with progress and Cancel. Stop at 120 seconds; never discard the whole recording because the cap was reached.
5. Before returning, the recording activity finishes. Reacquire the target in the external app. Insert only if the original target and contents/selection are still safe. Never send the message or submit the form.
6. On unknown focus, incompatible editor, changed content, timeout, or uncertain insertion, show a result surface with Copy and Discard. Copy occurs only on an explicit tap; there is no silent clipboard overwrite. The result remains in memory until copied/dismissed or for five minutes, with a visible timeout warning.

### Model management and recovery

- Settings shows installed, available, incompatible, and failed models, with language coverage, provider, format, download size, device recommendation, and license links.
- A switch is applied when idle. Unload the previous engine before loading the new one. Explain failures and offer a language-compatible installed alternative; never silently change language or download a model.
- Permission and service status cards lead to the appropriate Settings page. Do not repeatedly request denied permissions or switch them on through automation.
- Deleting the only installed ASR model disables recording and offers model setup. Offline deletion/settings must still work.

## Floating UI specification

Use a light-first interface, as confirmed by the user on 2026-10-25: near-white surfaces, dark slate text and deep-teal action color, with a companion dark theme. The original deep-teal circular microphone has an amber recording cue and icon-plus-text feedback. Shape and text distinguish states without relying on color. Provide a 48 dp touch target and TalkBack labels; font scaling applies to expanded controls. Motion obeys reduced-animation preferences. [DESIGN.md](../../DESIGN.md) and the [screen specification](../design/SCREEN_SPEC.md) define the proposed visual handoff and attributed references.

| State | Visible behavior and available actions |
|---|---|
| Idle | Mic icon; tap to open recording; expand for Settings, Snooze, Disable |
| Loading | Static progress indicator and model name; Cancel; mic remains closed |
| Recording | Amber ring, timer, live audio level; Stop and Cancel; persistent recording notification |
| Processing | Spinner, elapsed wait, Cancel; no live mic indicator |
| Success | Brief check and accessible announcement; no transcript exposed in notification |
| Error/fallback | Short reason, Retry where safe, Copy if text exists, Settings for permission issues |

Persist normalized edge/vertical position and re-clamp after rotation, split-screen, fold, keyboard, cutout, and navigation-inset changes. Avoid the text cursor, keyboard controls, gesture zones, and permission dialogs. Use drag slop so dragging does not record. Snooze hides the bubble until manually resumed; show status in the main app.

Press-and-hold is implemented first inside the recording activity. Holding on an overlay cannot be assumed to survive the activity/focus transition. Bubble press-and-hold is experimental until DOWN/UP/CANCEL, drag, permission, and OEM tests establish no accidental recording or lost stop event. It is not required to pass the first MVP gate.

Hide on device lock, screen off, password/PIN fields, permission UI, package installer, and known sensitive apps/windows. An app exclusion list lets users suppress financial, health, and work apps. Accessibility cannot reliably identify every sensitive custom field or every `FLAG_SECURE` window; fail closed when eligibility is uncertain. Never claim comprehensive sensitive-screen detection.

## MVP boundaries and acceptance

The early end-to-end MVP uses one engine and returns final text. The multi-engine release candidate adds Moonshine; multiple Whisper variants alone do not fulfill the second-engine requirement. Include onboarding, downloads, overlay, recording activity, microphone service, guarded text insertion, explicit Copy fallback, settings, and cancellation. Add opt-in text history during polish.

Exclude cloud cleanup, autonomous actions, message sending, always-on listening, wake words, a replacement keyboard, audio-file imports, arbitrary model imports, ten-minute recording, iOS, and Windows from the MVP. Future iOS is a separate keyboard/app-extension investigation; an Android overlay is not portable.

Release requires the physical-device, quality, privacy, and insertion criteria in [Roadmap](IMPLEMENTATION_ROADMAP.md). Target apps are WhatsApp, Telegram, Chrome, Gmail, and at least one notes app; compatibility is published by app version/editor, not promised universally. Users retain their current keyboard.

## Approval items

Approve the Kotlin/Compose direction, the compact recording activity compromise, proposed API 26/ARM64/4 GB baseline, the two-engine sequence, and opt-in history policy. Confirm a physical-device budget before Phase 1. These are concrete design choices; see [ADRs](ARCHITECTURE_DECISIONS.md) for alternatives and [Risks](RISKS_AND_OPEN_QUESTIONS.md) for unverified claims.
