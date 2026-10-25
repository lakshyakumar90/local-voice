package dev.localvoice.core

/** Script conversion only. Latin text, URLs and English words are never translated or rewritten. */
object RomanHindi {
    private val runs=Regex("[\\u0900-\\u097F\\u200C\\u200D]+")
    private val consonants=mapOf(
        'क' to "k",'ख' to "kh",'ग' to "g",'घ' to "gh",'ङ' to "ng",
        'च' to "ch",'छ' to "chh",'ज' to "j",'झ' to "jh",'ञ' to "ny",
        'ट' to "t",'ठ' to "th",'ड' to "d",'ढ' to "dh",'ण' to "n",
        'त' to "t",'थ' to "th",'द' to "d",'ध' to "dh",'न' to "n",
        'प' to "p",'फ' to "ph",'ब' to "b",'भ' to "bh",'म' to "m",
        'य' to "y",'र' to "r",'ल' to "l",'व' to "v",'श' to "sh",'ष' to "sh",'स' to "s",'ह' to "h",
        'क़' to "q",'ख़' to "kh",'ग़' to "gh",'ज़' to "z",'ड़' to "r",'ढ़' to "rh",'फ़' to "f",'ळ' to "l")
    private val vowels=mapOf('अ' to "a",'आ' to "aa",'इ' to "i",'ई' to "ee",'उ' to "u",'ऊ' to "oo",'ऋ' to "ri",'ए' to "e",'ऐ' to "ai",'ओ' to "o",'औ' to "au",'ऑ' to "o",'ऍ' to "e")
    private val marks=mapOf('ा' to "aa",'ि' to "i",'ी' to "i",'ु' to "u",'ू' to "oo",'ृ' to "ri",'े' to "e",'ै' to "ai",'ो' to "o",'ौ' to "au",'ॉ' to "o",'ॅ' to "e")
    // Conventional spellings where a mechanical transcription is misleading in everyday Hindi.
    private val common=mapOf("मैं" to "main","में" to "mein","हूँ" to "hoon","हूं" to "hoon","हैं" to "hain","है" to "hai","नहीं" to "nahin","यह" to "yeh","वह" to "voh","और" to "aur","हिंदी" to "Hindi","हिन्दी" to "Hindi","जाना" to "jaana","करना" to "karna","करने" to "karne","करते" to "karte","करता" to "karta","करती" to "karti","आपका" to "aapka","आपकी" to "aapki","अपना" to "apna","अपने" to "apne","मतलब" to "matlab","लेकिन" to "lekin","अंग्रेजी" to "angrezi","अंग्रेज़ी" to "angrezi")
    fun convert(text: String): String = runs.replace(text) { match -> word(match.value) }
    private fun word(word: String): String {
        common[word]?.let {return it}
        val result=StringBuilder()
        var i=0
        while(i<word.length) {
            val c=word[i]
            val consonant=consonants[c]
            if(consonant!=null) {
                var base=consonant
                var next=i+1
                if(word.getOrNull(next)=='़') {
                    base=when(c) {'क'->"q";'ख'->"kh";'ग'->"gh";'ज'->"z";'ड'->"r";'ढ'->"rh";'फ'->"f";else->base}
                    next++
                }
                result.append(base)
                val following=word.getOrNull(next)
                when {
                    following=='्' -> next++
                    marks.containsKey(following) -> {result.append(marks[following]);next++}
                    next<word.length && !vowels.containsKey(following) && following !in listOf('।','॥') -> result.append('a')
                }
                i=next
                continue
            }
            result.append(when {
                vowels.containsKey(c) -> vowels[c]
                marks.containsKey(c) -> marks[c]
                c=='ं' || c=='ँ' -> if(word.getOrNull(i+1) in listOf('प','फ','ब','भ','म')) "m" else "n"
                c=='ः' -> "h"
                c=='्' || c=='़' || c=='\u200C' || c=='\u200D' -> ""
                c in '०'..'९' -> ('0'+(c-'०')).toString()
                c=='।' || c=='॥' -> "."
                else -> c.toString()
            })
            i++
        }
        return result.toString()
    }
}
