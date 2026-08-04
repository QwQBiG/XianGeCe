package win.iqwqi.xiangece.core.importing

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import win.iqwqi.xiangece.core.ocr.OcrRegion
import win.iqwqi.xiangece.domain.parser.PdfTextTimetableParser
import win.iqwqi.xiangece.domain.parser.TimetableCandidate

@Singleton
class PdfTimetableImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfTextParser: PdfTextTimetableParser,
) {
    suspend fun parse(
        file: File,
        defaultEndWeek: Int,
        maxPeriods: Int,
        layout: PdfTextTimetableParser.Layout = PdfTextTimetableParser.Layout.AUTO,
    ): Pair<String, List<TimetableCandidate>> = withContext(Dispatchers.Default) {
        require(file.isFile && file.length() > 0) { "PDF 文件不存在或为空" }
        PDFBoxResourceLoader.init(context)
        val extraction = runCatching { extractPdf(file) }
            .getOrDefault(PdfExtraction("", emptyList()))
        val originalText = extraction.rawText.trim()
        val direct = pdfTextParser.parse(extraction.regions, defaultEndWeek, layout)
            .filter { candidate ->
                candidate.startPeriod in 1..maxPeriods &&
                    candidate.endPeriod in candidate.startPeriod..maxPeriods
            }
        if (direct.isNotEmpty()) {
            return@withContext originalText to direct
        }
        require(originalText.isNotBlank()) {
            "PDF 没有可读取的文字层；请改用教务系统 HTML，或使用支持视觉的 AI 截图识别"
        }
        originalText to emptyList()
    }

    private data class PdfExtraction(
        val rawText: String,
        val regions: List<OcrRegion>,
    )

    private fun extractPdf(file: File): PdfExtraction =
        PDDocument.load(file).use { document ->
            require(document.numberOfPages in 1..MAX_PAGES) {
                "PDF 页数超过 $MAX_PAGES 页，请只保留课表页面"
            }
            val rawText = PDFTextStripper().apply {
                sortByPosition = true
            }.getText(document)
            val regions = buildList {
                for (page in 1..document.numberOfPages) {
                    val stripper = PositionedTextStripper(
                        target = this,
                        pageOffset = (page - 1) * PAGE_Y_STRIDE,
                    )
                    stripper.sortByPosition = true
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(document)
                }
            }
            PdfExtraction(rawText = rawText, regions = regions)
        }

    private class PositionedTextStripper(
        private val target: MutableList<OcrRegion>,
        private val pageOffset: Int,
    ) : PDFTextStripper() {
        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (text.isBlank() || textPositions.isEmpty()) return

            /*
             * PDFTextStripper 的 writeString 往往代表一整条视觉行。一行课表可能横跨七个
             * 星期列，若直接生成一个 OcrRegion，后续就无法恢复列归属。这里根据每个
             * TextPosition 的空白、水平间距和换行来生成连续词块，保留真实二维布局。
             */
            val positions = textPositions.filter { !it.unicode.isNullOrBlank() || it.widthDirAdj > 0f }
            if (positions.isEmpty()) return

            val typicalWidth = positions
                .map { it.widthDirAdj }
                .filter { it > 0.1f }
                .sorted()
                .let { widths ->
                    if (widths.isEmpty()) DEFAULT_GLYPH_WIDTH else widths[widths.size / 2]
                }
            val horizontalGapThreshold = maxOf(MIN_HORIZONTAL_GAP, typicalWidth * GAP_WIDTH_FACTOR)
            val run = mutableListOf<TextPosition>()

            fun flushRun() {
                if (run.isEmpty()) return
                val value = run.joinToString(separator = "") { it.unicode.orEmpty() }
                    .replace("\u0000", "")
                    .trim()
                if (value.isNotBlank()) {
                    target += OcrRegion(
                        text = value,
                        left = run.minOf { it.xDirAdj }.toInt(),
                        top = pageOffset + run.minOf { it.yDirAdj - it.heightDir }.toInt(),
                        right = run.maxOf { it.xDirAdj + it.widthDirAdj }.toInt(),
                        bottom = pageOffset + run.maxOf { it.yDirAdj }.toInt(),
                    )
                }
                run.clear()
            }

            var previous: TextPosition? = null
            positions.forEach { position ->
                val unicode = position.unicode.orEmpty().replace("\u0000", "")
                if (unicode.isBlank()) {
                    flushRun()
                    previous = null
                    return@forEach
                }

                val before = previous
                if (before != null && run.isNotEmpty()) {
                    val gap = position.xDirAdj - (before.xDirAdj + before.widthDirAdj)
                    val lineTolerance = maxOf(
                        MIN_LINE_TOLERANCE,
                        maxOf(before.heightDir, position.heightDir) * LINE_HEIGHT_FACTOR,
                    )
                    val changedLine = kotlin.math.abs(position.yDirAdj - before.yDirAdj) > lineTolerance
                    val movedBackwards = position.xDirAdj + typicalWidth < before.xDirAdj
                    if (changedLine || movedBackwards || gap > horizontalGapThreshold) {
                        flushRun()
                    }
                }

                run += position
                previous = position
            }
            flushRun()
        }
    }

    private companion object {
        const val MAX_PAGES = 8
        const val PAGE_Y_STRIDE = 100_000
        const val DEFAULT_GLYPH_WIDTH = 8f
        const val MIN_HORIZONTAL_GAP = 2.5f
        const val GAP_WIDTH_FACTOR = 0.65f
        const val MIN_LINE_TOLERANCE = 2f
        const val LINE_HEIGHT_FACTOR = 0.45f
    }
}
