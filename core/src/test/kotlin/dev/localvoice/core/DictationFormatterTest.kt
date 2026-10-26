package dev.localvoice.core

import org.junit.Assert.*
import org.junit.Test

class DictationFormatterTest {
    @Test fun naturalFeatureRequestBecomesBulletsWithoutSpokenListCommands() {
        assertEquals("I want these features:\n• UI\n• Front end\n• Back end\n• Database.",DictationFormatter.format("I want these features: UI, front end, back end, database."))
        assertEquals("I want these features:\n• UI\n• Front end\n• Back end\n• Database",DictationFormatter.format("I want these features UI, front end, back end, database"))
    }
    @Test fun naturalListSupportsFinalConjunctionAndOxfordComma() {
        val expected="I need these features:\n• UI\n• Front end\n• Back end\n• Database."
        assertEquals(expected,DictationFormatter.format("I need these features: UI, front end, back end and database."))
        assertEquals(expected,DictationFormatter.format("I need these features: UI, front end, back end, and database."))
    }
    @Test fun listEndsBeforeFollowingProse() {
        assertEquals("Features:\n• UI\n• Front end\n• Database.\n\nPlease ship it tomorrow.",DictationFormatter.format("Features: UI, front end, database. Please ship it tomorrow."))
    }
    @Test fun commaProseWithoutListIntentStaysProse() {
        val text="I worked on UI, front end, back end, and database yesterday."
        assertEquals(text,DictationFormatter.format(text))
        assertEquals("These features are useful, important, and fast.",DictationFormatter.format("These features are useful, important, and fast."))
    }
    @Test fun listPreservesNestedCommasQuotesAndCompoundItems() {
        assertEquals("Features:\n• UI\n• Billing (monthly, yearly)\n• Research and development.",DictationFormatter.format("Features: UI, billing (monthly, yearly), research and development."))
        assertEquals("Options:\n• \"red, green\"\n• Blue\n• Black.",DictationFormatter.format("Options: \"red, green\", blue, black."))
    }
    @Test fun listPreservesThousandsSeparatorsAndTechnicalNames() {
        assertEquals("Requirements:\n• 1,000 MB RAM\n• SSD storage\n• PostgreSQL.",DictationFormatter.format("Requirements: 1,000 MB RAM, SSD storage, PostgreSQL."))
        assertEquals("Features:\n• Node.js\n• Next.js\n• Database.",DictationFormatter.format("Features: Node.js, Next.js, database."))
    }
    @Test fun hindiFeatureListsKeepTheirLanguage() {
        val input="मुझे ये फीचर्स चाहिए: UI, फ्रंट एंड, बैक एंड, डेटाबेस।"
        val hindi=DictationOutput.finish(input,LanguageMode.HINDI,FormattingMode.SMART)
        assertEquals("मुझे ये फीचर्स चाहिए:\n• UI\n• फ्रंट एंड\n• बैक एंड\n• डेटाबेस।",hindi)
        val roman=DictationOutput.finish(input,LanguageMode.MIXED,FormattingMode.SMART)
        assertTrue(roman.contains("\n• UI\n"))
        assertFalse(roman.any {it in '\u0900'..'\u097f'})
    }
    @Test fun naturalListsAreIdempotentAndOriginalModeDoesNotReformat() {
        val raw="I want these features: UI, front end, back end, database."
        val formatted=DictationFormatter.format(raw)
        assertEquals(formatted,DictationFormatter.format(formatted))
        assertEquals(raw,DictationFormatter.format(raw,FormattingMode.VERBATIM))
    }
    @Test fun spokenParagraphAndLineBreaks() {
        assertEquals("Hello.\n\nThis is a new thought.\nCall tomorrow.",DictationFormatter.format("Hello. New paragraph. This is a new thought. Next line. Call tomorrow."))
    }
    @Test fun requestedBreaksSurviveAtInsertionBoundaries() {
        assertEquals("\n\n",DictationOutput.finish("New paragraph.",LanguageMode.ENGLISH,FormattingMode.SMART))
        assertEquals("\n",DictationOutput.finish("नई लाइन",LanguageMode.MIXED,FormattingMode.SMART))
        assertEquals("\n\n",DictationOutput.finish("New paragraph",LanguageMode.HINDI,FormattingMode.SMART))
        assertEquals("\n\nHello.",DictationFormatter.format("New paragraph hello."))
        assertEquals("Hello.\n",DictationFormatter.format("Hello. New line."))
        assertEquals("Before\n\nAfter",TextInsertion.replacement("BeforeAfter",6,6,"\n\n"))
        assertEquals("Before\n\nHello.",TextInsertion.replacement("Before",6,6,DictationFormatter.format("new paragraph hello.")))
        assertNull(TextInsertion.replacement("Before",6,6,"   "))
    }
    @Test fun enumeratedTasksBecomeNumberedList() {
        assertEquals("1. Buy milk.\n2. Call the team.\n3. Send the report.",DictationFormatter.format("First, buy milk. Second, call the team. Third, send the report."))
        assertEquals("Tasks:\n1. Buy milk\n2. Call the team",DictationFormatter.format("Tasks: number one buy milk number two call the team"))
    }
    @Test fun numberedListAlreadyFormattedIsPreserved() {
        assertEquals("1. Buy milk\n2. Call home",DictationFormatter.format("1. Buy milk\n2. Call home"))
    }
    @Test fun explicitBullets() {
        assertEquals("Shopping:\n• Milk\n• Bread\n• Eggs",DictationFormatter.format("Shopping: bullet point milk next bullet bread bullet point eggs"))
    }
    @Test fun spokenNumbersAndListTermination() {
        assertEquals("1. Buy milk\n2. Call home\n\nThank you.",DictationFormatter.format("One buy milk two call home end list thank you."))
        assertEquals("1. दूध लाओ\n2. घर जाओ",DictationFormatter.format("एक दूध लाओ दो घर जाओ"))
        assertEquals("One day we talked. Two years later we met.",DictationFormatter.format("One day we talked. Two years later we met."))
    }
    @Test fun namesAndMeaningfulRepetitionsArePreserved() {
        assertEquals("iPhone is very very useful.",DictationFormatter.format("iPhone is very very useful."))
    }
    @Test fun unrelatedOrdinalsAndNumbersStayInProse() {
        val examples=listOf("My first job was at a second hand shop.","First time we met. Second time we talked.","The first step is here. Second, consider this.","Version 1.2 costs 3.5 dollars.")
        examples.forEach {assertEquals(it,DictationFormatter.format(it))}
    }
    @Test fun punctuationDoesNotKeepSpokenCommandOrRecognizerComma() {
        assertEquals("Hello, how are you?",DictationFormatter.format("Hello, comma, how are you question mark."))
        assertEquals("Yes. No!",DictationFormatter.format("Yes full stop No exclamation point"))
        assertEquals("A period of time.",DictationFormatter.format("A period of time."))
    }
    @Test fun literalQuotesCodeUrlsAndEmailAreNotCommands() {
        val text="Say \"new paragraph\" and use `first, second` at https://example.com/new_line or comma@example.com."
        assertEquals(text,DictationFormatter.format(text))
    }
    @Test fun cleanOnlyClearHesitations() {
        assertEquals("I actually like the design, you know.",DictationFormatter.format("Um, I actually like the design, you know."))
        assertEquals("Uh-huh, that is very very good.",DictationFormatter.format("Uh-huh, that is very very good."))
    }
    @Test fun numericCorrectionDoesNotRemoveSemanticActually() {
        assertEquals("Meet at 3 pm.",DictationFormatter.format("Meet at 2 pm, actually 3."))
        assertEquals("I actually enjoyed the movie.",DictationFormatter.format("I actually enjoyed the movie."))
        assertEquals("It costs 2.5 actually 3.5 dollars.",DictationFormatter.format("It costs 2.5 actually 3.5 dollars."))
    }
    @Test fun hindiFormattingPrecedesRomanConversion() {
        assertEquals("कल मिलते हैं।\n\nमुझे जाना है।",DictationOutput.finish("कल मिलते हैं। नया पैराग्राफ मुझे जाना है।",LanguageMode.HINDI,FormattingMode.SMART))
        assertEquals("हम कल मिलते हैं।\n\nमुझे जाना है।",DictationOutput.finish("उम्म, हम कल मिलते हैं। न्यू पैराग्राफ मुझे जाना है।",LanguageMode.HINDI,FormattingMode.SMART))
        assertEquals("kal milte hain.\n\nmujhe jaana hai.",DictationOutput.finish("कल मिलते हैं। नया पैराग्राफ मुझे जाना है।",LanguageMode.MIXED,FormattingMode.SMART))
    }
    @Test fun hindiListsStayHindi() {
        assertEquals("1. दूध लाओ।\n2. घर जाओ।",DictationFormatter.format("पहला दूध लाओ। दूसरा घर जाओ।"))
    }
    @Test fun greetingAndSignoffAreSeparated() {
        assertEquals("Hi Alex,\n\nPlease send the report.\n\nThanks, Laksh",DictationFormatter.format("Hi Alex, please send the report. Thanks, Laksh"))
    }
    @Test fun longProseBecomesParagraphsWithoutLosingWords() {
        val sentence="This is a long sentence about a project with enough words to exercise the paragraph boundary and keep its original meaning."
        val input=List(6) {sentence}.joinToString(" ")
        val result=DictationFormatter.format(input)
        assertEquals(List(3) {sentence}.joinToString(" ")+"\n\n"+List(3) {sentence}.joinToString(" "),result)
        assertEquals(input,result.replace("\n\n"," "))
    }
    @Test fun longUnpunctuatedRecognitionStillGetsReadableParagraphs() {
        val input=List(150) {if(it==0) "Word0" else "word$it"}.joinToString(" ")
        val result=DictationFormatter.format(input)
        assertTrue(result.contains("\n\n"))
        assertEquals(input,result.replace(Regex("\\s+")," "))
    }
    @Test fun originalWordingModeDoesNotInterpretCommands() {
        val text="Um, first milk second bread new paragraph actually 3."
        assertEquals(text,DictationFormatter.format(text,FormattingMode.VERBATIM))
    }
    @Test fun smartFormattingIsIdempotentForStructuredOutput() {
        val input="First, buy milk. Second, call home. New paragraph. Thank you."
        val once=DictationFormatter.format(input)
        assertEquals(once,DictationFormatter.format(once))
    }
    @Test fun longRecordingAndTargetBudgetsAreConsistent() {
        assertEquals(600,RecordingLimits.MAX_SECONDS)
        assertTrue(RecordingLimits.processingTimeoutMs(180_000)>240_000)
        assertTrue(RecordingLimits.TARGET_LIFETIME_MS>RecordingLimits.MAX_DURATION_MS+RecordingLimits.MAX_PROCESSING_MS)
        assertEquals(RecordingLimits.MAX_PROCESSING_MS,RecordingLimits.processingTimeoutMs(RecordingLimits.MAX_DURATION_MS))
    }
}
