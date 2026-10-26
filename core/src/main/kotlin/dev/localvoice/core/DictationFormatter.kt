package dev.localvoice.core

import java.text.BreakIterator
import java.util.Locale

enum class FormattingMode(val label: String) { SMART("Smart formatting"), VERBATIM("Original wording") }

/** Local, conservative text rules. No network, generative rewrite or translation. */
object DictationFormatter {
    private val flags=setOf(RegexOption.IGNORE_CASE)
    private val protected=Regex("```[\\s\\S]*?```|`[^`\\n]+`|https?://[^\\s]+|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}|\"[^\"\\n]*\"")
    private val markers=listOf(
        "first|number one|one|पहला|पहले|नंबर एक|एक|1[.)]",
        "second|number two|two|दूसरा|दूसरे|नंबर दो|दो|2[.)]",
        "third|number three|three|तीसरा|तीसरे|नंबर तीन|तीन|3[.)]",
        "fourth|number four|four|चौथा|चार|4[.)]",
        "fifth|number five|five|पाँचवाँ|पांचवां|पांच|पाँच|5[.)]",
        "sixth|number six|six|छठा|छह|6[.)]",
        "seventh|number seven|seven|सातवाँ|सात|7[.)]",
        "eighth|number eight|eight|आठवाँ|आठ|8[.)]",
        "ninth|number nine|nine|नौवाँ|नौ|9[.)]",
        "tenth|number ten|ten|दसवाँ|दस|10[.)]",
    )
    private val listMarker=Regex("(?<![\\p{L}\\p{N}_])(?:${markers.joinToString("|")})(?![\\p{L}\\p{N}_])[,):.।]?\\s+",flags)
    private val bullet=Regex("(?<![\\p{L}\\p{N}_])(?:bullet point|next bullet|बुलेट पॉइंट|अगला बुलेट)(?![\\p{L}\\p{N}_])[,.:।]?\\s*",flags)
    private const val listNouns="features|requirements|tasks|items|options|steps|goals|priorities|ingredients|groceries|shopping list|packing list|to-do list|todo list|फीचर्स|विशेषताएँ|सुविधाएँ|ज़रूरतें|जरूरतें|काम|चीज़ें|चीजें|विकल्प|सूची"
    private val titledList=Regex("^(.{0,200}?(?:\\b(?:$listNouns)(?:\\s+(?:are|include|चाहिए|हैं))?|(?:I|we) (?:want|need) (?:these|the following)|मुझे (?:ये|यह|निम्नलिखित)(?: चाहिए)?))\\s*:\\s*(.+)$",flags)
    private val requestedList=Regex("^((?:(?:I|we)\\s+(?:want|need|would like)|(?:please\\s+)?(?:add|include)).{0,100}?\\b(?:$listNouns))\\s*(?:[:,]|\\b(?:including|like|such as|are|namely)\\b)?\\s+(.+)$",flags)
    private val lastConjunction=Regex("\\s+(?:and|और|aur)\\s+",flags)
    private val compoundItems=setOf("research and development","terms and conditions","salt and pepper","bread and butter","health and safety","sales and marketing","trial and error")

