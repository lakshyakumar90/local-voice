package dev.localvoice.core

import org.junit.Assert.*
import org.junit.Test

class EditableFieldSnapshotTest {
    @Test fun emptyEditorsAcceptMissingSelection() {
        for (text in listOf(null, "")) for (start in -1..0) for (end in -1..0) {
            val field=EditableFieldSnapshot.capture(text,start,end)!!
            assertEquals("",field.text)
            assertEquals(0,field.start)
            assertEquals(0,field.end)
            assertEquals("Hello",TextInsertion.replacement(field.text,field.start,field.end,"Hello"))
        }
    }
    @Test fun emptySelectionCanBecomeKnownDuringRecording() {
        assertEquals(EditableFieldSnapshot.capture(null,-1,-1),EditableFieldSnapshot.capture("",0,0))
    }
    @Test fun placeholderIsNotInsertedOrPreservedAsContent() {
        val field=EditableFieldSnapshot.capture("Type a message",-1,-1,true)!!
        assertEquals(EditableFieldSnapshot.capture("",0,0),field)
        assertEquals("Hello",TextInsertion.replacement(field.text,field.start,field.end,"Hello"))
    }
    @Test fun actualTextMatchingPlaceholderRemainsText() {
        val field=EditableFieldSnapshot.capture("Type a message",14,14,false)!!
        assertEquals("Type a message please",TextInsertion.replacement(field.text,field.start,field.end,"please"))
    }
    @Test fun nonemptyEditorsStillRequireValidSelection() {
        for (text in listOf("existing", " ", "\n")) {
            assertNull(EditableFieldSnapshot.capture(text,-1,-1))
            assertNull(EditableFieldSnapshot.capture(text,0,-1))
        }
        assertNull(EditableFieldSnapshot.capture("abc",2,1))
        assertNull(EditableFieldSnapshot.capture("abc",0,4))
        assertNull(EditableFieldSnapshot.capture("",1,1))
    }
    @Test fun editsAfterRecordingProduceDifferentSnapshots() {
        val captured=EditableFieldSnapshot.capture("Hint",-1,-1,true)
        assertNotEquals(captured,EditableFieldSnapshot.capture("typed",5,5))
        assertNotEquals(EditableFieldSnapshot.capture("typed",5,5),EditableFieldSnapshot.capture("typed",0,0))
    }
    @Test fun subsequentDictationPreservesFirstInsertion() {
        val first=EditableFieldSnapshot.capture(null,-1,-1)!!
        val inserted=TextInsertion.replacement(first.text,first.start,first.end,"Hello")!!
        val next=EditableFieldSnapshot.capture(inserted,inserted.length,inserted.length)!!
        assertEquals("Hello world",TextInsertion.replacement(next.text,next.start,next.end,"world"))
    }
}
