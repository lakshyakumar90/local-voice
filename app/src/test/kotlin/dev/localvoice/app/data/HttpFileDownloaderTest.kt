package dev.localvoice.app.data

import dev.localvoice.core.ModelFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class HttpFileDownloaderTest {
    @get:Rule val temp=TemporaryFolder()
    private val bytes="verified speech model fixture".toByteArray()
    private fun spec(server: MockWebServer,hash: String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)})=ModelFile("model.bin",server.url("/model").toString(),bytes.size.toLong(),hash)
    private fun downloader()=HttpFileDownloader(OkHttpClient.Builder().readTimeout(2,TimeUnit.SECONDS).build(),allowInsecureForTests=true)
    @Test fun fullTransferChecksHashBeforeMakingFileAvailable() = runBlocking {
        MockWebServer().use {server -> server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val target=File(temp.root,"model.bin");downloader().fetch(spec(server),target)
            assertArrayEquals(bytes,target.readBytes());assertFalse(File(target.path+".part").exists())
        }
    }
    @Test fun resumeAppendsOnlyTheRequestedRange() = runBlocking {
        MockWebServer().use {server -> server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range","bytes 10-${bytes.size-1}/${bytes.size}").setBody(Buffer().write(bytes.copyOfRange(10,bytes.size))))
            val target=File(temp.root,"model.bin");File(target.path+".part").writeBytes(bytes.copyOfRange(0,10))
            downloader().fetch(spec(server),target);assertArrayEquals(bytes,target.readBytes());assertEquals("bytes=10-",server.takeRequest().getHeader("Range"))
        }
    }
    @Test fun ignoredRangeRestartsRatherThanCorruptingFile() = runBlocking {
        MockWebServer().use {server -> server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val target=File(temp.root,"model.bin");File(target.path+".part").writeBytes(bytes.copyOfRange(0,10))
            downloader().fetch(spec(server),target);assertArrayEquals(bytes,target.readBytes())
        }
    }
    @Test fun wrongRangeCannotBecomeAnInstalledFile() = runBlocking {
        MockWebServer().use {server -> server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range","bytes 0-${bytes.size-1}/${bytes.size}").setBody(Buffer().write(bytes)))
            val target=File(temp.root,"model.bin");File(target.path+".part").writeBytes(bytes.copyOfRange(0,10))
            assertTrue(runCatching {downloader().fetch(spec(server),target)}.isFailure);assertFalse(target.exists())
        }
    }
    @Test fun incorrectHashDeletesUntrustedPartial() = runBlocking {
        MockWebServer().use {server -> server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val target=File(temp.root,"model.bin")
            assertTrue(runCatching {downloader().fetch(spec(server,"0".repeat(64)),target)}.isFailure)
            assertFalse(target.exists());assertFalse(File(target.path+".part").exists())
        }
    }
    @Test fun alreadyCompletePartialIsVerifiedWithoutNetwork() = runBlocking {
        MockWebServer().use {server ->
            val target=File(temp.root,"model.bin");File(target.path+".part").writeBytes(bytes)
            downloader().fetch(spec(server),target);assertArrayEquals(bytes,target.readBytes());assertEquals(0,server.requestCount)
        }
    }
    @Test fun cancellationClosesBlockedNetworkCallWithoutInstallingAnything() = runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val target=File(temp.root,"model.bin")
            val transfer=launch {downloader().fetch(spec(server),target)}
            withContext(Dispatchers.IO) {assertNotNull(server.takeRequest(5,TimeUnit.SECONDS))}
            withTimeout(1500) {transfer.cancelAndJoin()}
            assertFalse(target.exists())
        }
    }
}
