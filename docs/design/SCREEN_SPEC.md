# Android screen and interaction specification

Status: proposed design handoff, 2026-10-25. Light-first preference is confirmed; composition is a draft. [DESIGN.md](../../DESIGN.md) owns visual tokens; [Product](../../PRODUCT.md) and [engineering architecture](../planning/SYSTEM_ARCHITECTURE.md) own capability/lifecycle truth. The concept image is illustrative, not pixel-accurate evidence.

## Navigation and information architecture

Three compact-width destinations: **Home**, **Models**, **Settings**. Home owns readiness/practice/recovery, Models owns downloads/current selection, Settings owns preferences/privacy/permissions. Optional history is reached through Settings → History so an empty history tab does not dominate the initial app. History remains off until explicitly enabled.

Use a native navigation bar on compact windows, rail on medium/expanded windows, and predictable Back throughout. Model details are a nested destination, not a fourth tab. Recording/result surfaces form a separate user-triggered session flow, not a fourth permanently selected tab. They must remain discoverable to accessibility and cannot be a deceptive transparent activity.

## Screen contracts

### A. First-run setup

Sequence: local-processing introduction → language → model choice/download → in-app practice and contextual microphone request → optional cross-app permissions walkthrough → external notes-app test. Offer app-only practice if overlay/accessibility is declined. No login/paywall; no forced all-model download.

Each step has a short statement of purpose, its actual state and one specific next action. Label Android Settings launches “Open Android settings,” never imply a toggle inside our app grants OS access. Accessibility has a separate affirmative disclosure. Request notification permission contextually; denial is explained without blocking unrelated setup.

Model download page copy example: “Download Whisper Base,” “60 MB,” “English and Hindi,” “Transcription works offline after download.” Explain that device performance and Hinglish output need evaluation rather than guaranteeing accuracy. Offer metered-download confirmation and Pause/Cancel. During verification say “Checking model files,” not “Ready.” Teach that the bubble opens a small visible recording window.

### B. Home — readiness and practice

Content order:

1. Top app bar “Voice typing” (working label; public brand undecided).
2. Readiness panel: “Ready to dictate” when model and requested permissions are ready, otherwise the most useful next repair. “Model ready offline” is distinct from microphone/overlay readiness.
3. Filled “Try dictation” action for an owned practice text field. A quick practice session has a known internal insertion target and does not need overlay/accessibility grants.
4. Current model summary: friendly name, selected language mode, installed/loaded distinction available in details. “Downloaded” does not mean a permanently resident memory context.
5. Bubble preference/status: “Bubble enabled” only if both app preference and required OS/service state permit it; otherwise “Finish cross-app setup” or “Accessibility needs attention.” A preference switch cannot mask revoked OS access.
6. Small privacy/history entry if relevant, not speculative words-saved or productivity statistics.

Do not display “All set” while a selected model is missing, service disconnected or permission unavailable. Repair priorities: unavailable model/mic first for practice; overlay/accessibility for cross-app mode only. Home has no fake usage charts, testimonials or benchmark summaries.

### C. Models — mobile library

Top title “Models”; supporting copy “Download only what you need.” Language selection chip English / Hindi / English + Hindi; section ordering **Current model**, **Downloaded**, **Available for this language**. One current model only; current may also be downloaded but do not duplicate identical full rows in the same viewport.

Row content: friendly model name, one-line role, relevant language(s), exact rounded decimal size and install state, one action. Consumer descriptions are qualitative choices, not unmeasured rankings. Whisper Base “Balanced multilingual”; Tiny “Smaller download”; Small “Higher capacity, larger download”; Moonshine Tiny Streaming “Streaming English.” The Small claim does not guarantee better accuracy on the user's speech.

Use 16 sp model names, 14 sp supporting text, 12 sp noncritical metadata and native 48 dp target actions. Active selection gets a checked tinted panel; ordinary available entries use list spacing/dividers or quiet containers. Long names wrap; action moves below content on narrow/font-scaled layouts.

