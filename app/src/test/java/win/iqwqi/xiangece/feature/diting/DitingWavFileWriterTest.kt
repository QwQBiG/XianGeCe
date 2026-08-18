package win.iqwqi.xiangece.feature.diting

import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import win.iqwqi.xiangece.feature.diting.audio.DitingWavFileWriter

class DitingWavFileWriterTest {
    @Test
    fun closeWritesValidPcm16WavHeader() {
        val directory = Files.createTempDirectory("diting-wav-test").toFile()
        val file = directory.resolve("segment.wav")
        val pcm = ByteArray(3_200) { (it % 127).toByte() }

        DitingWavFileWriter(file).use { it.write(pcm) }

        val bytes = file.readBytes()
        assertEquals(3_244, bytes.size)
        assertArrayEquals("RIFF".toByteArray(Charsets.US_ASCII), bytes.copyOfRange(0, 4))
        assertArrayEquals("WAVE".toByteArray(Charsets.US_ASCII), bytes.copyOfRange(8, 12))
        assertArrayEquals("data".toByteArray(Charsets.US_ASCII), bytes.copyOfRange(36, 40))
        assertEquals(3_200, littleEndianInt(bytes, 40))
        assertEquals(3_200, bytes.copyOfRange(44, bytes.size).size)
        assertArrayEquals(pcm, bytes.copyOfRange(44, bytes.size))
        directory.deleteRecursively()
    }

    @Test
    fun repairsHeaderAfterProcessInterruption() {
        val directory = Files.createTempDirectory("diting-wav-repair-test").toFile()
        val file = directory.resolve("recording.wav")
        val pcm = ByteArray(3_200) { (it % 97).toByte() }
        DitingWavFileWriter(file).use { it.write(pcm) }
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(ByteArray(4))
            raf.seek(40)
            raf.write(ByteArray(4))
        }

        assertTrue(DitingWavFileWriter.repairInterruptedFile(file))
        val bytes = file.readBytes()
        assertEquals(3_200, littleEndianInt(bytes, 40))
        assertEquals(3_236, littleEndianInt(bytes, 4))
        assertArrayEquals(pcm, bytes.copyOfRange(44, bytes.size))
        directory.deleteRecursively()
    }
    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
