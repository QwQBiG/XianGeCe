package win.iqwqi.xiangece.feature.diting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.data.local.XiangeceDatabase
import win.iqwqi.xiangece.feature.diting.audio.DitingAudioChunk
import win.iqwqi.xiangece.feature.diting.audio.DitingAudioStore
import win.iqwqi.xiangece.feature.diting.data.DitingMarkerEntity
import win.iqwqi.xiangece.feature.diting.data.DitingRepository
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity
import win.iqwqi.xiangece.feature.diting.domain.DitingMarkerType
import win.iqwqi.xiangece.feature.diting.transcription.DitingSignalAnalyzer
import win.iqwqi.xiangece.feature.diting.transcription.DitingTranscriptionCoordinator

@RunWith(AndroidJUnit4::class)
class DitingTranscriptionCoordinatorTest {
    private lateinit var database: XiangeceDatabase
    private lateinit var coordinator: DitingTranscriptionCoordinator

    @Before
    fun createCoordinator() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, XiangeceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coordinator = DitingTranscriptionCoordinator(
            DitingRepository(database.ditingDao(), database, DitingAudioStore(context)),
            DitingSignalAnalyzer(),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun repeatedFinalTranscriptDoesNotDuplicateAutoMarker() = runTest {
        val dao = database.ditingDao()
        val sessionId = dao.upsertSession(DitingSessionEntity(title = "重复回调测试", mode = "professional"))
        val chunk = DitingAudioChunk(0, File("chunk-0.wav"), 0, 8_000)

        coordinator.onChunkReady(
            sessionId = sessionId,
            chunk = chunk,
            languageMode = "zh",
            cloudTranscriptionEnabled = false,
            transcriptionEnabled = true,
        )
        coordinator.onRecognizerTranscript(sessionId, "这部分是考试重点")
        coordinator.onTranscriptReady(sessionId, 0, "这部分是考试重点")

        assertEquals(1, dao.markersBySession(sessionId).size)
    }
    @Test
    fun finalReanalysisRebuildsLocalSignalsAndPreservesOtherSources() = runTest {
        val dao = database.ditingDao()
        val sessionId = dao.upsertSession(DitingSessionEntity(title = "水课最终分析", mode = "water_class"))
        val segmentId = dao.upsertSegment(
            DitingSegmentEntity(
                sessionId = sessionId,
                sequence = 0,
                startMillis = 0,
                endMillis = 8_000,
                text = "还有什么要问的，有同学想问吗？",
                isFinal = true,
                status = "completed",
            ),
        )
        dao.upsertMarker(
            DitingMarkerEntity(
                sessionId = sessionId,
                positionMillis = 1_000,
                type = DitingMarkerType.MANUAL_HIGHLIGHT.key,
                title = "手动重点",
                source = "manual",
            ),
        )
        dao.upsertMarker(
            DitingMarkerEntity(
                sessionId = sessionId,
                segmentId = segmentId,
                positionMillis = 2_000,
                type = DitingMarkerType.AUTO_HIGHLIGHT.key,
                title = "AI重点",
                source = "ai",
            ),
        )

        val signals = coordinator.reanalyzeLocalSignals(sessionId)
        val markers = dao.markersBySession(sessionId)

        assertEquals(1, signals.size)
        assertEquals(DitingMarkerType.AUTO_QUESTION, signals.single().type)
        assertTrue(markers.any { it.source == "manual" && it.type == DitingMarkerType.MANUAL_HIGHLIGHT.key })
        assertTrue(markers.any { it.source == "ai" && it.type == DitingMarkerType.AUTO_HIGHLIGHT.key })
        assertTrue(markers.any { it.source == "local_rule" && it.type == DitingMarkerType.AUTO_QUESTION.key })
    }

    @Test
    fun directSegmentTranscriptDoesNotStealNextRecognizerChunk() = runTest {
        val dao = database.ditingDao()
        val sessionId = dao.upsertSession(DitingSessionEntity(title = "分段队列测试"))

        coordinator.onChunkReady(
            sessionId = sessionId,
            chunk = DitingAudioChunk(0, File("chunk-0.wav"), 0, 8_000),
            languageMode = "zh",
            cloudTranscriptionEnabled = false,
            transcriptionEnabled = true,
        )
        coordinator.onTranscriptReady(sessionId, 0, "第一段已完成")
        coordinator.onChunkReady(
            sessionId = sessionId,
            chunk = DitingAudioChunk(1, File("chunk-1.wav"), 8_000, 16_000),
            languageMode = "zh",
            cloudTranscriptionEnabled = false,
            transcriptionEnabled = true,
        )
        coordinator.onRecognizerTranscript(sessionId, "第二段实时文字")

        val segments = dao.allSegments(sessionId)
        assertEquals("第一段已完成", segments[0].text)
        assertEquals("第二段实时文字", segments[1].text)
    }
    @Test
    fun finalTranscriptClearsPartialBeforeNextChunk() = runTest {
        val dao = database.ditingDao()
        val sessionId = dao.upsertSession(DitingSessionEntity(title = "测试课堂"))

        coordinator.onTranscriptPartial(sessionId, "上一段半句")
        coordinator.onChunkReady(
            sessionId = sessionId,
            chunk = DitingAudioChunk(0, File("chunk-0.wav"), 0, 8_000),
            languageMode = "zh",
            cloudTranscriptionEnabled = false,
            transcriptionEnabled = true,
        )
        coordinator.onRecognizerTranscript(sessionId, "上一段完整")
        coordinator.onChunkReady(
            sessionId = sessionId,
            chunk = DitingAudioChunk(1, File("chunk-1.wav"), 8_000, 16_000),
            languageMode = "zh",
            cloudTranscriptionEnabled = false,
            transcriptionEnabled = true,
        )

        val segments = dao.allSegments(sessionId)
        assertEquals("上一段完整", segments[0].text)
        assertEquals("", segments[1].text)
    }
}