Show at most one context-specific recommendation, e.g. “Recommended starting model for English + Hindi,” with its basis in details. Do not place Recommended on every row. No star ratings, speed/accuracy meters, unverifiable “best on any hardware” badges or fabricated comparison charts.

Language mismatch handling: Moonshine remains English-only. Normal available-for-language results omit incompatible models. If an installed incompatible model or an expanded “Other languages” group is visible, show the reason and a deliberate “Change language” action rather than a usable selection button. The concept's visible incompatible Moonshine row illustrates this explanation, not a broken filter or supported Hindi engine.

#### Install/activation state actions

| State | Display/action |
|---|---|
| Not installed, compatible | Size + Download |
| Queued/network constrained | “Waiting for Wi-Fi” or equivalent actual constraint; Cancel |
| Downloading | Determinate progress when total known; bytes/total; Pause, Cancel |
| Paused | “Download paused”; Resume, Remove download |
| Verifying | “Checking model files”; no Use action |
| Installed, inactive | Downloaded; Use; overflow Details/Delete |
| Loading for activation | “Loading model”; prevent duplicate selection; Cancel where safe |
| Active | Check + Active; Details; deleting active model requires an explained replacement/disabled dictation outcome |
| Incompatible | Specific language/runtime/device constraint; no Download/Use pretending success |
| Failed | Concrete short reason, Retry where safe; older active model remains intact |

Changing model during recording shows “Finish or cancel this dictation first”; do not silently drop a session. A failed load offers a compatible installed alternative explicitly. No model silently downloads on fallback. Pause retains partial bytes, Cancel removes the partial; labels explain the distinction. All intended shelf models and experimental candidates are listed in [Model selection](../planning/MODEL_SELECTION.md).

### D. Model details

Show friendly purpose and languages first; Download/Use state next; device requirement as measured or proposed; storage; output behavior (batch final versus streaming preview); limitations; code and weight license links; provider/source. Put exact artifact file names, quantization, engine/runtime revision, integrity hash and benchmark methodology behind an “Advanced details” expansion.

Absent measurements display “Phone benchmarks not yet available,” never a dash filled with a fake bar. A measured chart must name device, model revision/quantization, dataset, language, sample size and metric. Separate compute time from stop-to-insert latency. Library rows remain readable without this expansion.

### E. Recording activity

Entry is an explicit action; the main app need not remain foreground. Show the small **actual visible recording activity** before starting its microphone FGS; it is not a hidden launcher/trampoline. Compact width uses native owned surface layout; expanded width centers a bounded surface. If the launch is blocked, do not start capture: offer notification/in-app recovery.

States: Preparing → Loading if needed → Listening → Processing → Delivering. “Listening” appears only after real input samples. Loading/processing never shows a fake live meter. Whisper's initial path produces a final transcript after Stop, so no promised live transcript while it records. Moonshine partial previews can be shown later in our owned surface only; external insertion remains final-only.

Listening layout: app/session title, short “Audio stays on this phone” statement, elapsed/120 s timer with amber word/icon cue, level feedback, model/language summary, a prominent filled Stop button and separate text Cancel with full touch target. Show a cap warning near the end. Never use a tiny unlabeled X as the only way to stop/cancel. Returning Back during capture prompts to cancel/discard or keep recording; an explicit Cancel aborts without another confirmation. Lock/screen-off cancels by product policy.

Stop releases AudioRecord/FGS immediately then changes title to “Transcribing on your phone.” Processing has Cancel and honest indeterminate progress unless actual progress is available. No fabricated percentage or ETA. For more than a short wait, show elapsed wait and model context; the architecture's watchdog defines timeout. If the activity is backgrounded during processing, cancel according to the current engineering baseline rather than promise durable background processing.

### F. Delivery and fallback

Close/return the owned recording activity and reacquire the original target using the engineering guard. A check/snackbar “Text inserted” is allowed only after verified success. No automatic Send or form submission.

