package dev.localvoice.app.data

import dev.localvoice.core.DownloadResume
import dev.localvoice.core.ModelFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Platform-independent verified transfer, exercised with a local mock server. */
class HttpFileDownloader(private val client: OkHttpClient,private val allowInsecureForTests: Boolean = false) {
    suspend fun fetch(spec: ModelFile,target: File,onProgress: suspend (Long) -> Unit = {}) = withContext(Dispatchers.IO) {
        require(spec.name == target.name && !spec.name.contains("..") && !spec.name.contains('/') && !spec.name.contains('\\'))
        require(allowInsecureForTests || spec.url.startsWith("https://"))
        require(spec.bytes>0 && spec.sha256.matches(Regex("[a-f0-9]{64}")))
        if(verify(target,spec)) return@withContext
        target.delete()
        val partial=File(target.path+".part")
        val etagFile=File(target.path+".etag")
        if(partial.length()>spec.bytes) {partial.delete();etagFile.delete()}
        if(verify(partial,spec)) {check(partial.renameTo(target));etagFile.delete();onProgress(spec.bytes);return@withContext}
        var offset=partial.length()
        val request=Request.Builder().url(spec.url).header("User-Agent","LocalVoice/0.1").apply {
            if(offset>0) {header("Range","bytes=$offset-");if(etagFile.isFile) header("If-Range",etagFile.readText())}
        }.build()
        val call=client.newCall(request)
        val cancellation=CoroutineScope(coroutineContext).launch(start=CoroutineStart.UNDISPATCHED) {try {awaitCancellation()} finally {call.cancel()}}
        try {call.execute().use {response ->
            if(!allowInsecureForTests && !response.request.url.isHttps) throw IOException("An insecure model redirect was refused.")
            if(response.code==416) {partial.delete();etagFile.delete();throw IOException("The remote model changed. Retry the download.")}
            if(!response.isSuccessful) throw IOException("Download server returned ${response.code}. Retry later.")
            if(offset>0 && response.code==200) {partial.delete();offset=0}
            else if(offset>0 && !DownloadResume.mayAppend(offset,response.code,response.header("Content-Range"))) {partial.delete();etagFile.delete();throw IOException("The server returned an invalid download range.")}
            if(offset==0L && response.code==206 && !response.header("Content-Range").orEmpty().startsWith("bytes 0-")) throw IOException("Invalid initial download range.")
            response.header("ETag")?.takeIf {it.length<=256}?.let(etagFile::writeText)
            val body=response.body ?: throw IOException("Download was empty.")
            if(body.contentLength()>=0 && body.contentLength()+offset!=spec.bytes) {partial.delete();throw IOException("Unexpected model file length.")}
            body.byteStream().use {input -> FileOutputStream(partial,offset>0).use {output ->
                val buffer=ByteArray(65536)
                while(true) {
                    coroutineContext.ensureActive()
                    val count=input.read(buffer)
                    if(count<0) break
                    if(offset+count>spec.bytes) throw IOException("Model exceeded its expected size.")
                    output.write(buffer,0,count);offset+=count;onProgress(offset)
                }
                output.fd.sync()
            }}
        }} catch(error: IOException) {coroutineContext.ensureActive();throw error} finally {cancellation.cancel()}
        if(!verify(partial,spec)) {partial.delete();etagFile.delete();throw IOException("Model integrity check failed. Download again.")}
        check(partial.renameTo(target)) {"Could not install a model file."}
        etagFile.delete()
    }
    companion object {
        fun verify(file: File,spec: ModelFile): Boolean = file.isFile && file.length()==spec.bytes && sha256(file)==spec.sha256
        fun sha256(file: File): String {
            val hash=MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use {input -> val buffer=ByteArray(65536);while(true) {val count=input.read(buffer);if(count<0) break;hash.update(buffer,0,count)}}
            return hash.digest().joinToString("") {"%02x".format(it)}
        }
    }
}
