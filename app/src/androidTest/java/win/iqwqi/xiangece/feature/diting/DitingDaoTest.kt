package win.iqwqi.xiangece.feature.diting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import win.iqwqi.xiangece.data.local.XiangeceDatabase
import win.iqwqi.xiangece.feature.diting.audio.DitingAudioStore
import win.iqwqi.xiangece.feature.diting.data.DitingRepository
import win.iqwqi.xiangece.feature.diting.data.DitingMarkerEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity

@RunWith(AndroidJUnit4::class)
class DitingDaoTest {
    private lateinit var database: XiangeceDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            XiangeceDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun sessionStoresPerClassAiPreference() = runTest {
        val dao = database.ditingDao()
        val sessionId = dao.upsertSession(
            DitingSessionEntity(title = "AI 课堂", aiAnnotationEnabled = true),
        )

        assertTrue(dao.sessionById(sessionId)?.aiAnnotationEnabled == true)
    }
    @Test
    fun sessionSegmentsAndMarkersAreStoredAndObserved() = runTest {
        val dao = database.ditingDao()
        val sessionId = dao.upsertSession(DitingSessionEntity(title = "数据结构"))
        dao.upsertSegment(
            DitingSegmentEntity(
                sessionId = sessionId,
                sequence = 1,
                startMillis = 0,
                endMillis = 1_000,
                text = "红黑树保持平衡",
                isFinal = true,
            ),
        )
        dao.upsertMarker(
            DitingMarkerEntity(
                sessionId = sessionId,
                positionMillis = 800,
                type = "manual_highlight",
                title = "重点",
            ),
        )

        assertEquals(1, dao.observeSessions().first().size)
        assertEquals("红黑树保持平衡", dao.observeSegments(sessionId).first().single().text)
        assertEquals(1, dao.observeMarkers(sessionId).first().size)
    }

    @Test
    fun deletingSessionChildrenRemovesOnlyItsData() = runTest {
        val dao = database.ditingDao()
        val first = dao.upsertSession(DitingSessionEntity(title = "第一节"))
        val second = dao.upsertSession(DitingSessionEntity(title = "第二节"))
        dao.upsertMarker(DitingMarkerEntity(sessionId = first, positionMillis = 1, type = "manual_highlight"))
        dao.upsertMarker(DitingMarkerEntity(sessionId = second, positionMillis = 2, type = "manual_question"))

        dao.deleteMarkers(first)
        dao.deleteSegments(first)
        dao.deleteSession(first)

        assertEquals(1, dao.observeSessions().first().size)
        assertEquals(second, dao.observeSessions().first().single().id)
        assertEquals(1, dao.observeMarkers(second).first().size)
    }

    @Test
    fun unavailableTranscriptionKeepsAudioOnlySegments() = runTest {
        val dao = database.ditingDao()
        val sessionId = dao.upsertSession(DitingSessionEntity(title = "识别不可用"))
        dao.upsertSegment(DitingSegmentEntity(sessionId = sessionId, sequence = 0, startMillis = 0, endMillis = 8_000, status = "waiting_for_transcription"))
        dao.upsertSegment(DitingSegmentEntity(sessionId = sessionId, sequence = 1, startMillis = 8_000, endMillis = 16_000, status = "transcribing"))
        dao.upsertSegment(DitingSegmentEntity(sessionId = sessionId, sequence = 2, startMillis = 16_000, endMillis = 24_000, status = "completed"))

        dao.markTranscriptionUnavailable(sessionId, "转写服务不可用，音频已保留")

        val statuses = dao.allSegments(sessionId).map { it.status }
        assertEquals(listOf("local_audio_only", "local_audio_only", "completed"), statuses)
    }

    @Test
    fun recoveringInterruptedSessionAlsoReleasesPendingSegments() = runTest {
        val dao = database.ditingDao()
        val staleTime = System.currentTimeMillis() - 10 * 60_000L
        val sessionId = dao.upsertSession(
            DitingSessionEntity(
                title = "中断课堂",
                status = "recording",
                updatedAtEpochMillis = staleTime,
            ),
        )
        dao.upsertSegment(DitingSegmentEntity(sessionId = sessionId, sequence = 0, startMillis = 0, endMillis = 8_000, status = "waiting_for_transcription"))
        dao.upsertSegment(DitingSegmentEntity(sessionId = sessionId, sequence = 1, startMillis = 8_000, endMillis = 16_000, status = "transcribing"))

        DitingRepository(dao, database, DitingAudioStore(ApplicationProvider.getApplicationContext())).recoverInterruptedSessions()

        assertEquals("failed", dao.sessionById(sessionId)?.status)
        assertEquals(listOf("local_audio_only", "local_audio_only"), dao.allSegments(sessionId).map { it.status })
    }

    @Test
    fun activeSessionGuardReportsOnlyLiveRecordingStates() = runTest {
        val dao = database.ditingDao()
        assertTrue(!dao.hasActiveSession())
        val sessionId = dao.upsertSession(DitingSessionEntity(title = "资源保护", status = "paused"))
        assertTrue(dao.hasActiveSession())
        dao.upsertSession(DitingSessionEntity(id = sessionId, title = "资源保护", status = "completed"))
        assertTrue(!dao.hasActiveSession())
    }

    @Test
    fun deletingActiveSessionIsIgnored() = runTest {
        val dao = database.ditingDao()
        val sessionId = dao.upsertSession(
            DitingSessionEntity(title = "正在录音", status = "recording"),
        )

        DitingRepository(dao, database, DitingAudioStore(ApplicationProvider.getApplicationContext())).deleteSession(sessionId)

        assertEquals("recording", dao.sessionById(sessionId)?.status)
    }
    @Test
    fun deletingProcessingSessionIsIgnored() = runTest {
        val dao = database.ditingDao()
        val sessionId = dao.upsertSession(
            DitingSessionEntity(title = "正在保存", status = "processing"),
        )

        DitingRepository(dao, database, DitingAudioStore(ApplicationProvider.getApplicationContext())).deleteSession(sessionId)

        assertEquals("processing", dao.sessionById(sessionId)?.status)
    }
}
