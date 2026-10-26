package dev.localvoice.core

/** Delivery policy for this English/Hindi app; never silently discard an unwanted-script word. */
object DictationOutput {
    private val repeatedFragment=Regex("([\\p{L}\\p{M}]{2,12})\\1{5,}")
    private val supportedHindiScripts=setOf(Character.UnicodeScript.LATIN,Character.UnicodeScript.DEVANAGARI,Character.UnicodeScript.COMMON,Character.UnicodeScript.INHERITED)
    fun finish(raw: String,language: LanguageMode,formatting: FormattingMode=FormattingMode.VERBATIM): String {
        val text=TranscriptText.normalize(raw)
        require(text.split(Regex("\\s+")).none {it.length>=48 && repeatedFragment.containsMatchIn(it)}) {
            "The model produced a repetitive, unreliable fragment. Nothing was inserted. Try a shorter recording or Whisper Small."
        }
        require(text.none { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF' }) {
            "The model returned an unsupported Arabic/Urdu script. Nothing was inserted. Try a shorter recording with Hindi or English + Roman Hindi selected; consider Whisper Small if Base keeps missing words."
        }
        require(language==LanguageMode.ENGLISH || text.codePoints().noneMatch {Character.isLetter(it) && Character.UnicodeScript.of(it) !in supportedHindiScripts}) {
            "The model returned an unsupported writing script. Nothing was inserted. Try a shorter recording or Whisper Small."
        }
        val formatted=DictationFormatter.format(text,formatting)
        // Punctuation/line-break commands are language neutral; don't mistake them for a translation.
        require(language!=LanguageMode.HINDI || formatted.none(Char::isLetter) || text.any {it in '\u0900'..'\u097F'}) {
            "The model did not return Hindi script. Nothing was inserted. Try recording again; Whisper Small may recognize Hindi better than Base."
        }
        return if(language==LanguageMode.MIXED) RomanHindi.convert(formatted) else formatted
    }
}
