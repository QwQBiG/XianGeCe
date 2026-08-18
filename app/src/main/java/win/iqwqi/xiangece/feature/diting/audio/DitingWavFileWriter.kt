package win.iqwqi.xiangece.feature.diting.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes a PCM16 mono WAV file while recording, then fixes the RIFF sizes on close. */
class DitingWavFileWriter(
    private val file: File,
    private val sampleRate: Int = 16_000,
    private val channels: Short = 1,
    private val bitsPerSample: Short = 16,
) : Closeable {
    private val output: RandomAccessFile
    private var dataBytes = 0L
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        output = RandomAccessFile(file, "rw")
        output.setLength(0)
        writeHeader(0)
    }

    @Synchronized
    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        check(!closed) { "WAV writer is closed" }
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "PCM 数据范围无效" }
        output.write(bytes, offset, length)
        dataBytes += length
    }

    override fun close() {
        synchronized(this) {
            if (closed) return
            writeHeader(dataBytes)
            output.close()
            closed = true
        }
    }

    private fun writeHeader(dataLength: Long) {
        output.seek(0)
        output.write(buildHeader(dataLength, sampleRate, channels, bitsPerSample))
        output.seek(output.length())
    }

    companion object {
        /** Repairs a valid PCM WAV whose process died before close() fixed RIFF sizes. */
        fun repairInterruptedFile(
            file: File,
            sampleRate: Int = 16_000,
            channels: Short = 1,
            bitsPerSample: Short = 16,
        ): Boolean = runCatching {
            if (!file.isFile || file.length() < 44L) return@runCatching false
            RandomAccessFile(file, "rw").use { input ->
                val header = ByteArray(44)
                input.seek(0)
                input.readFully(header)
                val riff = String(header, 0, 4, Charsets.US_ASCII)
                val wave = String(header, 8, 4, Charsets.US_ASCII)
                val fmt = String(header, 12, 4, Charsets.US_ASCII)
                val data = String(header, 36, 4, Charsets.US_ASCII)
                if (riff != "RIFF" || wave != "WAVE" || fmt != "fmt " || data != "data") {
                    return@runCatching false
                }
                val dataBytes = ((input.length() - 44L) / 2L * 2L).coerceAtMost(Int.MAX_VALUE.toLong())
                input.setLength(44L + dataBytes)
                input.seek(4)
                input.writeIntLittleEndian((36L + dataBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                input.seek(40)
                input.writeIntLittleEndian(dataBytes.toInt())
                input.fd.sync()
                true
            }
        }.getOrDefault(false)

        private fun buildHeader(
            dataLength: Long,
            sampleRate: Int,
            channels: Short,
            bitsPerSample: Short,
        ): ByteArray {
            val blockAlign = channels * bitsPerSample / 8
            val byteRate = sampleRate * blockAlign
            return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((36 + dataLength).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(channels)
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign.toShort())
                putShort(bitsPerSample)
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }.array()
        }

        private fun RandomAccessFile.writeIntLittleEndian(value: Int) {
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        }
    }
}
