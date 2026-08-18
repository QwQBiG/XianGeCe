package win.iqwqi.xiangece.feature.diting.transcription

import javax.inject.Inject
import javax.inject.Singleton
import win.iqwqi.xiangece.feature.diting.domain.DitingMarkerType

data class DitingSignal(
    val type: DitingMarkerType,
    val title: String,
    val confidence: Float,
)

/** Conservative, explainable local heuristics for the first pass of the radar. */
@Singleton
class DitingSignalAnalyzer @Inject constructor() {
    fun analyze(text: String, mode: String, precedingText: String = ""): List<DitingSignal> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return emptyList()
        val normalizedPreceding = precedingText.replace(Regex("\\s+"), " ").trim()
        // 水课的口语问句可能被 8 秒边界切开；只在前一段本身没有触发时拼一次上下文，避免重复标记。
        val questionInput = if (
            mode == "water_class" &&
            normalizedPreceding.isNotBlank() &&
            questionScore(normalizedPreceding, mode) == null
        ) {
            "$normalizedPreceding $normalized"
        } else {
            normalized
        }
        return buildList {
            highlightScore(normalized)?.let { add(DitingSignal(DitingMarkerType.AUTO_HIGHLIGHT, "疑似重点", it)) }
            val questionConfidence = questionScore(questionInput, mode) ?: if (questionInput != normalized) {
                questionScore("$normalizedPreceding$normalized", mode)
            } else {
                null
            }
            questionConfidence?.let { add(DitingSignal(DitingMarkerType.AUTO_QUESTION, "疑似提问", it)) }
        }
    }

    private fun highlightScore(text: String): Float? {
        val explicitPhrases = listOf(
            "重点", "考点", "必考", "考试重点", "考试范围", "作业要求", "截止时间",
            "易错", "容易错", "务必", "记住", "记下来", "记牢", "背下来", "抄下来",
            "画重点", "画线", "请画线", "圈出来", "圈出", "标注", "标出来",
            "非常重要", "一定要注意", "考试会考", "会考",
            "key point", "important", "this is important", "must remember", "remember this", "take note", "write it down", "you need to know", "must know", "will be on the exam",
        )
        val contextualPhrases = listOf("核心", "关键")
        val supportPhrases = listOf("考试", "作业", "注意", "总结", "结论", "重点掌握", "需要掌握", "必须掌握", "熟记", "背诵", "这里要背", "老师强调", "important", "key point", "exam", "will be on the exam", "you need to know", "remember this", "must know", "write it down", "take note", "mark this")
        val explicitHits = explicitPhrases.count { containsPhrase(text, it) }
        val contextualHits = contextualPhrases.count { containsPhrase(text, it) }
        val supportHits = supportPhrases.count { containsPhrase(text, it) }
        return when {
            explicitHits >= 2 || explicitHits == 1 && supportHits >= 1 -> 0.96f
            explicitHits == 1 -> 0.90f
            contextualHits >= 1 && supportHits >= 1 -> 0.88f
            supportHits >= 2 -> 0.84f
            else -> null
        }
    }

    private fun questionScore(text: String, mode: String): Float? {
        val directQuestionPhrases = listOf(
            "有没有问题", "还有问题吗", "有问题吗", "有什么问题", "有什么疑问",
            "谁能回答", "谁来回答", "请问", "欢迎提问", "可以提问", "提问时间",
            "有同学想问", "有同学要问", "有同学有问题", "同学有什么问题", "有啥问题", "有啥疑问",
            "还有什么要问", "还有什么想问", "还有问题要问吗", "有谁要问", "谁有问题", "谁有疑问", "有同学想提问",
            "有不懂的吗", "有不明白的吗", "有不清楚的吗", "有没有不懂",
            "有没有不清楚", "有疑问吗", "听懂了吗", "明白了吗", "理解了吗",
            "清楚了吗", "大家可以提问", "大家可以问", "有问题可以问",
            "有疑问可以问",
        )
        val directHits = directQuestionPhrases.count { text.contains(it) }
        val englishHits = listOf(
            "any questions", "does anyone", "who can answer", "open for questions", "anyone have a question", "feel free to ask",
        ).count { containsPhrase(text, it) }
        val standaloneEnglishQuestion = containsWord(text, "question") &&
            !Regex("(?i)\\b(?:the|this|that) question(?: is| of| number| means| refers to| asks)\\b").containsMatchIn(text) &&
            !Regex("(?i)\\b(?:answer|answers|answered|discuss|discusses|explain|explains|solve|solves)\\s+(?:the|this|that)?\\s*question\\b").containsMatchIn(text)
        val punctuationQuestion = text.contains('？') || text.contains('?')
        val waterClass = mode == "water_class"
        val invitationHits = directHits + englishHits + if (standaloneEnglishQuestion) 1 else 0

        // 水课需要“高召回”：课堂真实提问经常被 ASR 切成“要提问了 / 问一下 / 回答一下”
        // 这类没有问号、也不包含“有没有问题”的口语片段，不能被专业课的保守阈值漏掉。
        val waterActionPhrases = listOf(
            "提问了", "提问啦", "提问吗", "提问呢", "要提问", "现在提问", "开始提问",
            "接下来提问", "继续提问", "提问一下", "提问环节", "问答环节",
            "问一下", "问一下问题", "问个问题", "问一个问题", "提个问题", "提一个问题", "问问题",
            "我要问", "我想问", "我有个问题", "我有一个问题", "老师我想问", "要问了",
            "问题我要", "问题吗", "问题呢", "问题啊", "回答问题", "回答一下", "回答这个问题",
            "请回答", "请你回答", "请大家回答", "请同学回答", "请同学们回答", "来回答", "你来回答", "同学回答", "同学来回答",
            "谁来答", "哪位同学", "哪一个同学", "请一位同学", "点名", "抽答", "举手", "答疑",
        )
        val waterActionHits = waterActionPhrases.count { text.contains(it) }
        // 专业课也要提醒真实的提问、点名和回答环节；只匹配带动作语义的短语，
        // 不把“这个问题如何证明”这类讲解内容误报成课堂提问。
        val professionalActionPhrases = listOf(
            "现在要提问", "现在提问", "开始提问", "接下来提问", "继续提问", "提问了",
            "提问一下", "提问这个问题", "我要提问", "我想提问", "要提问",
            "问一下", "问个问题", "问一个问题", "提个问题", "提一个问题",
            "我要问", "我想问", "我有个问题", "我有一个问题", "请回答",
            "请同学回答", "请同学们回答", "同学来回答", "谁来回答", "点名", "抽答", "答疑",
        )
        val professionalActionHits = professionalActionPhrases.count { text.contains(it) }
        val waterQuestionContext = text.contains("问题") && (
            punctuationQuestion || text.contains("吗") || text.contains("什么") || text.contains("谁") ||
                text.contains("哪") || text.contains("请") || text.contains("提问") || text.contains("回答")
            )

        return when {
            waterClass && waterActionHits >= 2 -> 0.98f
            professionalActionHits >= 1 -> if (waterClass) 0.96f else 0.94f
            invitationHits >= 2 -> 0.96f
            waterClass && waterActionHits == 1 -> 0.94f
            invitationHits == 1 -> if (waterClass) 0.90f else 0.86f
            waterClass && waterQuestionContext -> 0.88f
            waterClass && punctuationQuestion -> 0.80f
            else -> null
        }
    }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        if (phrase.any(Char::isLetter)) containsWord(text, phrase) else text.contains(phrase)

    private fun containsWord(text: String, phrase: String): Boolean {
        val pattern = "(?i)(?<![A-Za-z])${Regex.escape(phrase)}(?![A-Za-z])"
        return Regex(pattern).containsMatchIn(text)
    }
}
