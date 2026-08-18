package win.iqwqi.xiangece.feature.diting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.feature.diting.domain.DitingMarkerType
import win.iqwqi.xiangece.feature.diting.domain.prefersOfflineDitingTranscription
import win.iqwqi.xiangece.feature.diting.transcription.DitingSignalAnalyzer

class DitingSignalAnalyzerTest {
    private val analyzer = DitingSignalAnalyzer()

    @Test
    fun installedOfflinePackSupportsAllLanguageModes() {
        assertTrue(prefersOfflineDitingTranscription("zh", true))
        assertTrue(prefersOfflineDitingTranscription("auto", true))
        assertTrue(prefersOfflineDitingTranscription("mixed", true))
        assertTrue(prefersOfflineDitingTranscription("en", true))
        assertTrue(!prefersOfflineDitingTranscription("zh", false))
    }

    @Test
    fun detectsProfessionalHighlight() {
        val signals = analyzer.analyze("这部分是考试重点，请注意这个结论。", "professional")

        assertTrue(signals.any { it.type == DitingMarkerType.AUTO_HIGHLIGHT })
    }

    @Test
    fun teacherRecordingInstructionGetsHighlightSignalOffline() {
        val signals = analyzer.analyze("这条请画线，考试会用到；旁边再圈出来。", "professional")

        assertEquals(DitingMarkerType.AUTO_HIGHLIGHT, signals.single().type)
        assertTrue(signals.single().confidence >= 0.94f)
    }

    @Test
    fun waterClassQuestionGetsQuestionSignal() {
        val signals = analyzer.analyze("大家有没有问题？any questions", "water_class")

        assertEquals(DitingMarkerType.AUTO_QUESTION, signals.single().type)
        assertTrue(signals.single().confidence >= 0.94f)
    }

    @Test
    fun waterClassNaturalInvitationGetsQuestionSignal() {
        val signals = analyzer.analyze("还有什么要问的，有同学想问吗？", "water_class")

        assertEquals(DitingMarkerType.AUTO_QUESTION, signals.single().type)
        assertTrue(signals.single().confidence >= 0.94f)
    }

    @Test
    fun waterClassQuestionWithoutPunctuationGetsQuestionSignal() {
        val signals = analyzer.analyze("大家还有没有问题", "water_class")

        assertEquals(DitingMarkerType.AUTO_QUESTION, signals.single().type)
        assertTrue(signals.single().confidence >= 0.90f)
    }

    @Test
    fun homeworkDeadlineGetsHighlightSignal() {
        val signals = analyzer.analyze("这次作业截止时间是周五晚上", "professional")

        assertEquals(DitingMarkerType.AUTO_HIGHLIGHT, signals.single().type)
    }

    @Test
    fun questionAsAStatementDoesNotTriggerProfessionalQuestion() {
        assertTrue(analyzer.analyze("The question is how this formula works", "professional").none {
            it.type == DitingMarkerType.AUTO_QUESTION
        })
    }
    @Test
    fun ordinaryEnglishQuestionReferenceDoesNotTriggerQuestion() {
        val text = "This question is about eigenvalues; we will answer the question in the next section."

        assertTrue(analyzer.analyze(text, "professional").none {
            it.type == DitingMarkerType.AUTO_QUESTION
        })
    }
    @Test
    fun ordinarySentenceProducesNoSignal() {
        assertTrue(analyzer.analyze("我们从第一章的定义开始。", "professional").isEmpty())
    }

    @Test
    fun waterClassActionPhraseWithoutQuestionMarkGetsQuestionSignal() {
        val signals = analyzer.analyze("好了现在要提问了，自己点满了问题，问题我要去问了。", "water_class")

        assertEquals(DitingMarkerType.AUTO_QUESTION, signals.single().type)
        assertTrue(signals.single().confidence >= 0.94f)
    }

