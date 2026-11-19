package win.iqwqi.xiangece.feature.diting.transcription

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object DitingCloudEndpoint {
    const val FUN_ASR_DEFAULT = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"

    fun isFunAsr(model: String): Boolean = model.trim().startsWith("fun-asr-realtime")

    fun resolve(model: String, configured: String, baseUrl: String): String {
        if (!isFunAsr(model)) {
            val base = configured.trim().ifBlank { baseUrl.trim().trimEnd('/') + "/audio/transcriptions" }
            val endpoint = if (base.contains("/audio/transcriptions", ignoreCase = true)) base
            else base.trimEnd('/') + "/audio/transcriptions"
            val url = endpoint.toHttpUrlOrNull()
                ?: throw IOException("云端转写地址无效，请填写 HTTP 或 HTTPS 接口地址")
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) throw IOException("接口地址不能包含登录凭据")
            return url.toString()
        }
        val endpoint = configured.trim().ifBlank { FUN_ASR_DEFAULT }
        val https = when {
            endpoint.startsWith("wss://") -> "https://" + endpoint.removePrefix("wss://")
            endpoint.startsWith("https://") -> endpoint
            else -> throw IOException("Fun-ASR 需要安全的 wss:// 或 https:// WebSocket 地址")
        }
        val url = https.toHttpUrlOrNull() ?: throw IOException("Fun-ASR WebSocket 地址无效")
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) {
            throw IOException("Fun-ASR 地址不能携带凭据、查询参数或片段；密钥请填写在密钥设置中")
        }
        val official = url.host in setOf("dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com") ||
            url.host.endsWith(".cn-beijing.maas.aliyuncs.com") ||
            url.host.endsWith(".ap-southeast-1.maas.aliyuncs.com")
        val path = url.encodedPath.trimEnd('/')
        val normalized = if (official) {
            if (path !in setOf("", "/api/v1", "/compatible-mode/v1", "/api-ws/v1/inference")) {
                throw IOException("请填写 DashScope WebSocket 地址 /api-ws/v1/inference")
            }
            url.newBuilder().encodedPath("/api-ws/v1/inference").build()
        } else {
            if (path.isEmpty()) throw IOException("自定义 Fun-ASR 地址需要包含完整 WebSocket 路径")
            url
        }
        return "wss://" + normalized.toString().removePrefix("https://")
    }
}

/** Never surface response bodies, endpoint URLs or throwable messages: they may echo secrets/audio. */
internal object DitingCloudErrors {
    fun http(code: Int): IOException = IOException(when (code) {
        401 -> "云端转写密钥验证失败（401），请检查密钥和服务地域"
        403 -> "云端转写访问被拒绝（403），请检查模型权限与服务地域"
        404 -> "云端转写接口或模型不存在（404），请检查地址和模型名"
        429 -> "云端转写请求受限（429），请检查额度或稍后重试"
        in 500..599 -> "云端转写服务暂时不可用（$code）"
        else -> "云端转写请求失败（HTTP $code），请检查接口配置"
    })

    fun task(code: String): IOException = IOException(when (code.uppercase()) {
        "INVALID_API_KEY", "INVALIDAPIKEY", "AUTHENTICATION_FAILED", "UNAUTHORIZED" -> "Fun-ASR 密钥验证失败，请检查密钥和服务地域"
        "INVALID_PARAMETER", "INVALIDPARAMETER", "INVALIDPARAMETER.VALUEERROR", "CLIENT_ERROR" -> "Fun-ASR 拒绝任务，请检查模型、音频格式及参数"
        "MODEL_NOT_FOUND", "INVALIDMODEL", "MODELNOTFOUND" -> "Fun-ASR 模型不存在或当前地域不支持该模型"
        "QUOTA_EXCEEDED", "THROTTLING", "THROTTLING.RATEQUOTA", "LIMIT_REQUESTS" -> "Fun-ASR 额度或并发受限，请稍后重试"
        "INTERNAL_ERROR", "INTERNALERROR", "SERVER_ERROR" -> "Fun-ASR 服务内部错误，请稍后重试"
        else -> "Fun-ASR 任务失败，请检查服务权限、额度和模型配置"
    })
}
