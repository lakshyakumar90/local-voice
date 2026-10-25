package dev.localvoice.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.*
import dev.localvoice.core.AudioClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "transcripts")
data class HistoryEntry(
    @PrimaryKey val id: String, val createdAt: Long,
    val textCipher: ByteArray, val modelName: String, val language: String,
    val durationMs: Long, val audioFile: String?,
)
@Dao interface HistoryDao {
    @Query("SELECT * FROM transcripts ORDER BY createdAt DESC") fun observe(): Flow<List<HistoryEntry>>
    @Query("SELECT * FROM transcripts WHERE id = :id") suspend fun get(id: String): HistoryEntry?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: HistoryEntry): Long
    @Query("DELETE FROM transcripts WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM transcripts WHERE createdAt < :cutoff") suspend fun expired(cutoff: Long): List<HistoryEntry>
    @Query("DELETE FROM transcripts") suspend fun clear()
}
@Database(entities = [HistoryEntry::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() { abstract fun history(): HistoryDao }

class LocalCipher {
    private val alias = "localvoice.history.v1"
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun encrypt(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(bytes)
    }
    fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size > 13)
        val ivSize = bytes[0].toInt() and 255
        require(ivSize in 12..16 && bytes.size > 1 + ivSize)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,1+ivSize))) }.doFinal(bytes,1+ivSize,bytes.size-1-ivSize)
    }
}
class HistoryRepository(context: Context) {
    private val db = Room.databaseBuilder(context,HistoryDatabase::class.java,"history.db").build()
    private val dao = db.history()
    private val cipher = LocalCipher()
    private val audioDir = context.noBackupFilesDir.resolve("recordings").apply { mkdirs() }
    val entries = dao.observe()
    suspend fun text(entry: HistoryEntry): String = withContext(Dispatchers.IO) { String(cipher.decrypt(entry.textCipher),Charsets.UTF_8) }
    suspend fun get(id: String) = dao.get(id)
    suspend fun save(text: String, model: String, language: String, duration: Long, audio: AudioClip? = null, id: String = UUID.randomUUID().toString()): String = withContext(Dispatchers.IO) {
        dao.get(id)?.let { return@withContext it.id }
        val audioName = if(audio != null) "$id.enc" else null
        if(audio != null) {
            val wav = wav(audio)
            try { audioDir.resolve(audioName!!).writeBytes(cipher.encrypt(wav)) } finally { wav.fill(0) }
        }
        try { dao.insert(HistoryEntry(id,System.currentTimeMillis(),cipher.encrypt(text.toByteArray(Charsets.UTF_8)),model,language,duration,audioName)) }
        catch(error: Exception) { audioName?.let { audioDir.resolve(it).delete() }; throw error }
        id
    }
    suspend fun audio(entry: HistoryEntry): ByteArray? = withContext(Dispatchers.IO) { entry.audioFile?.let { cipher.decrypt(audioDir.resolve(it).readBytes()) } }
    suspend fun delete(entry: HistoryEntry) = withContext(Dispatchers.IO) { entry.audioFile?.let { audioDir.resolve(it).delete() }; dao.delete(entry.id) }
    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear(); audioDir.listFiles()?.forEach { it.delete() } }
    suspend fun prune() = withContext(Dispatchers.IO) {
        dao.expired(System.currentTimeMillis()-7L*24*60*60*1000).forEach { delete(it) }
    }
    private fun wav(audio: AudioClip): ByteArray {
        val count = audio.pcm.size*2
        return ByteBuffer.allocate(44+count).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray());putInt(36+count);put("WAVEfmt ".toByteArray());putInt(16)
            putShort(1);putShort(1);putInt(audio.sampleRate);putInt(audio.sampleRate*2);putShort(2);putShort(16)
            put("data".toByteArray());putInt(count)
            audio.pcm.forEach { putShort((it.coerceIn(-1f,1f)*32767).toInt().toShort()) }
        }.array()
    }
}
