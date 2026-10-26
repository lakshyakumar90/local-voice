package dev.localvoice.core

/** Owns decrypted playback bytes. Closing erases this buffer; no plaintext file is created. */
class RecordingBytes(private val bytes: ByteArray) : AutoCloseable {
    private var closed=false
    @Synchronized fun size(): Long = if(closed) 0 else bytes.size.toLong()
    @Synchronized fun read(position: Long,buffer: ByteArray,offset: Int,size: Int): Int {
        check(!closed)
        require(position>=0 && offset>=0 && size>=0 && offset<=buffer.size-size)
        if(size==0) return 0
        if(position>=bytes.size) return -1
        val count=minOf(size.toLong(),bytes.size-position).toInt()
        bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+count)
        return count
    }
    @Synchronized override fun close() {bytes.fill(0);closed=true}
}