For an unsupported/changed target, open a visible result surface with final text, “Copy text,” “Discard,” and reason. Example: “The original field changed. Copy your text to paste it where you want.” If action acknowledgement left insertion uncertain, say “Check the field before pasting; the text may already be there.” Do not encourage immediate duplicate insertion. Copy is explicit, clipboard marked sensitive, no silent overwrite/restore. If copying fails retain the result and offer Retry.

The result lives in RAM for at most five minutes; warn before expiry. Process death loses an unsaved result; no hidden persistence or automatic history enabling. A no-speech result says “No speech detected” and offers Record again, not an empty inserted string. On error preserve final text if one exists; distinguish transcription failure from insertion failure.

### G. Settings and optional history

Groups: Dictation (language/model shortcut), Appearance (Light/Dark/System; light-first), Cross-app controls (bubble on/snooze/position reset/excluded apps), Permissions and service status, Privacy and history, Help/About/Licenses. Use native grouped rows/switches, no custom dashboard tiles for every item.

History section says “History is off” initially. Enable text-only history with separate explanation and seven-day default retention; show Delete all and per-entry Delete. No waveform/audio playback because raw audio retention is not a feature. Do not add account/profile/subscription UI to a product that has none.

## Bubble behavior specification

56 dp visual control with minimum 48 dp hit area; actual icon 24 dp. Edge placement remembers normalized position. Drag uses touch slop; pointer cancel never begins recording. Avoid keyboard/cursor/gesture navigation areas; re-clamp on insets/rotation. Native foreground permission dialogs and lockscreen never receive a floating decoration from the app.

Ready: mic + “Start dictation” semantics. Recording: explicit recording status in owned activity/notification; any accompanying bubble is optional, nonessential and must not obscure Stop. Processing: neutral progress indication, not audio-reactive bars. Error: direct repair from owned UI. Hide when service is disconnected, app excluded or node eligibility uncertain. Sensitive custom-screen detection is limited; don't sell the hide rules as comprehensive security classification.

Tap opens recording. Long-press opens bubble controls only if this doesn't conflict with accessibility; otherwise provide a labeled expansion action. Hold-to-talk exists first inside the visible recording activity. No direct-overlay press-and-hold promise until pointer lifecycle and mic eligibility are proven.

## Adaptation, text and motion

- Test compact widths around 320–430 dp with no horizontal scrolling in core flows. At 200% font, model actions move below descriptions, app bars expand as native components allow, and recording buttons remain reachable.
- On medium/expanded windows use navigation rail and model list/details panes, not stretched mobile cards. Respect folding hinges and safe bounds; tablet support must have actual layout/device evidence before release claims.
- English and Devanagari are LTR, but preserve full Unicode/Bidi correctness for pasted transcripts; use locale formatting and start/end padding. Don't split grapheme clusters to truncate Hindi/emoji.
- Light/dark schemes are semantic, not image inversion. Native dialogs/system bars and permission walkthrough artwork need readable variants.
- Native motion is brief and causal. Remove animations yields a static meaningful state, not a permanently frozen “recording” illusion. TalkBack announces state changes once and can reach Stop/Cancel without traversing decorative level bars.

## Design acceptance checklist

- [ ] All screens use the light-first roles, companion dark roles and native Material components; no competitor assets shipped.
- [ ] Model/runtime distinctions and English-only incompatibility are clear; exact sizes come from registry; unavailable benchmarks aren't meters.
- [ ] Readiness/permission/install states reflect actual system values, not static concept text.
- [ ] Loading/processing are visibly different from live capture, with immediate accessible Stop/Cancel behavior.
- [ ] Target changes and uncertain delivery lead to a safe explicit-copy flow, never a wrong-field edit or silent clipboard write.
- [ ] English/Hindi text, long model names, large fonts, TalkBack, contrast, insets, Back and reduced motion pass native testing.
- [ ] Device screenshots replace concept evidence during implementation QA; native lifecycle/performance are not validated through website screenshots.
