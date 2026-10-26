package dev.localvoice.core

/** Delivery policy for this English/Hindi app; never silently discard an unwanted-script word. */
object DictationOutput {
    fun finish(raw: String,language: LanguageMode): String {
        val text=TranscriptText.normalize(raw)
        // Hindi-script mode must not silently deliver an English-only model paraphrase.
        // Mixed mode intentionally accepts English-only utterances, so this is not safe there.
        require(language!=LanguageMode.HINDI || text.none(Char::isLetter) || text.any {it in '\u0900'..'\u097F'}) {
            "The model did not return Hindi script. Nothing was inserted. Try recording again; Whisper Small may recognize Hindi better than Base."
        }
        require(text.none { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF' }) {
            "The model returned an unsupported Arabic/Urdu script. Nothing was inserted. Try a shorter recording with Hindi or English + Roman Hindi selected; consider Whisper Small if Base keeps missing words."
        }
        return if(language==LanguageMode.MIXED) RomanHindi.convert(text) else text
    }
}
