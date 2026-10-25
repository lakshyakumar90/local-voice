package dev.localvoice.core

/** Delivery policy for this English/Hindi app; never silently discard an unwanted-script word. */
object DictationOutput {
    fun finish(raw: String,language: LanguageMode): String {
        val text=TranscriptText.normalize(raw)
        require(text.none { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF' }) {
            "The model returned an unsupported Arabic/Urdu script. Nothing was inserted. Try a shorter recording with Hindi or English + Roman Hindi selected; consider Whisper Small if Base keeps missing words."
        }
        return if(language==LanguageMode.MIXED) RomanHindi.convert(text) else text
    }
}
