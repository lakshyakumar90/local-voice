# Android permissions, lifecycle, privacy and release policy

Accessed 2026-10-25. This is an engineering/policy checklist based on published primary sources, not Google Play approval. Requirements can change; recheck immediately before implementation and each submission. Related: [Architecture](SYSTEM_ARCHITECTURE.md), [Roadmap](IMPLEMENTATION_ROADMAP.md).

## Platform baseline

Propose Android 8.0/API 26 minimum, ARM64 production, 4 GB total RAM baseline. Android 8 is a compatibility target, not a promise that a particular model meets speed/quality requirements on every old phone. Test current Android 16/API 36 and Android 17/API 37, including all-app behavior changes. Android 14/15 microphone restrictions are especially important.

As accessed, [Google Play's target policy](https://support.google.com/googleplay/android-developer/answer/11926878) requires new phone apps and updates to target Android 16/API 36 from August 31, 2026. Plan target SDK 36 and compile SDK 37, revalidated against available stable build tools at implementation. Do not lower target SDK to evade restrictions. [16 KB native page support](https://developer.android.com/guide/practices/page-sizes) must cover all inference libraries, AAR dependencies and STL builds; validate both ELF and APK zip alignment and run a 16 KB device/emulator.

## Permissions and declarations

| Permission / component | Purpose and request timing | Refusal/revocation behavior |
|---|---|---|
| `RECORD_AUDIO` runtime | Ask on first explicit practice/record action; explain local capture | Never record; offer settings after permanent denial; support in-app typed settings/downloads |
| `FOREGROUND_SERVICE` | Declare for microphone capture beyond the visible activity lifecycle | Required architecture declaration, not user-granted microphone access |
| `FOREGROUND_SERVICE_MICROPHONE` and service type `microphone` | Declare for current target; create service only through eligible path after runtime grant | Catch start/security failure; do not retry invisibly |
| `POST_NOTIFICATIONS` runtime on API 33+ | Explain Stop/recording visibility and recovery; request contextually | FGS still needs a notification even if permission denied; Android may show it in Task Manager instead of drawer; use in-app recording status; do not claim drawer visibility |
| `SYSTEM_ALERT_WINDOW` special app access | Request for cross-app bubble; `Settings.canDrawOverlays` check, system Settings flow | Remove overlay immediately when lost; in-app transcription remains available |
| Accessibility service with `BIND_ACCESSIBILITY_SERVICE` | Service declares system binding permission and XML metadata; user enables in Settings after separate consent | Disconnect removes overlay/insertion ability, cancels active recording by product policy; no automatic re-enable |
| `INTERNET`, `ACCESS_NETWORK_STATE` normal | Requested model downloads, network constraints | Recognition stays local; INTERNET does not justify audio upload |
| `WAKE_LOCK` if needed | Session-scoped CPU capture reliability; use only with measured need | Release on Stop/Cancel/error; never idle keepalive |
| Bluetooth permissions, if needed later | Route-specific permission/SDK matrix must precede advertised Bluetooth input | Built-in microphone default; do not collect nearby-device access unnecessarily |

No contacts, SMS, call logs, location, broad storage, all-files access, device admin, VPN, full-screen intent, exact alarm, usage-access, or boot microphone permission is justified. Avoid `QUERY_ALL_PACKAGES`: the app exclusion UI can use observed target package labels locally, without broad installed-app discovery. Do not request direct battery-optimization exemption by default; see troubleshooting below.

Configure accessibility only for focused-field tracking and user-triggered insertion, with window-content retrieval capability where required. Subscribe to narrow view/window/focus events; event filtering does not eliminate the broad sensitivity of the permission, which must be disclosed. Do not enable key filtering, touch exploration, gesture dispatch, screenshot capture, or action automation. Use `isAccessibilityTool=false` for this general productivity product; motor-accessibility benefits do not alone establish eligibility to claim the special category.

Sources: [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types), [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission), [overlay constants](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY), [accessibility service guide](https://developer.android.com/guide/topics/ui/accessibility/service).

## Microphone start: two independent checks

The [FGS background-start rules](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) and while-in-use microphone rules must both pass. On target Android 15+, the `SYSTEM_ALERT_WINDOW` FGS-launch exemption requires an actually visible overlay. This does **not** by itself prove permission to create a microphone FGS from the background. `checkSelfPermission(RECORD_AUDIO)` reporting granted is insufficient to establish current while-in-use eligibility. Android 14+ can throw SecurityException at service creation.

Baseline sequence: explicit bubble action → actual visible RecordingActivity → permission/model checks while resumed → start microphone FGS and notification → open AudioRecord → show active recording only after capture starts. Keep the activity visibly recording, not transparent or a one-frame trampoline. The [background activity launch rules](https://developer.android.com/guide/components/activities/background-starts) include overlay special-access cases and changing PendingIntent requirements; test the chosen direct activity intent across the supported OS/OEM matrix. If blocked, do not record: offer a user-tapped notification or in-app action to open the recording activity.

Stop immediately closes microphone and ends the microphone FGS. The visible activity can finish bounded local inference. On activity backgrounding during processing, cancel processing rather than retaining an unjustified microphone FGS. A future asynchronous processing service needs its own justified design/type; don't label inference as microphone capture or specialUse merely to keep a process alive. WorkManager never schedules immediate capture.

A direct bubble-to-record path can be explored only after a documented, applicable while-in-use exemption and physical-device validation. Do not infer it from Wispr's observed behavior, accessibility being enabled, the overlay being visible, or a desktop implementation. No boot receiver or silent restart starts recording.

## Lifecycle and recovery matrix

| Event | Feasible state / product behavior | Recovery and required user action |
|---|---|---|
| Main app backgrounded | Enabled system-bound accessibility service can track eligible focus and host overlay; no idle mic | User taps bubble; real recording activity handles start; no uptime guarantee |
| Main task removed from Recents | Task removal is not force-stop; service may remain, but OEMs may kill it; recording activity task behavior must be tested | Continue idle bubble if service remains; otherwise reopen app/status page if reconnect does not occur; never advertise immortal service |
| Recording task removed | Cancel capture, close mic/notification, clear pending session when callbacks run; if process is killed OS releases resources | New explicit session required; no replay/restart of recording |
| Low-memory/process kill | System releases mic; ephemeral audio/result lost; installed models/settings remain; service may later rebind | Rebinding can restore idle bubble if service still enabled; never auto-resume recording or insert a stale result |
| FGS Task Manager Stop | Android stops app; callback delivery is not guaranteed; queued jobs/alarms differ from force-stop | Do not depend on onDestroy for durable cleanup; next manual launch reconciles state; no automatic microphone restart |
| Settings Force stop | App enters stopped state; app code cannot reliably restart itself, receive ordinary recovery work, or show a new recovery notification | User explicitly launches/interacts with app to leave stopped state; guide them then; no alarm/WorkManager bypass |
| Microphone/overlay/accessibility revoked | May cause process termination or callback changes; detect at next use and continuously during active session where possible | Stop/cancel safely; explain exact missing access; user regrants through system UI |
| Notification permission revoked | Drawer notification may disappear; capture rules still apply | Keep visible activity status; show optional recovery explanation, not repeated nagging |
| Device reboot / app update | Installed files persist; no in-flight session persists; accessibility may reconnect after unlock if enabled | Reconcile status on connection/launch; notification only if permitted and justified; user launches app if unavailable; never boot-start microphone |
| Device locked / screen off | Hide overlay; cancel capture/delivery by product choice; raw buffer discarded | Unlock + explicit new session; no insertion into lockscreen or behind authentication |
| Battery/OEM background restriction | Service may be delayed/disconnected even when Android API permits behavior | Offer vendor-specific instructions with caveats after observed failure; user may reopen app; no guaranteed recovery |

Sources: [FGS user Stop](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping), [process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle), [Android 15 stopped-state changes](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state), [persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent). Test callback delivery and reconciliation, not only happy-path onDestroy.

## Focus eligibility, insertion and clipboard

Do not infer field focus solely from keyboard visibility. Track the active editable node, its visibility/enabled/password status, active window/package and generation. Custom canvas editors, WebViews, rich text, protected fields and incomplete accessibility trees may be unsupported. Fail closed if eligibility is unknown. Hide over permission/system installation/authentication UI; maintain user-managed package exclusions. `FLAG_SECURE` detection is not universally available to a third-party accessibility service, so a complete sensitive-screen guarantee would be false.

`ACTION_SET_TEXT` replaces a value, rather than providing universal cursor insertion. Revalidate the original target, obtain complete safe plain text and selection transiently, construct the replacement, perform once and verify where supported. Unknown selection/composition/rich content or changed target produces manual-copy fallback. Never use arbitrary coordinate taps, global gestures, or Send/Submit actions. [Action reference](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction#ACTION_SET_TEXT).

Copy is an explicit action from a visible owned result surface. Mark copied transcript sensitive with `ClipDescription.EXTRA_IS_SENSITIVE` where supported to suppress previews. Android 10+ restricts background clipboard reads; Android 13 adds clipboard UI and automatic clearing behavior. Do not read/save the user's previous clipboard for restoration, assume universal paste availability, or silently overwrite clipboard after insertion failure. Offer a five-minute result window and warn before expiry. If implementing a clear action, clear only when it still owns the matching clip, never newer user content. [Official clipboard guidance](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste).

## Setup disclosure drafts

Present these as separate readable screens, with affirmative controls, before opening system Settings. Localize English and Hindi; do not bundle accessibility consent into microphone consent.

**Accessibility:** “To show the microphone near supported text fields and insert text you dictate, this app uses Android Accessibility. It can access information about the active screen, including editable fields. We use this only to find a supported field and, when you start dictation and when we insert its result, briefly read that field's text and selection to check that it has not changed and preserve existing text. We do not save screen contents, monitor your messages, or send this information to a server. You can turn this access off in Android Settings. Some apps and protected fields are unsupported.” Controls: Agree and continue / Not now. Record consent version/date locally, without screen contents. The memory-only start snapshot retains a salted digest and selection, not the source text.

**Overlay:** “Allow a small microphone control to appear over other apps. It appears only when we can identify an eligible text field, and you can snooze or disable it.” Explain that system Settings is the authority and sensitive custom screens may be unidentifiable.

**Microphone:** “Recording begins only when you start dictation. Audio is processed on this phone using your installed model and is discarded after processing or cancellation. A visible recording surface shows Stop and Cancel.” Request runtime permission only after Continue/record action.

**History, later opt-in:** “Save dictated text on this device for seven days. History is off unless you enable it. It never includes raw audio. You can delete entries or all history at any time.” Retention selector and delete-all available before enabling.

These are drafts for implementation review, not a claim that a particular wording guarantees approval. Also provide a public privacy policy linked in-app and in Play; clearly disclose model download hosts/CDNs and their network metadata. No account-deletion flow is needed while there are no accounts, but all local data must be erasable.

## Security and retention

- Default history off. PCM in bounded RAM only, discarded after final processing, cancellation or error. Retain an unsaved transcript in memory for at most five minutes; no notification content, logs or crash report includes it. No secret crash persistence.
- History opt-in encrypts transcript payloads using Android Keystore-backed AES-GCM with unique nonces. Exclude history/keys/models from backup and device transfer; handle key loss by explaining and deleting unreadable entries. Do not claim secure flash erasure.
- No transcript/audio analytics. Redact paths/URLs where user-supplied, all node text, clipboard and PCM. Diagnostic export is explicit, previewed and bounded. Default app needs no third-party analytics/crash SDK.
- Model bytes are data: download over HTTPS, validate exact sizes/SHA-256, constrain bundle paths and decompression, reject unsupported formats, install atomically. Disable cleartext traffic. Model deletion works offline and unloads handles first.
- Export only necessary Android components, protect accessibility binding, use immutable scoped PendingIntents and avoid untrusted Intent extras/path traversal. No remote executable modules or arbitrary code downloads.
- Optional battery guidance must describe likely effects, not instruct users to disable all device power management. Reference [Doze/app standby](https://developer.android.com/training/monitoring-device-state/doze-standby); do not treat exemption as a microphone permission.

## Google Play and APK release gates

1. Recheck target API, behavior changes, 16 KB native support, current sensitive-permission rules and developer verification/distribution requirements for launch countries.
2. Complete Accessibility API declaration; list the narrow user-directed insertion use and data access accurately. `isAccessibilityTool=false`; no autonomous planning/actions. Submit a video showing launch, full disclosure, affirmative grant, refusal path, and actual dictation/insertion.
3. Review the [Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491), [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311), [FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821), and [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/9888379). Declare microphone FGS in Console with visible user benefit, triggers, stops and demonstration.
4. Complete Data safety from actual flows/SDKs, distinguishing on-device processing from transmission and accounting for download-server metadata. Do not copy another app's disclosure or state “no network traffic” when model downloads exist.
5. Audit SDK/native/weight licenses, attribution, model provenance, redistribution terms, package alignment and known dependency vulnerabilities. Hash and archive the reviewed registry for the release.
6. Validate overlays do not obscure security dialogs, coerce grants, impersonate other apps or capture unrelated taps. Verify accessible controls and no accidental Send action.
7. Publish signed AAB and reproducible signed APK builds with documented update/signing strategy. Preserve application ID/signing compatibility deliberately; Play App Signing vs direct-APK update compatibility must be resolved before first public release.
8. Test sideloaded access restrictions: some Android versions block enabling sensitive settings for sideloaded apps until the user explicitly allows restricted settings. Explain the OS flow without bypassing it. Policy rejection is not permission to hide the feature or ship a deceptive alternate build.
9. Publish a tested app/editor compatibility list, limitations and recovery instructions. No “works everywhere,” “always running,” or guaranteed Hindi/Hinglish accuracy claim.
10. APK testing can proceed before Play review; public Play release cannot. If review rejects accessibility use, document the feedback and evaluate a user-selected IME or app-only product separately; do not silently substitute a new permission model.
