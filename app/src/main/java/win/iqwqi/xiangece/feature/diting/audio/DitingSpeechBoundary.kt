package win.iqwqi.xiangece.feature.diting.audio

import kotlin.math.sqrt

/** Energy-only boundary hint, never a speech filter: every input sample is kept. */
internal class DitingSpeechBoundary(
    private val sampleRate: Int = 16_000,
    private val minMillis: Int = 4_000,
    private val maxMillis: Int = 12_000,
    private val silenceMillis: Int = 480,
) {
    init {
        require(sampleRate >= 1_000 && minMillis > 0 && maxMillis >= minMillis && silenceMillis > 0)
    }
    private var samples = 0
    private var frameSamples = 0
    private var frameEnergy = 0.0
    private var quietSamples = 0
    private var noiseFloor = 100.0
    var shouldSplit: Boolean = false
        private set

    /** Returns bytes up to the first boundary, or all available bytes. PCM16 must be aligned. */
    fun consume(pcm: ByteArray, offset: Int, count: Int): Int {
        require(offset >= 0 && count > 0 && count % 2 == 0 && offset + count <= pcm.size)
        check(!shouldSplit) { "Reset the boundary after closing a chunk" }
        var consumed = 0
        while (consumed < count) {
            val index = offset + consumed
            val sample = ((pcm[index].toInt() and 255) or (pcm[index + 1].toInt() shl 8)).toShort().toDouble()
            samples++
            frameSamples++
            frameEnergy += sample * sample
            consumed += 2
            if (frameSamples >= sampleRate / 50) {
                val rms = sqrt(frameEnergy / frameSamples)
                val quiet = rms < (noiseFloor * 2.5).coerceIn(180.0, 600.0)
                quietSamples = if (quiet) quietSamples + frameSamples else 0
                if (quiet) noiseFloor = noiseFloor * 0.95 + rms * 0.05
                frameSamples = 0
                frameEnergy = 0.0
            }
            shouldSplit = samples >= sampleRate * maxMillis / 1_000 ||
                (samples >= sampleRate * minMillis / 1_000 && quietSamples >= sampleRate * silenceMillis / 1_000)
            if (shouldSplit) break
        }
        return consumed
    }

    fun reset() {
        samples = 0
        frameSamples = 0
        frameEnergy = 0.0
        quietSamples = 0
        shouldSplit = false
    }
}
