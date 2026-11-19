package win.iqwqi.xiangece.feature.diting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.feature.diting.audio.DitingSpeechBoundary

class DitingSpeechBoundaryTest {
    private fun pcm(milliseconds: Int, amplitude: Int): ByteArray =
        ByteArray(milliseconds * 32).also { data ->
            for (index in data.indices step 2) {
                data[index] = amplitude.toByte()
                data[index + 1] = (amplitude shr 8).toByte()
            }
        }

    private fun splits(data: ByteArray): List<Int> {
        val boundary = DitingSpeechBoundary()
        val lengths = mutableListOf<Int>()
        var offset = 0
        var chunk = 0
        while (offset < data.size) {
            val count = boundary.consume(data, offset, minOf(4_096, data.size - offset))
            assertTrue(count > 0 && count % 2 == 0)
            offset += count
            chunk += count
            if (boundary.shouldSplit) {
                lengths += chunk
                boundary.reset()
                chunk = 0
            }
        }
        if (chunk > 0) lengths += chunk
        assertEquals(data.size, lengths.sum())
        return lengths
    }

    @Test fun continuousSpeechHasHardBoundAndNoSampleLoss() {
        assertEquals(listOf(12_000 * 32, 12_000 * 32, 1_000 * 32), splits(pcm(25_000, 3_000)))
    }

    @Test fun selectsPauseAfterMinimumLength() {
        val result = splits(pcm(5_000, 3_000) + pcm(600, 0) + pcm(1_000, 3_000))
        assertEquals(5_480 * 32, result.first())
        assertEquals(2, result.size)
    }

    @Test fun silenceIsSavedInBoundedChunksInsteadOfDropped() {
        assertEquals(listOf(4_000 * 32, 4_000 * 32, 2_000 * 32), splits(pcm(10_000, 0)))
    }

    @Test fun briefQuietGapDoesNotSplitSentence() {
        assertEquals(listOf(8_200 * 32), splits(pcm(5_000, 3_000) + pcm(200, 0) + pcm(3_000, 3_000)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnalignedPcm() {
        DitingSpeechBoundary().consume(ByteArray(3), 0, 3)
    }
}
