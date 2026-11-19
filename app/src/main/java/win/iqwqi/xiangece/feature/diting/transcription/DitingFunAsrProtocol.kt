package win.iqwqi.xiangece.feature.diting.transcription

import java.io.IOException
import kotlinx.serialization.json.*

/** DashScope client/server events, verified against the official docs on 2026-09-11. */
internal object DitingFunAsrProtocol {
    fun runTask(id: String, model: String, rate: Int, language: String, glossary: String): String =
        buildJsonObject {
            put("header", header("run-task", id))
            putJsonObject("payload") {
                put("task_group", "audio")
                put("task", "asr")
                put("function", "recognition")
                put("model", model)
                putJsonObject("parameters") {
                    put("format", "pcm")
                    put("sample_rate", rate)
                    if (language == "zh" || language == "en") putJsonArray("language_hints") { add(language) }
                }
                putJsonObject("input") {
                    // Context is supported only by these Fun-ASR versions. It is not vocabulary_id.
                    if (model in setOf("fun-asr-realtime", "fun-asr-realtime-2025-11-07") && glossary.isNotBlank()) {
                        putJsonArray("context") {
                            addJsonObject {
                                put("role", "user")
                                putJsonArray("content") {
                                    addJsonObject {
                                        put("type", "input_text")
                                        put("text", glossary.trim().take(400))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()

    fun finishTask(id: String): String = buildJsonObject {
        put("header", header("finish-task", id))
        putJsonObject("payload") { putJsonObject("input") {} }
    }.toString()

    private fun header(action: String, id: String) = buildJsonObject {
        put("action", action)
        put("task_id", id)
        put("streaming", "duplex")
    }

    fun event(raw: String, taskId: String): JsonObject {
        val event = try { Json.parseToJsonElement(raw).jsonObject } catch (_: Exception) {
            throw IOException("Fun-ASR 返回了无效的事件数据")
        }
        val header = event["header"] as? JsonObject ?: throw IOException("Fun-ASR 事件缺少头部")
        if (header.string("task_id") != taskId) throw IOException("Fun-ASR 任务编号不匹配")
        if (header.string("event") == "task-failed") throw DitingCloudErrors.task(header.string("error_code"))
        return event
    }

    fun eventName(event: JsonObject): String = (event["header"] as? JsonObject)?.string("event").orEmpty()
}

/** Only committed sentences enter the transcript; repeated final events replace, not append. */
internal class DitingFunAsrResults {
    private data class Sentence(val begin: Long, val text: String)
    private val finals = linkedMapOf<String, Sentence>()
    private val pending = mutableSetOf<String>()

    fun accept(event: JsonObject) {
        val payload = event["payload"] as? JsonObject
        val output = payload?.get("output") as? JsonObject
        val sentence = output?.get("sentence") as? JsonObject ?: throw IOException("Fun-ASR 结果缺少句子")
        if ((sentence["heartbeat"] as? JsonPrimitive)?.booleanOrNull == true) return
        val begin = (sentence["begin_time"] as? JsonPrimitive)?.longOrNull
            ?: throw IOException("Fun-ASR 句子缺少时间位置")
        val sentenceId = (sentence["sentence_id"] as? JsonPrimitive)?.longOrNull
        val key = sentenceId?.let { "id:$it" } ?: "begin:$begin"
        val text = sentence.string("text").trim()
        val finalized = (sentence["sentence_end"] as? JsonPrimitive)?.booleanOrNull
            ?: throw IOException("Fun-ASR 句子缺少完成标志")
        if (!finalized) {
            if (text.isNotEmpty() && key !in finals) pending.add(key)
            return
        }
        pending.remove(key)
        // Do not erase a previously committed sentence on an empty duplicate event.
        if (text.isNotEmpty()) finals[key] = Sentence(begin, text)
        if (finals.size > 1_000 || finals.values.sumOf { it.text.length } > 100_000) {
            throw IOException("Fun-ASR 返回的文字超出当前分段限制")
        }
    }

    fun transcript(): DitingCloudTranscript {
        if (pending.isNotEmpty()) throw IOException("Fun-ASR 仍有未完成句子，请重新转写该片段")
        val text = finals.values.sortedBy { it.begin }.joinToString("\n") { it.text }.trim()
        if (text.isEmpty()) throw IOException("Fun-ASR 未返回有效语音文字")
        // The documented response has no detected-language field; do not label a hint as detection.
        return DitingCloudTranscript(text = text)
    }
}

private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