    fun format(input: String,mode: FormattingMode=FormattingMode.SMART): String {
        if(mode==FormattingMode.VERBATIM || input.isBlank()) return TranscriptText.normalize(input)
        // Protect verbatim quoted text, links, addresses and code before interpreting voice commands.
        var prefix="\uE000"
        while(input.contains(prefix)) prefix+="\uE000"
        val saved=mutableListOf<String>()
        var text=protected.replace(input) {match -> "$prefix${saved.size}\uE001".also {saved+=match.value} }
        text=command(text,"start a new paragraph|new paragraph|next paragraph|नया पैराग्राफ|अगला पैराग्राफ|न्यू पैराग्राफ", "\n\n")
        text=command(text,"end (?:the )?list|सूची समाप्त", "\n\n")
        text=command(text,"new line|next line|line break|skip a line|नई लाइन|अगली लाइन|न्यू लाइन", "\n")
        val punctuation=listOf(
            "question mark|प्रश्न चिन्ह" to "?", "exclamation point|exclamation mark|विस्मयादिबोधक चिन्ह" to "!",
            "full stop|period(?!\\s+of\\b)|पूर्ण विराम" to ".", "semicolon|अर्ध विराम" to ";", "comma|अल्प विराम" to ",", "colon" to ":",
        )
        punctuation.forEach {(words,symbol) -> text=command(text,words,symbol)}
        // Only unambiguous vocal hesitations, never semantic words such as 'like' or 'actually'.
        text=Regex("(?<![\\p{L}\\p{N}_-])(?:u+m+|u+h+|उम्म|उह)(?![\\p{L}\\p{N}_-])[, ]*",flags).replace(text,"")
        // A narrowly defined numeric correction; other restarts are preserved rather than guessed.
        text=Regex("(?<![\\p{L}\\p{N}_.:+-])([0-9]+(?::[0-9]{2})?)(?:\\s*(am|pm))?[, ]+(?:actually|sorry|rather)[, ]+([0-9]+(?::[0-9]{2})?)(?:\\s*(am|pm))?(?![\\p{L}\\p{N}_:]|\\.[0-9])",flags).replace(text) {m ->
            m.groupValues[3]+m.groupValues[4].ifBlank {m.groupValues[2]}.let {if(it.isBlank()) "" else " $it"}
        }
        text=text.split(Regex("\\n\\s*\\n")).joinToString("\n\n",transform=::formatBlock)
        text=clean(text)
        text=paragraphs(text)
        // Restore exactly; e.g. code, an email or a quote mentioning 'new line' stays literal.
        saved.forEachIndexed {i,value -> text=text.replace("$prefix$i\uE001",value)}
        return text.filter { !it.isISOControl() || it=='\n' || it=='\t' }.trim(' ','\t')
    }

    private fun command(text: String,words: String,replacement: String)=Regex(
        "(?<![\\p{L}\\p{N}_])(?:$words)(?![\\p{L}\\p{N}_])[,.:।]?",flags,
    ).replace(text) {replacement}

    private fun formatBlock(block: String): String {
        val bullets=bullet.findAll(block).toList()
        if(bullets.isNotEmpty()) {
            val intro=block.substring(0,bullets.first().range.first).trim()
            val items=bullets.mapIndexed {index,match -> block.substring(match.range.last+1,bullets.getOrNull(index+1)?.range?.first ?: block.length).trim()}
            if(items.all(String::isNotBlank)) return (listOf(intro).filter(String::isNotBlank)+items.map {"• ${capital(it)}"}).joinToString("\n")
        }
        val matches=listMarker.findAll(block).toList()
        val numbers=matches.map {match -> markers.indexOfFirst {Regex("^(?:$it)[,):.।]?\\s+$",flags).matches(match.value)}+1}
        // Require 1, 2, ... in order; don't turn arbitrary numbers/ordinal mentions into a list.
        val intro=matches.firstOrNull()?.let {block.substring(0,it.range.first).trim()}.orEmpty()
        val startsList=intro.isBlank() || intro.endsWith(":") || Regex("\\b(?:list|steps|tasks|agenda|points|errands)\\b",flags).containsMatchIn(intro)
        if(matches.size>=2 && numbers==numbers.indices.map {it+1} && startsList) {
            val items=matches.mapIndexed {index,match -> block.substring(match.range.last+1,matches.getOrNull(index+1)?.range?.first ?: block.length).trim()}
            val ordinaryOrdinal=Regex("^(?:time|times|year|years|day|days|moment|minute|minutes|hour|hours|of|floor|class|place|child|name|बार|साल|दिन)(?![\\p{L}\\p{N}_])",flags)
            if(items.all {it.isNotBlank() && !ordinaryOrdinal.containsMatchIn(it)}) return (listOf(intro).filter(String::isNotBlank)+items.mapIndexed {index,item -> "${index+1}. ${capital(item)}"}).joinToString("\n")
        }
        return naturalList(block) ?: block
    }

