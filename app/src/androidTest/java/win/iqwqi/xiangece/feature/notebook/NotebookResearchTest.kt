package win.iqwqi.xiangece.feature.notebook

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import win.iqwqi.xiangece.core.security.ApiKeyCipher
import win.iqwqi.xiangece.data.local.XiangeceDatabase
import win.iqwqi.xiangece.data.settings.AppSettings
import win.iqwqi.xiangece.feature.diting.audio.DitingAudioStore
import win.iqwqi.xiangece.feature.diting.data.*
import win.iqwqi.xiangece.feature.notebook.data.*

/** Synthetic protocol/material tests; these do not measure model or microphone accuracy. */
@RunWith(AndroidJUnit4::class)
class NotebookResearchTest {
    private lateinit var root: File
    private lateinit var database: XiangeceDatabase
    private lateinit var collection: NotebookCollection
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        root = File(app.cacheDir, "notebook-research-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = File(root, "files").apply { mkdirs() } }
        database = Room.inMemoryDatabaseBuilder(context, XiangeceDatabase::class.java).build()
        collection = NotebookCollection(NotebookRepository(context), DitingRepository(database.ditingDao(), database, DitingAudioStore(context)))
    }
    @After fun cleanup() {
        database.close()
        check(root.canonicalFile.parentFile == ApplicationProvider.getApplicationContext<Context>().cacheDir.canonicalFile)
        check(root.name.startsWith("notebook-research-")); root.deleteRecursively()
    }
    @Test fun sourceSnapshotDetectsRevisionChangesAndRetainsAudio() = runBlocking {
        val dao = database.ditingDao()
        val audio = File(root, "original.wav").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val id = dao.upsertSession(DitingSessionEntity(title = "合成课堂", status = "completed", audioPath = audio.path, createdAtEpochMillis = 123))
        dao.upsertSegment(DitingSegmentEntity(sessionId = id, sequence = 0, startMillis = 1000, endMillis = 8000,
            text = "矩阵可逆的条件是行列式不为零。", isFinal = true))
        val draft = collection.fromClass(id)
        collection.persist(draft)
        assertEquals(draft, collection.restoreDraft())
        assertTrue(collection.status(draft.source).changedBlocks.isEmpty())
        val segment = dao.allSegments(id).single()
        collection.diting.updateSegmentText(segment, "修正后的转写文字")
        assertEquals(setOf(draft.source.blocks.single().id), collection.status(draft.source).changedBlocks)
        val note = collection.save(draft)
        assertEquals("矩阵可逆的条件是行列式不为零。", note.record.sources.single().text)
        assertEquals(note.record.id, collection.save(draft).record.id)
        dao.deleteSession(id)
        assertTrue(collection.status(draft.source).changedBlocks.isNotEmpty())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), audio.readBytes())
    }
    @Test fun recordingSessionsCannotBeCollectedAsFinishedTranscripts() = runBlocking {
        val id = database.ditingDao().upsertSession(DitingSessionEntity(title = "录音中", status = "recording"))
        assertTrue(runCatching { collection.fromClass(id) }.isFailure)
        assertTrue(collection.repository.store.list().isEmpty())
    }
    private fun settings(cipher: ApiKeyCipher) = AppSettings(aiEnabled = true, aiModel = "synthetic-test",
        aiBaseUrl = "https://notebook-test.invalid/v1", encryptedApiKey = cipher.encrypt("synthetic-test-key"))
    private fun response(request: Request, content: String, code: Int = 200): Response {
        val body = buildJsonObject { putJsonArray("choices") { add(buildJsonObject { putJsonObject("message") { put("content", content) } }) } }.toString()
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic")
            .body(body.toResponseBody()).build()
    }
    @Test fun cloudAdapterSendsOnlyChosenTextAndRejectsBadEvidence() = runBlocking {
        val cipher = ApiKeyCipher()
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer(); chain.request().body!!.writeTo(buffer); requests += buffer.readUtf8()
            assertEquals("Bearer synthetic-test-key", chain.request().header("Authorization"))
            response(chain.request(), if (requests.size == 1) """{"terms":["状态栏","安全区"]}""" else
                """{"points":[{"text":"留出安全区域","evidence":[{"id":"p1","quote":"为状态栏保留安全区域"}]},{"text":"无效引用","evidence":[{"id":"unknown","quote":"不在资料中的文字"}]}]}""")
        }.build()
        val service = NotebookCloud(client, cipher)
        val config = settings(cipher)
        assertEquals(2, service.expand("顶部被挡住", config).size)
        val result = service.grounded("如何处理", mapOf("p1" to "为状态栏保留安全区域，其他内容正常滚动。"), config)
        assertEquals(1, result.points.size); assertEquals(1, result.rejected)
        assertFalse(requests.first().contains("为状态栏保留安全区域"))
        assertFalse(requests.last().contains("image_url"))
        assertFalse(service.configured(config.copy(aiEnabled = false)))
        assertFalse(service.sameService(config, config.copy(aiBaseUrl = "https://another.invalid")))
        assertTrue(runCatching { service.expand("描述", config.copy(aiEnabled = false)) }.isFailure)
        assertEquals(2, requests.size)
    }
    @Test fun cancellationStopsPendingRequestAndFailureDoesNotWriteNotes() = runBlocking {
        val entered = CountDownLatch(1)
        val cipher = ApiKeyCipher()
        val service = NotebookCloud(OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown()
            repeat(300) { if (chain.call().isCanceled()) throw java.io.IOException("Cancelled"); Thread.sleep(10) }
            response(chain.request(), "{}", 503)
        }.build(), cipher)
        val request = launch { service.expand("合成描述", settings(cipher)) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(10, TimeUnit.SECONDS) })
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
        val failing = NotebookCloud(OkHttpClient.Builder().addInterceptor { response(it.request(), "{}", 503) }.build(), cipher)
        assertTrue(runCatching { failing.expand("合成描述", settings(cipher)) }.isFailure)
        assertTrue(collection.repository.store.list().isEmpty())
    }
    @Test fun failedOrganizationRetainsDraftAndRetryRequiresFreshConsent() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(application) { override fun getFilesDir() = File(root, "files").apply { mkdirs() } }
        val prefsJob = SupervisorJob()
        val prefs = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + prefsJob)) { File(root, "retry.preferences_pb") }
        val settings = win.iqwqi.xiangece.data.settings.AppSettingsStore(prefs)
        val cipher = ApiKeyCipher()
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        var blockId = ""
        val material = "方阵可逆的条件是行列式不为零。"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            when (calls.incrementAndGet()) {
                1 -> response(chain.request(), "{}", 503)
                2 -> response(chain.request(), """{"points":[{"text":"行列式决定方阵是否可逆","evidence":[{"id":"$blockId","quote":"$material"}]}]}""")
                else -> {
                    entered.countDown()
                    repeat(1000) {
                        if (chain.call().isCanceled()) { cancelled.countDown(); throw java.io.IOException("Cancelled") }
                        Thread.sleep(10)
                    }
                    response(chain.request(), "{}")
                }
            }
        }.build()
        val ocr = win.iqwqi.xiangece.core.ocr.OfflineOcrService(context, win.iqwqi.xiangece.core.ocr.OfflineOcrPackManager(context, client))
        val model = win.iqwqi.xiangece.feature.notebook.ui.NotebookResearchModel(collection, NotebookCloud(client, cipher), settings, ocr)
        suspend fun act(block: () -> Unit) {
            withContext(Dispatchers.Main) { block() }
            withTimeout(10000) { model.state.first { !it.busy } }
        }
        try {
            settings.updateAi(true, "Test", "https://notebook-test.invalid/v1", "synthetic-test", "",
                "Authorization: Bearer {key}", false, cipher.encrypt("synthetic-test-key"))
            act { model.beginText(material, "失败重试样例") }
            blockId = model.state.value.draft!!.source.blocks.single().id
            act { model.prepare("organize") }; act { model.confirm() }
            assertEquals("organize", model.state.value.retryKind)
            assertEquals(material, model.state.value.draft!!.source.text)
            assertTrue(model.state.value.message.orEmpty().contains("503"))
            act { model.retry() }
            assertEquals(1, calls.get()); assertNotNull(model.state.value.consent)
            act { model.confirm(); model.confirm() }
            assertEquals(2, calls.get()); assertEquals(1, model.state.value.draft!!.points.size)
            assertTrue(collection.repository.store.list().isEmpty())
            act { model.prepare("organize") }
            withContext(Dispatchers.Main) { model.confirm() }
            assertTrue(withContext(Dispatchers.IO) { entered.await(10, TimeUnit.SECONDS) })
            act { model.stopNetwork() }
            assertTrue(withContext(Dispatchers.IO) { cancelled.await(10, TimeUnit.SECONDS) })
            assertFalse(model.state.value.busy)
            assertEquals("organize", model.state.value.retryKind)
            assertEquals(1, model.state.value.draft!!.points.size)
            assertEquals(material, collection.restoreDraft()!!.source.text)
            assertTrue(collection.repository.store.list().isEmpty())
        } finally {
            withContext(Dispatchers.Main) { model.viewModelScope.cancel() }
            prefsJob.cancelAndJoin()
        }
    }

    @Test fun everyRequestNeedsConsentAndChangedConfigurationInvalidatesIt() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(application) { override fun getFilesDir() = File(root, "files").apply { mkdirs() } }
        val prefsJob = SupervisorJob()
        val prefs = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + prefsJob)) { File(root, "test.preferences_pb") }
        val settings = win.iqwqi.xiangece.data.settings.AppSettingsStore(prefs)
        val cipher = ApiKeyCipher()
        suspend fun configure(model: String) = settings.updateAi(true, "Test", "https://notebook-test.invalid/v1", model,
            "", "Authorization: Bearer {key}", false, cipher.encrypt("synthetic-test-key"))
        val note = collection.repository.change { create("安全区域", text = "为状态栏保留安全区域。") }
        collection.repository.change { create("无关的材料", text = "PRIVATE-EXCLUDED") }
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = okio.Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            assertFalse(body.contains("PRIVATE-EXCLUDED"))
            response(chain.request(), if (calls.incrementAndGet() == 1) """{"terms":["状态栏","安全区域"]}""" else
                """{"points":[{"text":"保留系统安全区","evidence":[{"id":"${note.record.id}-0","quote":"为状态栏保留安全区域"}]}]}""")
        }.build()
        val ocr = win.iqwqi.xiangece.core.ocr.OfflineOcrService(context, win.iqwqi.xiangece.core.ocr.OfflineOcrPackManager(context, client))
        val model = win.iqwqi.xiangece.feature.notebook.ui.NotebookResearchModel(collection, NotebookCloud(client, cipher), settings, ocr)
        suspend fun settled() { withTimeout(10000) { model.state.first { !it.busy } } }
        suspend fun act(block: () -> Unit) { withContext(Dispatchers.Main) { block() }; settled() }
        try {
            configure("first")
            act { model.question("顶部被挡住"); model.prepare("expand") }
            assertNotNull(model.state.value.consent); assertEquals(0, calls.get())
            configure("changed")
            act { model.confirm() }
            assertEquals(0, calls.get()); assertTrue(model.state.value.message.orEmpty().contains("配置已变化"))
            act { model.prepare("expand") }; act { model.confirm() }
            assertEquals(1, calls.get()); assertTrue(model.state.value.passages.isNotEmpty())
            act { model.prepare("answer") }
            assertEquals(1, calls.get())
            act { model.confirm(); model.confirm() }
            assertEquals(2, calls.get()); assertEquals(1, model.state.value.answer?.points?.size)
            act { model.keepAnswer() }
            assertEquals("notes", model.state.value.draft?.source?.kind)
            act { model.save {}; model.save {} }
            assertEquals(3, collection.repository.store.list().size)
            val saved = collection.repository.store.list().first { it.record.sources.isNotEmpty() }
            assertTrue(collection.status(saved.record.sources.single()).changedBlocks.isEmpty())
            collection.repository.change { save(note.record.id, "用户更正了文字", note.revision) }
            assertTrue(collection.status(saved.record.sources.single()).changedBlocks.isNotEmpty())
        } finally {
            withContext(Dispatchers.Main) { model.viewModelScope.cancel() }
            prefsJob.cancelAndJoin()
        }
    }
}