    @Test
    fun commonWaterClassTeacherAndStudentQuestionCuesGetQuestionSignal() {
        val signals = analyzer.analyze("老师我有个问题，请一位同学回答一下。", "water_class")

        assertEquals(DitingMarkerType.AUTO_QUESTION, signals.single().type)
        assertTrue(signals.single().confidence >= 0.94f)
    }
    @Test
    fun waterClassAnswerPromptGetsQuestionSignal() {
        val signals = analyzer.analyze("这个问题的题目是什么，请同学来回答一下。", "water_class")

        assertEquals(DitingMarkerType.AUTO_QUESTION, signals.single().type)
        assertTrue(signals.single().confidence >= 0.94f)
    }

    @Test
    fun waterClassQuestionWordWithContextGetsQuestionSignal() {
        val signals = analyzer.analyze("我还是发现了很多问题，问题我要去问了。", "water_class")

        assertTrue(signals.any { it.type == DitingMarkerType.AUTO_QUESTION })
    }

    @Test
    fun waterQuestionSplitAcrossSegmentsUsesPrecedingContext() {
        val signals = analyzer.analyze("问题", "water_class", precedingText = "大家有没有")

        assertTrue(signals.any { it.type == DitingMarkerType.AUTO_QUESTION })
    }

    @Test
    fun alreadyMarkedPrecedingQuestionDoesNotDuplicateOnNextSegment() {
        val signals = analyzer.analyze("大家", "water_class", precedingText = "大家有没有问题")

        assertTrue(signals.none { it.type == DitingMarkerType.AUTO_QUESTION })
    }

    @Test
    fun professionalQuestionActionGetsQuestionSignal() {
        val signals = analyzer.analyze("现在要提问了，接下来请同学回答这个问题。", "professional")

        assertTrue(signals.any { it.type == DitingMarkerType.AUTO_QUESTION })
        assertTrue(signals.first { it.type == DitingMarkerType.AUTO_QUESTION }.confidence >= 0.94f)
    }

    @Test
    fun commonWaterClassOralQuestionGetsQuestionSignal() {
        val signals = analyzer.analyze("大家有不懂的吗，听懂了吗？", "water_class")

        assertEquals(DitingMarkerType.AUTO_QUESTION, signals.single().type)
        assertTrue(signals.single().confidence >= 0.94f)
    }

    @Test
    fun teacherInvitesQuestionsInEnglish() {
        val signals = analyzer.analyze("Feel free to ask if anyone has a question.", "water_class")

        assertEquals(DitingMarkerType.AUTO_QUESTION, signals.single().type)
    }

    @Test
    fun commonProfessionalExamEmphasisGetsHighlightSignal() {
        val signals = analyzer.analyze("这道题考试会考，傅里叶变换需要掌握。", "professional")

        assertEquals(DitingMarkerType.AUTO_HIGHLIGHT, signals.single().type)
        assertTrue(signals.single().confidence >= 0.94f)
    }

    @Test
    fun englishExamEmphasisGetsHighlightSignal() {
        val signals = analyzer.analyze("This will be on the exam, so you need to know it.", "professional")

        assertEquals(DitingMarkerType.AUTO_HIGHLIGHT, signals.single().type)
    }
    @Test
    fun conciseEnglishTeachingCueGetsHighlight() {
        val signals = analyzer.analyze("Key point: remember this theorem. Take note.", "professional")

        assertEquals(DitingMarkerType.AUTO_HIGHLIGHT, signals.single().type)
        assertTrue(signals.single().confidence >= 0.94f)
    }

    @Test
    fun ambiguousProfessionalWordAloneDoesNotTriggerHighlight() {
        assertTrue(analyzer.analyze("关键在于把变量代入公式。", "professional").none {
            it.type == DitingMarkerType.AUTO_HIGHLIGHT
        })
    }

    @Test
    fun ambiguousProfessionalWordWithExamContextGetsHighlight() {
        val signals = analyzer.analyze("考试时关键是先写出定义。", "professional")

        assertEquals(DitingMarkerType.AUTO_HIGHLIGHT, signals.single().type)
    }
}
