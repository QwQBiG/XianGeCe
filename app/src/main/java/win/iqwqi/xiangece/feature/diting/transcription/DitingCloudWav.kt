package win.iqwqi.xiangece.feature.diting.transcription

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** A validated, already-finalized segment. Only this data range is sent to DashScope. */
internal data class DitingCloudWav(val sampleRate: Int, val offset: Long, val bytes: Long) {
    val durationMillis: Long get() = bytes * 1_000 / (sampleRate * 2L)

    companion object {
        const val MAX_DURATION_MILLIS = 60_000L

        fun inspect(file: File): DitingCloudWav {
            if (!file.isFile || file.length() !in 45..16_000_000L) {
                throw IOException("Fun-ASR 需要有效的录音片段（最大 16 MB）")
            }
            RandomAccessFile(file, "r").use { input ->
                if (input.fourCC() != "RIFF") throw IOException("Fun-ASR 片段必须是 WAV 音频")
                val riffEnd = input.u32() + 8
                if (input.fourCC() != "WAVE" || riffEnd > input.length() || riffEnd < 44) {
                    throw IOException("WAV 片段尚未完成或头部损坏")
                }
                var rate: Int? = null
                var dataOffset: Long? = null
                var dataBytes: Long? = null
                while (input.filePointer + 8 <= riffEnd) {
                    val type = input.fourCC()
                    val size = input.u32()
                    val start = input.filePointer
                    if (start + size > riffEnd) throw IOException("WAV 片段数据不完整")
                    when (type) {
                        "fmt " -> {
                            if (size < 16) throw IOException("WAV 格式信息不完整")
                            val pcm = input.u16()
                            val channels = input.u16()
                            val sampleRate = input.u32()
                            val byteRate = input.u32()
                            val alignment = input.u16()
                            val bits = input.u16()
                            if (pcm != 1 || channels != 1 || bits != 16 || alignment != 2 ||
                                sampleRate !in 8_000L..48_000L || byteRate != sampleRate * 2
                            ) throw IOException("Fun-ASR 当前支持 8–48 kHz 单声道 PCM16 WAV 片段")
                            rate = sampleRate.toInt()
                        }
                        "data" -> {
                            if (dataOffset != null) throw IOException("WAV 片段包含多个音频数据区")
                            dataOffset = start
                            dataBytes = size
                        }
                    }
                    input.seek(start + size + size % 2)
                }
                val result = DitingCloudWav(
                    rate ?: throw IOException("WAV 缺少格式信息"),
                    dataOffset ?: throw IOException("WAV 缺少音频数据"),
                    dataBytes ?: throw IOException("WAV 缺少音频长度"),
                )
                if (result.bytes <= 0 || result.bytes % 2 != 0L) throw IOException("WAV 片段为空或采样数据损坏")
                if (result.bytes > result.sampleRate * 2L * MAX_DURATION_MILLIS / 1_000) {
                    throw IOException("Fun-ASR 单次仅接收不超过 60 秒的录音片段，请先分段")
                }
                return result
            }
        }

        private fun RandomAccessFile.fourCC(): String = ByteArray(4).also { readFully(it) }.toString(Charsets.US_ASCII)
        private fun RandomAccessFile.u16(): Int = java.lang.Short.reverseBytes(readShort()).toInt() and 0xffff
        private fun RandomAccessFile.u32(): Long = Integer.reverseBytes(readInt()).toLong() and 0xffffffffL
    }
}
