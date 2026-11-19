package win.iqwqi.xiangece.feature.diting.transcription

import java.io.File
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

/** Retains the existing OpenAI-compatible multipart contract, including custom auth and prompt. */
internal class DitingCompatibleAsrAdapter(private val calls: Call.Factory) {
    suspend fun transcribe(
        file: File,
        endpoint: String,
        key: String,
        authTemplate: String,
        model: String,
        language: String,
        glossary: String,
    ): DitingCloudTranscript {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .apply {
                if (language == "zh" || language == "en") addFormDataPart("language", language)
                if (glossary.isNotBlank()) addFormDataPart("prompt", glossary.trim().take(1_000))
            }
            .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaType()))
            .build()
        val request = Request.Builder().url(endpoint)
            .apply {
                val auth = authTemplate.ifBlank { "Authorization: Bearer {key}" }.replace("{key}", key)
                val separator = auth.indexOf(':')
                if (separator > 0) header(auth.substring(0, separator).trim(), auth.substring(separator + 1).trim())
                else header("Authorization", auth)
            }
            .header("Accept", "application/json").post(form).build()
        val body = withTimeoutOrNull(45_000) { responseBody(request) }
            ?: throw IOException("云端转写超时，请重试该片段")
        val json = try { Json.parseToJsonElement(body).jsonObject } catch (_: Exception) {
            throw IOException("转写接口返回的内容不是有效的 JSON")
        }
        val text = (json["text"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        if (text.isBlank()) throw IOException("转写接口没有返回文字")
        return DitingCloudTranscript(text, (json["language"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim())
    }

    private suspend fun responseBody(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = calls.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("云端转写连接失败或超时，请检查网络和接口地址"))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        if (!it.isSuccessful) Result.failure(DitingCloudErrors.http(it.code))
                        else {
                            val source = it.body.source()
                            source.request(1_000_001)
                            if (source.buffer.size > 1_000_000) Result.failure(IOException("转写接口返回内容过大"))
                            else Result.success(it.body.string())
                        }
                    }
                } catch (_: Exception) {
                    Result.failure(IOException("云端转写响应读取失败"))
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
}
