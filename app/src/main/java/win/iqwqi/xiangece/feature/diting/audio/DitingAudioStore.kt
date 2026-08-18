package win.iqwqi.xiangece.feature.diting.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DitingAudioFiles(
    val directory: File,
    val recording: File,
    val segmentsDirectory: File,
)

@Singleton
class DitingAudioStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File
        get() = File(context.filesDir, "diting")

    fun filesFor(sessionId: Long): DitingAudioFiles {
        require(sessionId > 0) { "课堂记录 ID 无效" }
        val directory = File(root, sessionId.toString()).apply { mkdirs() }
        val segmentsDirectory = File(directory, "segments").apply { mkdirs() }
        return DitingAudioFiles(directory, File(directory, "recording.wav"), segmentsDirectory)
    }

    fun segmentFile(sessionId: Long, sequence: Int): File {
        require(sessionId > 0 && sequence >= 0) { "课堂音频分段参数无效" }
        return File(filesFor(sessionId).segmentsDirectory, "%06d.wav".format(sequence))
    }
    /** Repairs WAV sizes left stale when Android kills the recording process. */
    suspend fun repairSessionWavFiles(sessionId: Long): Int = withContext(Dispatchers.IO) {
        require(sessionId > 0) { "课堂记录 ID 无效" }
        val directory = File(root, sessionId.toString()).canonicalFile
        val rootDirectory = root.canonicalFile
        require(directory.parentFile == rootDirectory) { "课堂录音路径无效" }
        if (!directory.isDirectory) return@withContext 0
        directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
            .count { DitingWavFileWriter.repairInterruptedFile(it) }
    }

    suspend fun deleteSessionFiles(sessionId: Long) = withContext(Dispatchers.IO) {
        val directory = File(root, sessionId.toString()).canonicalFile
        val rootDirectory = root.canonicalFile
        require(directory.parentFile == rootDirectory) { "课堂录音路径无效" }
        if (directory.isDirectory) directory.deleteRecursively()
    }

    suspend fun sizeOf(path: String): Long = withContext(Dispatchers.IO) {
        val file = runCatching { File(path).canonicalFile }.getOrNull()
        if (file?.isFile == true) file.length() else 0L
    }
}