package win.iqwqi.xiangece.core.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import win.iqwqi.xiangece.BuildConfig

@Serializable
data class AppUpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val mandatory: Boolean = true,
    val minSupportedVersionCode: Int = 0,
    val apkUrl: String = "",
    val sha256: String = "",
    val releaseNotes: String = "",
    val downloadPageUrl: String = "https://github.com/QwQBiG/XianGeCe/releases/latest",
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Available(val manifest: AppUpdateManifest) : AppUpdateState
    data class PreparingBackup(val manifest: AppUpdateManifest) : AppUpdateState
    data class Downloading(val manifest: AppUpdateManifest, val progress: Int?) : AppUpdateState
    data class Failed(val manifest: AppUpdateManifest, val message: String, val duringBackup: Boolean = false) : AppUpdateState
}

@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun check(): AppUpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(UPDATE_MANIFEST_URL)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                require(response.isSuccessful) { "更新检查暂时不可用" }
                val body = response.body?.string().orEmpty()
                require(body.length <= MAX_MANIFEST_BYTES) { "更新信息无效" }
                val manifest = json.decodeFromString<AppUpdateManifest>(body)
                require(manifest.versionCode > 0 && manifest.apkUrl.isNotBlank()) { "更新信息不完整" }
                require(manifest.apkUrl.startsWith("https://")) { "更新地址不安全" }
                val currentCode = BuildConfig.VERSION_CODE
                val mustUpdate = currentCode < manifest.minSupportedVersionCode ||
                    (manifest.mandatory && currentCode < manifest.versionCode)
                if (manifest.versionCode > currentCode && mustUpdate) {
                    AppUpdateState.Available(if (currentCode < manifest.minSupportedVersionCode) manifest.copy(mandatory = true) else manifest)
                } else {
                    AppUpdateState.Idle
                }
            }
        }.getOrElse {
            // 更新服务属于增强能力：临时断网、超时或服务器维护不能把用户锁在启动页。
            AppUpdateState.Idle
        }
    }

    suspend fun download(
        manifest: AppUpdateManifest,
        onProgress: (Int?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "xiangece-${manifest.versionCode}.apk")
        if (target.isFile && target.length() > 0L && matchesSha256(target, manifest.sha256)) {
            onProgress(100)
            return@withContext target
        }
        val temporary = File(directory, "${target.name}.part")
        temporary.delete()
        val request = Request.Builder().url(manifest.apkUrl).build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "更新下载失败（${response.code}）" }
            val body = response.body ?: error("更新文件为空")
            val total = body.contentLength()
            var copied = 0L
            body.byteStream().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(if (total > 0L) ((copied * 100L) / total).toInt().coerceIn(0, 100) else null)
                    }
                }
            }
            require(copied > 0L) { "更新文件为空" }
        }
        require(matchesSha256(temporary, manifest.sha256)) { "更新文件校验失败，请重新下载" }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        onProgress(100)
        target
    }

    private fun matchesSha256(file: File, expected: String): Boolean {
        if (expected.isBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(expected.trim(), ignoreCase = true)
    }

    companion object {
        const val UPDATE_PAGE_URL = "https://github.com/QwQBiG/XianGeCe/releases/latest"
        const val UPDATE_MANIFEST_URL = "https://github.com/QwQBiG/XianGeCe/releases/latest/download/update.json"
        private const val MAX_MANIFEST_BYTES = 256 * 1024
    }
}
