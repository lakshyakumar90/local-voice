# 0.1.2: Hindi-led recognition and input-field entry

## Recognition

The previous mixed mode passed `auto` to Whisper. Auto-detection could choose Urdu or English for an entire Hindi/English recording. It now passes `hi` explicitly, as a Hindi-led mixed-speech mode, then locally romanizes recognized Devanagari as before. English-only recordings should use English mode (`en`). Translation remains disabled in the native adapter; no English translation model or network service is used.

Final output is checked before result display, history saving and accessibility delivery. Arabic/Urdu-script output is rejected with an actionable error rather than silently deleting those words or inserting the wrong script. This guard does not recognize incorrect English words or guarantee accurate Hindi. A rejected recording must be recorded again; this patch does not retain cancelled/failed audio for retries.

Whisper Base has limited recognition capacity. Small is a reasonable next model to try, with higher memory/compute cost; no accuracy or latency guarantee is claimed for this phone. OpenAI documents model-size speed/accuracy tradeoffs and substantial language-dependent performance: [upstream Whisper README](https://github.com/openai/whisper#available-models-and-languages), checked through Agent Reach's Jina reader on 2026-10-25. The upstream GPU figures are not Android quantized-model benchmarks.

Hindi-led decoding can render some English words in Devanagari. Romanization is deterministic and approximate; it cannot reconstruct the intended English spelling or fix missing/misrecognized words. This patch does not claim perfect code-switching or audio-quality validation. No user audio sample or attached Android device was available for recognition comparison.

## Entry points

The input-field microphone bubble stays: it requires a focused eligible editor, user consent, overlay permission and the connected accessibility service. Normal in-app recording also remains available.

The user clarified that the unwanted side button is Android's floating Accessibility shortcut. App-provided launcher/pinned shortcuts, the keyboard shortcut and Quick Settings recording service remain available. They were not the unwanted system button.

An Android-managed Accessibility shortcut button is a separate OS setting. The app's accessibility XML never requested `flagRequestAccessibilityButton`; the app cannot deselect a user's system shortcut. Settings now explains how to turn off **Local Voice shortcut / Accessibility button** while leaving **Use Local Voice** enabled. Device labels may differ on Motorola Android 17. No settings are changed silently on the user's phone.

Android reference: [Use and remove accessibility shortcuts](https://support.google.com/accessibility/android/answer/7650693?hl=en), checked with Agent Reach's Jina reader. The app's launcher, keyboard and tile shortcuts are distinct from this system-owned shortcut.

## Verification

Romanization now applies a right-to-left medial-schwa rule; the user's supplied phrase is covered: **क्या तुम मुझे जानते हो → kya tum mujhe jaante ho**, including जानते → jaante rather than jaanate. This fixes a spelling error independently of ASR. The user supplied the intended phrase but not the incorrect recognition output, so it is not an audio-recognition benchmark.

Final verification: `:core:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` succeeded in 3 minutes 13 seconds. All 22 tests pass; lint reports zero errors. APK signature verification, same-certificate upgrade check and 16 KB ZIP alignment pass. The packaged manifest retains the app's tile and launcher shortcuts after the user's clarification. Version 0.1.2 is an installable debug build; a release variant was not rebuilt.

Regression tests cover that phrase, explicit mixed-mode Hindi selection, final English/Devanagari handling and whole-result rejection of Urdu-script output. Device microphone, recognition quality and the Android shortcut setting still need phone verification. Passing the spelling test does not prove that Base or Small recognizes this sentence correctly from audio.
