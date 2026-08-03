package win.iqwqi.xiangece.core.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import win.iqwqi.xiangece.core.security.ApiKeyCipher
import win.iqwqi.xiangece.data.settings.AppSettings
import win.iqwqi.xiangece.domain.model.ParsedDraft

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class VisionMessage(val role: String, val content: List<VisionPart>)

@Serializable
private data class VisionPart(
    val type: String,
    val text: String? = null,
    val image_url: VisionImageUrl? = null,
)

@Serializable
private data class VisionImageUrl(val url: String, val detail: String = "low")

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0,
)

@Serializable
private data class VisionChatRequest(
    val model: String,
    val messages: List<VisionMessage>,
    val temperature: Double = 0.0,
    val max_tokens: Int = 1800,
)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
private data class ChatChoice(val message: ChatMessage)

@Singleton
class AiCampusEnhancer @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val cipher: ApiKeyCipher,
) {
    suspend fun testConnection(settings: AppSettings): Result<Unit> =
        request(settings, "只回复一个 JSON 对象：{\"ok\":true}").map { Unit }

    suspend fun enhance(
        text: String,
        semesterContext: String,
        settings: AppSettings,
    ): Result<ParsedDraft> = request(
        settings,
        """
        你是大学校园信息结构化助手。只分析用户提供的文字，不补造事实。
        学期上下文：$semesterContext
        返回严格 JSON，字段必须与下列结构一致：
        {
          "type":"TASK|EVENT|NOTE|COURSE_MEETING",
          "title":"短标题",
          "description":"原意摘要",
          "dateTimeEpochMillis":null,
          "courseName":null,
          "location":null,
          "teachingWeek":null,
          "dayOfWeek":null,
          "startPeriod":null,
          "endPeriod":null,
          "weekParity":"ALL|ODD|EVEN",
          "confidence":0.0,
          "ambiguities":[]
        }
        无法确定的字段必须为 null，并把原因写入 ambiguities。待分析文字：
        $text
        """.trimIndent(),
    ).mapCatching { content ->
        val cleaned = content
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        json.decodeFromString<ParsedDraft>(cleaned)
    }

    suspend fun recognizeTimetableImage(
        file: File,
        settings: AppSettings,
        maxPeriods: Int,
        maxWeeks: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.aiEnabled) { "请先启用 AI" }
            require(settings.aiSupportsVision) { "当前服务商未开启多模态截图识别" }
            require(settings.aiVisionModel.isNotBlank()) { "请填写视觉模型" }
            val apiKey = cipher.decrypt(settings.encryptedApiKey)
            require(apiKey.isNotBlank()) { "API 密钥为空或无法解密" }
            val endpoint = settings.aiBaseUrl.trimEnd('/') + "/chat/completions"
            val body = json.encodeToString(
                VisionChatRequest(
                    model = settings.aiVisionModel,
                    messages = listOf(
                        VisionMessage(
                            role = "system",
                            content = listOf(VisionPart(type = "text", text = "你只返回有效 JSON，不使用 Markdown。")),
                        ),
                        VisionMessage(
                            role = "user",
                            content = listOf(
                                VisionPart(
                                    type = "text",
                                    text = """
                                    识别这张大学课表截图。为节省 token，只输出 JSON 数组，不解释。
                                    字段：name, teacher, location, dayOfWeek(1-7), startPeriod, endPeriod, startWeek, endWeek, weekParity(ALL/ODD/EVEN), note。
                                    规则：课程名必须保留；地点优先输出楼+教室；不确定的字段用空字符串或 null；节次范围 1-$maxPeriods；周次范围 1-$maxWeeks。
                                    """.trimIndent(),
                                ),
                                VisionPart(
                                    type = "image_url",
                                    image_url = VisionImageUrl("data:image/jpeg;base64,${compressedBase64(file)}", detail = "low"),
                                ),
                            ),
                        ),
                    ),
                ),
            ).toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .applyAuth(settings.aiAuthHeader, apiKey)
                .header("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (!response.isSuccessful) {
                    throw IOException("接口返回 ${response.code}：${responseBody.take(160)}")
                }
                json.decodeFromString<ChatResponse>(responseBody)
                    .choices.firstOrNull()?.message?.content
                    ?: throw IOException("接口没有返回可用内容")
            }
        }
    }

    private suspend fun request(settings: AppSettings, userPrompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(settings.aiBaseUrl.startsWith("https://") || settings.aiBaseUrl.startsWith("http://")) {
                    "接口地址必须以 http:// 或 https:// 开头"
                }
                require(settings.aiModel.isNotBlank()) { "请填写模型名称" }
                val apiKey = cipher.decrypt(settings.encryptedApiKey)
                require(apiKey.isNotBlank()) { "API 密钥为空或无法解密" }
                val endpoint = settings.aiBaseUrl.trimEnd('/') + "/chat/completions"
                val body = json.encodeToString(
                    ChatRequest(
                        model = settings.aiModel,
                        messages = listOf(
                            ChatMessage("system", "你只返回有效 JSON，不使用 Markdown。"),
                            ChatMessage("user", userPrompt),
                        ),
                    ),
                ).toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(endpoint)
                    .applyAuth(settings.aiAuthHeader, apiKey)
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        throw IOException("接口返回 ${response.code}：${responseBody.take(160)}")
                    }
                    json.decodeFromString<ChatResponse>(responseBody)
                        .choices.firstOrNull()?.message?.content
                        ?: throw IOException("接口没有返回可用内容")
                }
            }
        }

    private fun Request.Builder.applyAuth(template: String, apiKey: String): Request.Builder {
        val value = template.ifBlank { "Authorization: Bearer {key}" }.replace("{key}", apiKey)
        val separator = value.indexOf(':')
        return if (separator > 0) {
            header(value.substring(0, separator).trim(), value.substring(separator + 1).trim())
        } else {
            header("Authorization", "Bearer $apiKey")
        }
    }

    private fun compressedBase64(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        ) ?: error("图片解码失败")
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
