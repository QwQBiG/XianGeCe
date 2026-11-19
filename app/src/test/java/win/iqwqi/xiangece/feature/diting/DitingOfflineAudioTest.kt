package win.iqwqi.xiangece.feature.diting

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflineModelOptions
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackCatalog
import win.iqwqi.xiangece.feature.diting.offline.DitingPcmAudioReader
import win.iqwqi.xiangece.feature.diting.offline.safeTimestamps

class DitingOfflineAudioTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun pcmReaderPreservesDurationAndSkipsMetadata() {
        val audio = DitingPcmAudioReader.read(wav())
        assertEquals(16_000, audio.sampleRate)
        assertEquals(20L, audio.durationMillis)
        assertEquals(320, audio.samples.size)
        assertEquals(-1f, audio.samples[0], 0f)
        assertEquals(32767f / 32768f, audio.samples[1], 0f)
    }

    @Test
    fun rejectsTruncatedWrongFormatAndOversizeSegments() {
        val truncated = wav().also { it.writeBytes(it.readBytes().dropLast(2).toByteArray()) }
        assertThrows(IllegalStateException::class.java) { DitingPcmAudioReader.read(truncated) }
        assertThrows(IllegalStateException::class.java) { DitingPcmAudioReader.read(wav(rate = 8000)) }
        assertThrows(IllegalStateException::class.java) { DitingPcmAudioReader.read(wav(count = 480_001)) }
    }

    @Test
    fun cancellationPropagatesFromAudioRead() {
        assertThrows(CancellationException::class.java) {
            DitingPcmAudioReader.read(wav()) { throw CancellationException("cancel") }
        }
    }

    @Test
    fun glossaryIsBoundedAndUsedOnlyByNanoPromptConfiguration() {
        val glossary = "热力学，传热学\n热力学, C++, H2O"
        assertEquals("", DitingOfflineModelOptions.glossaryFor(DitingOfflinePackCatalog.SENSE_VOICE_ID, glossary))
        assertEquals("", DitingOfflineModelOptions.glossaryFor(DitingOfflinePackCatalog.CHINESE_PARAFORMER_ID, glossary))
        assertEquals("热力学,传热学,C++,H2O",
            DitingOfflineModelOptions.glossaryFor(DitingOfflinePackCatalog.FUN_ASR_NANO_ID, glossary))
        val long = (1..100).joinToString(",") { "词语$it" }
        val result = DitingOfflineModelOptions.glossaryFor(DitingOfflinePackCatalog.FUN_ASR_NANO_ID, long)
        assertTrue(result.length <= 240)
        assertTrue(result.split(',').size <= 24)
    }

    @Test
    fun timingRejectsMismatchedOrImpossibleValues() {
        assertEquals(listOf(0L, 500L), safeTimestamps(2, floatArrayOf(0f, 0.5f), 1000))
        assertTrue(safeTimestamps(2, floatArrayOf(0f), 1000).isEmpty())
        assertTrue(safeTimestamps(2, floatArrayOf(1f, 0f), 1000).isEmpty())
        assertTrue(safeTimestamps(1, floatArrayOf(Float.NaN), 1000).isEmpty())
        assertTrue(safeTimestamps(1, floatArrayOf(2f), 1000).isEmpty())
    }

    private fun wav(rate: Int = 16_000, count: Int = 320): File {
        val bytes = ByteBuffer.allocate(56 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(bytes.capacity() - 8)
        bytes.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        bytes.putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bytes.put("JUNK".toByteArray(Charsets.US_ASCII)).putInt(3).put(byteArrayOf(1, 2, 3, 0))
        bytes.put("data".toByteArray(Charsets.US_ASCII)).putInt(count * 2)
        repeat(count) { bytes.putShort(if (it % 2 == 0) Short.MIN_VALUE else Short.MAX_VALUE) }
        return temporary.newFile().also { it.writeBytes(bytes.array()) }
    }
}
