package win.iqwqi.xiangece.feature.diting.transcription

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import win.iqwqi.xiangece.core.security.ApiKeyCipher
import win.iqwqi.xiangece.data.settings.AppSettings
import win.iqwqi.xiangece.data.settings.AppSettingsStore

data class DitingCloudTranscript(
    val text: String,
    val language: String = "",
    val confidence: Float? = null,
)

/** Cloud engine selection only; recording-session upload consent remains the caller's responsibility. */
@Singleton
class DitingCloudTranscriber @Inject constructor(
    private val settingsStore: AppSettingsStore,
    private val cipher: ApiKeyCipher,
    private val client: OkHttpClient,
) {
    private val transcriptionClient by lazy {
        client.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .apply {
                // Audio and authorization must never pass through a shared logging interceptor.
                interceptors().clear()
                networkInterceptors().clear()
            }
            .build()
    }

    suspend fun isConfigured(): Boolean = withContext(Dispatchers.IO) {
        try { configuration(settingsStore.settings.first()) != null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    suspend fun transcribe(
        file: File,
        languageMode: String,
        glossary: String,
    ): Result<DitingCloudTranscript> = withContext(Dispatchers.IO) {
        try {
            if (!file.isFile || file.length() <= 44) throw IOException("音频分段为空")
            val settings = settingsStore.settings.first()
            val config = configuration(settings) ?: throw IOException("未启用或未配置云端转写")
            val model = settings.ditingTranscriptionModel.trim().ifBlank { "whisper-1" }
            val transcript = if (DitingCloudEndpoint.isFunAsr(model)) {
                DitingFunAsrAdapter(transcriptionClient).transcribe(
                    file, config.first, config.second, model, languageMode, glossary,
                )
            } else {
                DitingCompatibleAsrAdapter(transcriptionClient).transcribe(
                    file, config.first, config.second, settings.aiAuthHeader, model, languageMode, glossary,
                )
            }
            Result.success(transcript)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            Result.failure(failure)
        } catch (_: Exception) {
            // Configuration/file/request exceptions can contain URLs or header values.
            Result.failure(IOException("云端转写无法完成，请检查音频、服务地址和密钥"))
        }
    }

    private fun configuration(settings: AppSettings): Pair<String, String>? {
        if (!settings.aiEnabled) return null
        val key = cipher.decrypt(settings.encryptedApiKey).trim()
        if (key.isBlank()) return null
        val endpoint = DitingCloudEndpoint.resolve(
            settings.ditingTranscriptionModel,
            settings.ditingTranscriptionEndpoint,
            settings.aiBaseUrl,
        )
        return endpoint to key
    }
}
