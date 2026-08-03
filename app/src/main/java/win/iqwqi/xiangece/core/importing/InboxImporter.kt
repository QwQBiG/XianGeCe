package win.iqwqi.xiangece.core.importing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SharedInput {
    data class Text(val value: String) : SharedInput
    data class Image(val uri: Uri) : SharedInput
}

@Singleton
class InboxImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun fromIntent(intent: Intent?): SharedInput? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type.orEmpty()
        return when {
            type.startsWith("image/") -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                uri?.let(SharedInput::Image)
            }
            type == "text/plain" -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.takeIf(String::isNotBlank)
                ?.let(SharedInput::Text)
            else -> null
        }
    }

    suspend fun copyToPrivateStorage(uri: Uri): File = withContext(Dispatchers.IO) {
        val extension = displayName(uri)
            .substringAfterLast('.', "jpg")
            .lowercase()
            .filter(Char::isLetterOrDigit)
            .take(8)
            .ifBlank { "jpg" }
        val directory = File(context.filesDir, "inbox").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.$extension")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选图片" }
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_IMAGE_BYTES) { "图片超过 25 MB，无法导入" }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        target
    }

    suspend fun deletePrivateCopy(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        val inboxDirectory = File(context.filesDir, "inbox").canonicalFile
        val target = File(path).canonicalFile
        if (target.parentFile == inboxDirectory && target.isFile) {
            target.delete()
        }
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
            }
        return uri.lastPathSegment.orEmpty()
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
    }
}
