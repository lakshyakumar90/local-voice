package dev.localvoice.core

import org.junit.Assert.*
import org.junit.Test

class RecordingBytesTest {
    @Test fun supportsSeekingPartialReadsAndEndOfStream() {
        val source=RecordingBytes(byteArrayOf(1,2,3,4));val target=ByteArray(5)
        assertEquals(2,source.read(2,target,1,4))
        assertArrayEquals(byteArrayOf(0,3,4,0,0),target)
        assertEquals(-1,source.read(Long.MAX_VALUE,target,0,1))
        assertEquals(0,source.read(0,target,0,0))
    }
    @Test fun closeWipesOwnedAudioAndRefusesFurtherReads() {
        val decrypted=byteArrayOf(8,9,10);val source=RecordingBytes(decrypted)
        source.close();source.close()
        assertArrayEquals(ByteArray(3),decrypted)
        assertTrue(runCatching {source.read(0,ByteArray(1),0,1)}.isFailure)
    }
}
