package win.iqwqi.xiangece.feature.diting.offline

import java.io.File
import java.io.RandomAccessFile

internal data class DitingPcmAudio(val samples: FloatArray, val sampleRate: Int) {
    val durationMillis: Long get() = samples.size * 1000L / sampleRate
}

/** Read actual RIFF chunks; never interpret a metadata chunk as audio. */
internal object DitingPcmAudioReader {
    fun read(file: File, checkCancelled: () -> Unit = {}): DitingPcmAudio =
        RandomAccessFile(file, "r").use { input ->
            check(file.length() >= 44) { "WAV 文件头不完整" }
            check(input.tag() == "RIFF") { "仅支持 PCM WAV 音频" }
            val riffEnd = input.uint32() + 8L
            check(riffEnd in 44L..input.length()) { "WAV 文件不完整" }
            check(input.tag() == "WAVE") { "仅支持 PCM WAV 音频" }
            var sampleRate = 0
            var pcm = false
            var dataStart = -1L
            var dataBytes = 0L
            while (input.filePointer + 8 <= riffEnd) {
                checkCancelled()
                val tag = input.tag()
                val length = input.uint32()
                val start = input.filePointer
                check(length <= riffEnd - start) { "WAV 数据长度不正确" }
                when (tag) {
                    "fmt " -> {
                        check(length >= 16) { "WAV 格式信息不完整" }
                        val format = input.uint16()
                        val channels = input.uint16()
                        sampleRate = input.uint32().toInt()
                        val byteRate = input.uint32()
                        val alignment = input.uint16()
                        val bits = input.uint16()
                        pcm = format == 1 && channels == 1 && bits == 16 &&
                            alignment == 2 && sampleRate == 16_000 && byteRate == 32_000L
                    }
                    "data" -> {
                        check(dataStart == -1L) { "不支持多数据段 WAV" }
                        dataStart = start
                        dataBytes = length
                    }
                }
                input.seek(start + length + (length and 1L))
            }
            check(pcm && dataStart >= 0) { "需要 16 kHz、单声道、16 位 PCM WAV" }
            // The service supplies short segments. Bound native memory/context use even on bad input.
            check(dataBytes in 2L..(16_000L * 2 * 30) && dataBytes % 2 == 0L) {
                "请使用不超过 30 秒的完整音频分段"
            }
            input.seek(dataStart)
            val samples = FloatArray((dataBytes / 2).toInt())
            val buffer = ByteArray(16_384)
            var offset = 0
            while (offset < samples.size) {
                checkCancelled()
                val count = minOf(buffer.size, (samples.size - offset) * 2)
                input.readFully(buffer, 0, count)
                for (index in 0 until count step 2) {
                    val pcmSample = (buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xff)
                    samples[offset++] = pcmSample / 32768f
                }
            }
            DitingPcmAudio(samples, sampleRate)
        }

    private fun RandomAccessFile.tag(): String = ByteArray(4).also(::readFully).toString(Charsets.US_ASCII)
    private fun RandomAccessFile.uint16(): Int = readUnsignedByte() or (readUnsignedByte() shl 8)
    private fun RandomAccessFile.uint32(): Long = Integer.toUnsignedLong(Integer.reverseBytes(readInt()))
}