    private fun naturalList(block: String): String? {
        if(block.contains('\n')) return null
        val match=titledList.matchEntire(block) ?: requestedList.matchEntire(block) ?: return null
        val intro=match.groupValues[1].trim().trimEnd(':',',')+":"
        val body=match.groupValues[2].trim()
        val sentences=BreakIterator.getSentenceInstance(Locale.ROOT).apply {setText(body)}
        sentences.first()
        val end=sentences.next().let {if(it==BreakIterator.DONE) body.length else it}
        val enumeration=body.substring(0,end).trim()
        val tail=body.substring(end).trim()
        // Split at top-level delimiters only; keep parentheses, protected quotes and 1,000 intact.
        val items=mutableListOf<String>();var start=0
        val stack=mutableListOf<Char>()
        enumeration.forEachIndexed {index,c ->
            when(c) {
                '(','[','{' -> stack+=c
                ')',']','}' -> {
                    val expected=when(c) {')'->'(';']'->'[';else->'{'}
                    if(stack.lastOrNull()!=expected) return null
                    stack.removeAt(stack.lastIndex)
                }
                ',', ';' -> if(stack.isEmpty() && !(c==',' && enumeration.getOrNull(index-1)?.isDigit()==true && enumeration.getOrNull(index+1)?.isDigit()==true)) {
                    items+=enumeration.substring(start,index).trim();start=index+1
                }
            }
        }
        if(stack.isNotEmpty()) return null
        items+=enumeration.substring(start).trim()
        val last=items.last()
        val withoutAnd=last.replace(Regex("^(?:and|और|aur)\\s+",flags),"")
        if(withoutAnd!=last) items[items.lastIndex]=withoutAnd
        else if(last.trimEnd('.','।').lowercase(Locale.ROOT) !in compoundItems && last.none {it in "()[]{}"}) {
            val conjunction=lastConjunction.findAll(last).lastOrNull()
            if(conjunction!=null) {
                items[items.lastIndex]=last.substring(0,conjunction.range.first).trim()
                items+=last.substring(conjunction.range.last+1).trim()
            }
        }
        // Short noun/task items only. Don't reinterpret a paragraph with several clauses as a list.
        if(items.size !in 2..20 || items.any {it.isBlank() || it.length>120 || it.split(Regex("\\s+")).size>12}) return null
        return intro+"\n"+items.joinToString("\n") {"• ${capital(it)}"}+if(tail.isNotEmpty()) "\n\n$tail" else ""
    }

    private fun clean(input: String): String {
        var text=input.replace(Regex("[ \\t]+")," ").replace(Regex(" *\\n *"),"\n")
        text=text.replace(Regex("[ \\t]+([,.;:!?।])"),"$1")
        text=text.replace(Regex("([,;:])(?:[ \\t]*\\1)+"),"$1")
        text=text.replace(Regex("([,;:])\\."),"$1")
        text=text.replace(Regex("\\n{3,}"),"\n\n")
        return text.lines().joinToString("\n") {line ->
            if(line.startsWith("• ") || Regex("^[0-9]+\\. ").containsMatchIn(line)) line else capital(line.trim())
        }.trim(' ','\t')
    }

    private fun capital(text: String): String {
        if(text.takeWhile(Char::isLetter).any(Char::isUpperCase)) return text
        return text.replaceFirstChar {if(it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()}
    }

    private fun paragraphs(text: String): String = text.split("\n\n").joinToString("\n\n") {block ->
        if(block.contains('\n')) return@joinToString block
        // Readable long-form paragraphs, not an attempt to infer topic changes or summarize.
        val breaks=BreakIterator.getSentenceInstance(Locale.ROOT).apply {setText(block)}
        val pieces=mutableListOf<String>();var start=breaks.first();var end=breaks.next()
        while(end!=BreakIterator.DONE) {pieces+=block.substring(start,end).trim();start=end;end=breaks.next()}
        val words=block.split(Regex("\\s+"))
        if(words.size>=140 && pieces.size<4) return@joinToString words.chunked(70).joinToString("\n\n") {it.joinToString(" ")}
        var result=if(words.size>=90 && pieces.size>=4) pieces.chunked(3).joinToString("\n\n") {it.joinToString(" ")} else block
        result=Regex("^(Hi|Hello|Dear) ([\\p{L}'-]{1,30}), +",flags).replace(result,"$1 $2,\n\n")
        result=Regex("([.!?।]) +((?:Best regards|Kind regards|Regards|Cheers|Thanks), +[\\p{Lu}][\\p{L}' -]{0,60}[.!]?)$").replace(result,"$1\n\n$2")
        result.split("\n\n").joinToString("\n\n",transform=::capital)
    }
}
