package win.iqwqi.xiangece.feature.diting.transcription

import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import win.iqwqi.xiangece.core.security.ApiKeyCipher
import win.iqwqi.xiangece.data.settings.AppSettings
import win.iqwqi.xiangece.data.settings.AppSettingsStore

data class DitingCloudTranscript(
    val text: String,
    val language: String = "",
    val confidence: Float? = null,
)

/** Optional, explicit cloud fallback for devices without a usable local recognizer. */
@Singleton
class DitingCloudTranscriber @Inject constructor(
    private val settingsStore: AppSettingsStore,
    private val cipher: ApiKeyCipher,
    private val client: OkHttpClient,
) {
    suspend fun isConfigured(): Boolean = withContext(Dispatchers.IO) {
        runCatching { configuration(settingsStore.settings.first()) != null }.getOrDefault(false)
    }

    suspend fun transcribe(
        file: File,
        languageMode: String,
        glossary: String,
    ): Result<DitingCloudTranscript> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.isFile && file.length() > 44) { "音频分段为空" }
            val settings = settingsStore.settings.first()
            val config = configuration(settings) ?: throw IllegalStateException("未配置云端转写")
            val fileBody = file.asRequestBody("audio/wav".toMediaType())
            val form = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", settings.ditingTranscriptionModel.ifBlank { "whisper-1" })
                .addFormDataPart("response_format", "json")
                .apply {
                    when (languageMode) {
                        "zh" -> addFormDataPart("language", "zh")
                        "en" -> addFormDataPart("language", "en")
                    }
                    if (glossary.isNotBlank()) {
                        addFormDataPart("prompt", glossary.trim().take(1_000))
                    }
                }
                .addFormDataPart("file", file.name, fileBody)
                .build()
            val request = Request.Builder()
                .url(config.first)
                .applyAuth(settings.aiAuthHeader, config.second)
                .header("Accept", "application/json")
                .post(form)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw IOException("转写接口返回 ${response.code}：${body.take(180)}")
                }
                val json = JSONObject(body)
                val text = json.optString("text").trim()
                require(text.isNotBlank()) { "转写接口没有返回文字" }
                DitingCloudTranscript(
                    text = text,
                    language = json.optString("language").trim(),
                )
            }
        }
    }

    private fun configuration(settings: AppSettings): Pair<String, String>? {
        if (!settings.aiEnabled) return null
        val key = cipher.decrypt(settings.encryptedApiKey).trim()
        if (key.isBlank()) return null
        val configuredEndpoint = settings.ditingTranscriptionEndpoint.trim()
        val base = configuredEndpoint.ifBlank { settings.aiBaseUrl.trimEnd('/') + "/audio/transcriptions" }
        val endpoint = if (base.contains("/audio/transcriptions", ignoreCase = true)) base else base.trimEnd('/') + "/audio/transcriptions"
        if (!endpoint.startsWith("https://") && !endpoint.startsWith("http://")) return null
        return endpoint to key
    }

    private fun Request.Builder.applyAuth(template: String, key: String): Request.Builder {
        val value = template.ifBlank { "Authorization: Bearer {key}" }.replace("{key}", key)
        val separator = value.indexOf(':')
        if (separator > 0) {
            header(value.substring(0, separator).trim(), value.substring(separator + 1).trim())
        } else {
            header("Authorization", value)
        }
        return this
    }
}