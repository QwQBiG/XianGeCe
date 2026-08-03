package win.iqwqi.xiangece.core.importing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
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
    ): Pair<String, List<TimetableCandidate>> = withContext(Dispatchers.Default) {
        require(file.isFile && file.length() > 0) { "PDF 文件不存在或为空" }
        PDFBoxResourceLoader.init(context)
        val regions = runCatching { extractTextRegions(file) }.getOrDefault(emptyList())
        val originalText = regions.joinToString("\n") { it.text }.trim()
        val direct = pdfTextParser.parse(regions, defaultEndWeek)
        if (direct.isNotEmpty()) {
            return@withContext originalText to direct
        }
        require(originalText.isNotBlank()) {
            "PDF 没有可读取文字层；请改用教务系统 HTML，或使用支持视觉的 AI 截图识别"
        }
        originalText to emptyList()
    }

    private fun extractTextRegions(file: File): List<OcrRegion> =
        PDDocument.load(file).use { document ->
            require(document.numberOfPages in 1..MAX_PAGES) { "PDF 页数超过 $MAX_PAGES 页，请只保留课表页面" }
            buildList {
                for (page in 1..document.numberOfPages) {
                    val stripper = PositionedTextStripper(this)
                    stripper.sortByPosition = true
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(document)
                }
            }
        }

    private class PositionedTextStripper(
        private val target: MutableList<OcrRegion>,
    ) : PDFTextStripper() {
        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (text.isBlank() || textPositions.isEmpty()) return
            target += OcrRegion(
                text = text.trim(),
                left = textPositions.minOf { it.xDirAdj }.toInt(),
                top = textPositions.minOf { it.yDirAdj - it.heightDir }.toInt(),
                right = textPositions.maxOf { it.xDirAdj + it.widthDirAdj }.toInt(),
                bottom = textPositions.maxOf { it.yDirAdj }.toInt(),
            )
        }
    }

    private companion object {
        const val MAX_PAGES = 8
    }
}
