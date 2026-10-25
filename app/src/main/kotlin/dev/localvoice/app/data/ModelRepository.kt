package dev.localvoice.app.data

import android.content.Context
import android.os.StatFs
import androidx.work.*
import dev.localvoice.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext

private object ModelFiles {
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    fun lock(id: String) = locks.getOrPut(id) { Mutex() }
}

class ModelCatalog(context: Context) {
    private val source = JSONObject(context.assets.open("models.json").bufferedReader().use { it.readText() })
    val models = source.getJSONArray("models").let { a -> (0 until a.length()).map { parse(a.getJSONObject(it)) } }
    val vad = parse(source.getJSONObject("vad"))
    fun find(id: String) = (models+vad).firstOrNull { it.id == id }
    private fun parse(o: JSONObject): ModelDescriptor {
        val files = o.getJSONArray("files")
        return ModelDescriptor(o.getString("id"),o.getString("name"),o.getString("description"),EngineId.valueOf(o.getString("engine")),
            (0 until files.length()).map { i -> files.getJSONObject(i).let { ModelFile(it.getString("name"),it.getString("url"),it.getLong("bytes"),it.getString("sha256")) } },
            o.optBoolean("englishOnly"),o.optInt("recommendedRamGb",4),o.getString("weightLicense"),o.getString("sourceUrl"))
    }
}
class ModelRepository(private val context: Context, val catalog: ModelCatalog) {
    val work = WorkManager.getInstance(context)
    private val root = context.noBackupFilesDir.resolve("models").apply { mkdirs() }
    fun directory(model: ModelDescriptor) = root.resolve(model.id)
    fun staging(model: ModelDescriptor) = root.resolve(".${model.id}.staging").apply { mkdirs() }
    fun installed(model: ModelDescriptor): Boolean = directory(model).let { dir ->
        dir.resolve("verified.txt").takeIf { it.isFile }?.readText() == model.files.joinToString("\n") { it.sha256 } && model.files.all { dir.resolve(it.name).length() == it.bytes }
    }
    fun installedModel(model: ModelDescriptor): InstalledModel { check(installed(model)) { "Download and verify this model before recording." }; return InstalledModel(model,directory(model)) }
    fun download(model: ModelDescriptor, wifiOnly: Boolean = true) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if(wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setInputData(workDataOf("model" to model.id)).addTag("model-download")
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        work.enqueueUniqueWork("model-${model.id}",ExistingWorkPolicy.KEEP,request)
    }
    suspend fun pause(model: ModelDescriptor) = withContext(Dispatchers.IO) { work.cancelUniqueWork("model-${model.id}").result.get(); Unit }
    suspend fun cancel(model: ModelDescriptor) = withContext(Dispatchers.IO) { pause(model); ModelFiles.lock(model.id).withLock { staging(model).deleteRecursively() }; Unit }
    suspend fun delete(model: ModelDescriptor) = withContext(Dispatchers.IO) { pause(model); ModelFiles.lock(model.id).withLock { staging(model).deleteRecursively(); directory(model).deleteRecursively() }; Unit }
}
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val catalog = ModelCatalog(applicationContext)
        val model = inputData.getString("model")?.let(catalog::find) ?: return@withContext Result.failure()
        ModelFiles.lock(model.id).withLock { install(catalog, model) }
    }
    private suspend fun install(catalog: ModelCatalog, model: ModelDescriptor): Result {
        val repo = ModelRepository(applicationContext,catalog)
        if(repo.installed(model)) return Result.success()
        val stage = repo.staging(model)
        val client = OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).followSslRedirects(true).build()
        return try {
            var complete = 0L
            for(file in model.files) {
                currentCoroutineContext().ensureActive()
                require(file.name == File(file.name).name && !file.name.contains("..") && file.url.startsWith("https://"))
                val target = stage.resolve(file.name)
                if(target.isFile && target.length() == file.bytes && hash(target) == file.sha256) { complete += file.bytes; continue }
                val partial = stage.resolve("${file.name}.part")
                check(StatFs(stage.absolutePath).availableBytes > file.bytes-partial.length()+100L*1024*1024) { "Not enough storage. Free some space and retry." }
                var lastUpdate=0L
                HttpFileDownloader(client).fetch(file,target) { bytes ->
                    currentCoroutineContext().ensureActive()
                    if(isStopped) throw kotlinx.coroutines.CancellationException("Download paused")
                    if(System.currentTimeMillis()-lastUpdate >= 300) {
                        setProgress(workDataOf("done" to complete+bytes,"total" to model.bytes,"phase" to "Downloading"))
                        lastUpdate=System.currentTimeMillis()
                    }
                }
                setProgress(workDataOf("done" to complete+file.bytes,"total" to model.bytes,"phase" to "Checking model files"))
                complete += file.bytes
            }
            stage.resolve("verified.txt").writeText(model.files.joinToString("\n") { it.sha256 })
            val destination = repo.directory(model)
            if(destination.exists()) destination.deleteRecursively()
            check(stage.renameTo(destination)) { "Could not finish model installation." }
            Result.success()
        } catch(cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch(error: Exception) { currentCoroutineContext().ensureActive();Result.failure(workDataOf("error" to (error.message ?: "Could not download the model."))) }
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input -> val buffer = ByteArray(65536); while(true) { val count = input.read(buffer); if(count < 0) break; digest.update(buffer,0,count) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
