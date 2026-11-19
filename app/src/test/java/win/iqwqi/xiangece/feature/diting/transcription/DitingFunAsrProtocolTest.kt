package win.iqwqi.xiangece.feature.diting.transcription

import java.io.IOException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DitingFunAsrProtocolTest {
    @Test fun endpointSelectionKeepsCompatibleRoutesAndNormalizesOnlyOfficialRoots() {
        assertEquals("https://custom.example/v1/audio/transcriptions", DitingCloudEndpoint.resolve("whisper-1", "", "https://custom.example/v1"))
        assertEquals("https://custom.example/audio/transcriptions?api-version=x", DitingCloudEndpoint.resolve("other", "https://custom.example/audio/transcriptions?api-version=x", ""))
        assertEquals(DitingCloudEndpoint.FUN_ASR_DEFAULT, DitingCloudEndpoint.resolve("fun-asr-realtime", "", "https://unrelated.example/v1"))
        assertEquals("wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference", DitingCloudEndpoint.resolve("fun-asr-realtime", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", ""))
        assertEquals("wss://workspace.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference", DitingCloudEndpoint.resolve("fun-asr-realtime", "https://workspace.cn-beijing.maas.aliyuncs.com", ""))
        assertEquals("wss://proxy.example/speech", DitingCloudEndpoint.resolve("fun-asr-realtime", "https://proxy.example/speech", ""))
        assertThrows(IOException::class.java) { DitingCloudEndpoint.resolve("fun-asr-realtime", "ws://proxy.example/speech", "") }
        assertThrows(IOException::class.java) { DitingCloudEndpoint.resolve("fun-asr-realtime", "wss://proxy.example/speech?key=secret", "") }
    }

    @Test fun glossaryIsContextOnlyForSupportedModelsAndNeverFakeVocabulary() {
        val supported = Json.parseToJsonElement(DitingFunAsrProtocol.runTask("task", "fun-asr-realtime", 16000, "zh", "术".repeat(500))).jsonObject
        val payload = supported.getValue("payload").jsonObject
        val parameters = payload.getValue("parameters").jsonObject
        assertEquals("pcm", parameters.getValue("format").jsonPrimitive.content)
        assertEquals("zh", parameters.getValue("language_hints").jsonArray.single().jsonPrimitive.content)
        assertFalse(parameters.containsKey("vocabulary_id"))
        assertFalse(parameters.containsKey("vocabulary"))
        assertFalse(parameters.containsKey("prompt"))
        val context = payload.getValue("input").jsonObject.getValue("context").jsonArray.single().jsonObject
        assertEquals(400, context.getValue("content").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content.length)
        val unsupported = Json.parseToJsonElement(DitingFunAsrProtocol.runTask("task", "fun-asr-realtime-2026-02-28", 16000, "auto", "术语")).jsonObject
        assertTrue(unsupported.getValue("payload").jsonObject.getValue("input").jsonObject.isEmpty())
        assertFalse(unsupported.getValue("payload").jsonObject.getValue("parameters").jsonObject.containsKey("language_hints"))
    }

    @Test fun finalResultsReplacePartialsAndDeduplicateWithoutDroppingRepeatedSentences() {
        val results = DitingFunAsrResults()
        results.accept(result(1, 0, "错字", false))
        results.accept(result(1, 0, "正确。", true))
        results.accept(result(1, 0, "正确。", true))
        results.accept(result(2, 1000, "正确。", true))
        assertEquals("正确。\n正确。", results.transcript().text)
        assertEquals("", results.transcript().language)
    }

    @Test fun unfinalizedTextIsNeverReturnedAsSuccessfulTranscript() {
        val results = DitingFunAsrResults()
        results.accept(result(1, 0, "未定稿", false))
        assertThrows(IOException::class.java) { results.transcript() }
        assertThrows(IOException::class.java) { DitingFunAsrProtocol.event("not json", "task") }
        assertThrows(IOException::class.java) { DitingFunAsrProtocol.event("""{"header":{"task_id":"other","event":"task-started"}}""", "task") }
    }

    @Test fun serverFailureDoesNotEchoSecretErrorMessagesOrUntrustedCodes() {
        val failure = assertThrows(IOException::class.java) {
            DitingFunAsrProtocol.event("""{"header":{"task_id":"task","event":"task-failed","error_code":"secret-key","error_message":"secret-key and transcript"}}""", "task")
        }
        assertFalse(failure.toString().contains("secret-key"))
        assertNull(failure.cause)
    }

    private fun result(id: Int, begin: Long, text: String, finalized: Boolean): JsonObject = buildJsonObject {
        putJsonObject("payload") { putJsonObject("output") { putJsonObject("sentence") {
            put("sentence_id", id)
            put("begin_time", begin)
            put("text", text)
            put("sentence_end", finalized)
        } } }
    }
}
