# Wispr Flow research and Local Voice formatting

Researched 26 October 2026 using Agent Reach: Exa search, Jina page reading, and primary Wispr documentation. Product capabilities vary by platform and change over time. This document describes the sources reviewed, not an independent benchmark of Wispr.

## Why Flow feels better than a raw transcript

Flow separates hearing words from producing usable writing. Its [technical blog](https://wisprflow.ai/post/technical-challenges) describes speech recognition, context, and personalized language-model formatting as distinct problems, with cloud infrastructure and a tight latency budget. Formatting is therefore not a hidden Whisper setting. Better acoustic recognition alone will not automatically turn a long transcript into an email or resolve a correction's intended meaning.

The [features page](https://wisprflow.ai/features) emphasizes removing fillers, formatting lists, punctuation, corrections, dictionaries, snippets, and writing styles. Its [comparison with built-in dictation](https://wisprflow.ai/why-flow) emphasizes ready-to-use writing and staying in the destination app. These are product claims; they explain the intended workflow, not measured superiority on this phone.

The [Smart Formatting guide](https://docs.wisprflow.ai/articles/5373093536-how-do-i-use-smart-formatting-and-backtrack) gives concrete behaviors: sequence words can produce lists, spoken commands control punctuation and line breaks, and contextual corrections remove an abandoned value while preserving ordinary uses of words such as “actually.” It also says formatting does not repair misheard words. Android formatting is always applied, while other platforms have different controls.

The [Auto Cleanup guide](https://docs.wisprflow.ai/articles/4283510616-auto-cleanup-control-how-much-flow-edits-your-dictation-beta) distinguishes original transcription, light cleanup, and more extensive rewording on desktop. The [context-awareness guide](https://docs.wisprflow.ai/articles/4678293671-feature-context-awareness) describes surrounding-text and destination-aware behavior, including situations where context is unavailable. The [snippets guide](https://docs.wisprflow.ai/articles/5784437944-create-and-use-snippets) describes saved voice-trigger expansions. These are separate capabilities; a rule-based formatter must not be presented as a full replacement for them.

## What this app implements locally in 0.1.5

Pipeline: microphone → local ASR → script validation → local formatting → Roman Hindi conversion if selected → original-field insertion/history/export. Language mode and formatting mode are captured when recording starts. Formatting never invokes a translation model or network service.

| Behavior | Implementation |
| --- | --- |
| Numbered lists | Consecutive first/second, one/two, number one/number two, and supported Hindi equivalents; at least two items in order and a plausible list opening are required. Existing numbered lists are retained. |
| Bullets | Say “bullet point” / “next bullet” or supported Hindi equivalents. “End list” separates subsequent prose. Output uses real line breaks and plain-text bullets. |
| Paragraphs | “New paragraph” / “next paragraph” and Hindi equivalents create blank lines, including at field insertion boundaries. Long prose of at least 90 words and four sentences is grouped every three sentences. Unpunctuated text of at least 140 words is grouped in 70-word blocks. These are readability rules, not topic understanding. |
| Line breaks | “New line,” “next line,” “line break,” “skip a line,” “नई लाइन,” and “अगली लाइन.” |
| Punctuation | Spoken comma, full stop/period, question mark, exclamation point/mark, colon, semicolon, and selected Hindi names. Model-generated punctuation is retained. |
| Light cleanup | Removes isolated um/uh hesitations. Does not blindly delete “like,” “you know,” ordinary “actually,” or emphatic repeated words. |
| Clear numeric corrections | A restricted correction such as “at 2 pm, actually 3” becomes “at 3 pm.” General restarts, semantic corrections, negatives and decimals are not guessed. |
| Email shape | A simple greeting and a clearly punctuated sign-off can get their own paragraphs. No names or body content are invented. |
| Literal material | Quoted text, inline/fenced code, links and email addresses are protected from voice-command interpretation. |
| Control | Settings → Dictation → Smart formatting / Original wording. Original wording bypasses this layer; ASR's own punctuation and recognition mistakes still exist. |
| Duration | Recording, UI and native validation allow up to ten minutes. Processing timeout and field-target lifetime scale with that limit. Cancel remains available. |

The existing history player and WAV/text export continue to use the final transcript and optional retained recording. Cross-app insertion remains plain text through Android accessibility; this is not HTML/rich-text list insertion. It never submits the text or presses Send.

## Examples

Input: `First, buy milk. Second, send the report. Third, call Alex.`

Output:

```text
1. Buy milk.
2. Send the report.
3. Call Alex.
```

Input: `Shopping: bullet point milk next bullet bread end list Thank you.`

Output:

```text
Shopping:
• Milk
• Bread

Thank you.
```

Input: `कल मिलते हैं। नया पैराग्राफ मुझे जाना है।`

Hindi script output:

```text
कल मिलते हैं।

मुझे जाना है।
```

Roman Hindi output:

```text
kal milte hain.

mujhe jaana hai.
```

## Boundaries and future model work

There is no generative rewrite model in this build. It does not infer every implicit list, perform arbitrary grammar repair, learn spellings from edits, expand personal snippets, adapt tone per app, summarize, or understand arbitrary self-corrections. Recognition can miss a spoken command, and unquoted literal command phrases can be ambiguous; Original wording is the escape hatch. The formatter cannot restore Hindi words that ASR already misrecognized or translated.

Matching more of Flow's contextual writing requires a separately evaluated on-device language model: explicit download, memory and latency measurements on the target phone, English/Hindi/code-switching preservation tests, and a reversible original-text view. A general model should not silently rewrite names, numbers or intent merely to make text sound polished. This patch does not add a cloud fallback or silently download such a model.

Actual public-audio tests also exposed a problem with the earlier Hindi example prompt and disabled temperature fallback: Base could echo the prompt or generate repetitive nonsense. The new native policy removes invented prompt text and restores normal entropy/log-probability fallback while keeping Hindi transcription fixed. It excludes regular Arabic/Urdu lead-byte tokens during Hindi decoding; Latin remains available. Final checks reject unsupported mixed alphabets and long within-word decoder loops. This changes recognition policy, not just cosmetic cleanup; recognition errors still need honest reporting.

## Validation sources

Public audio is tested separately from controlled formatting cases. The public English clip is the [whisper.cpp JFK sample](https://github.com/ggml-org/whisper.cpp/blob/231638a0717b7822784297e5980965ffcec05810/samples/jfk.wav). Hindi clips are validation rows 0 and 1 of [Google FLEURS, Hindi (hi_in)](https://huggingface.co/datasets/google/fleurs), using the dataset's reference transcripts and CC BY 4.0 attribution. A repeated JFK clip creates an over-two-minute duration smoke test; it is explicitly constructed and does not represent independent long-form speech. The test script also supports a longer run up to 600 seconds. A locally synthesized English command utterance tests the ASR-to-list/paragraph path and is labeled synthetic.

The desktop smoke test uses pywhispercpp 1.5.1 and the same SHA-256-verified Base Q5_1 model artifact and language/task/prompt/sampling settings as the app, then runs its output through the actual Kotlin delivery formatter. Host thread count is configurable and defaults to one; Android uses up to four. Desktop binding/native build, hardware and lifecycle differ from Android. The results establish pipeline behavior for those samples, not an Android device or general accuracy benchmark. Measured results and APK verification are recorded in PATCH_0.1.5.md.
