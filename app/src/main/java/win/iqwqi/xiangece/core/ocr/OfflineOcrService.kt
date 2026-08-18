package win.iqwqi.xiangece.core.ocr

import android.graphics.BitmapFactory
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * PP-OCRv5 mobile 的本机中文/英文识别适配层。
 * 模型只从应用私有目录加载，不会把图片上传到网络。
 */
@Singleton
class OfflineOcrService @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val packManager: OfflineOcrPackManager,
) {
    private val mutex = Mutex()
    private var engine: PaddleOCR? = null
    private var engineDirectory: String? = null

    fun isReady(): Boolean = packManager.isChinesePackInstalled()

    suspend fun recognize(imageFile: File): OfflineOcrResult = mutex.withLock {
        check(imageFile.isFile) { "原始图片副本已不存在，请重新导入" }
        check(packManager.isChinesePackInstalled()) { "尚未安装免费中文离线 OCR 包" }

        // 先做轻量图片预检，避免坏图或极大图先触发 OpenCV/ORT 原生加载。
        val bounds = readImageBounds(imageFile)
        val pixelCount = bounds.first.toLong() * bounds.second.toLong()
        check(pixelCount <= MAX_IMAGE_PIXELS) {
            "图片分辨率过大，请先裁剪后再识别（最大支持约 ${MAX_IMAGE_PIXELS / 1_000_000} MP）"
        }
        check(imageFile.length() <= MAX_IMAGE_BYTES) {
            "图片文件过大，请先压缩或裁剪后再识别（最大 ${MAX_IMAGE_BYTES / (1024 * 1024)} MB）"
        }

        val modelDirectory = packManager.modelDirectory()
        val ocr = getOrCreate(modelDirectory)
        val imageBytes = imageFile.readBytes()
        val run = try {
            ocr.recognize(imageBytes)
        } catch (error: Throwable) {
            // A native ORT/OpenCV failure can leave the cached engine unusable.
            // Release it before the next user retry so the engine is rebuilt cleanly.
            if (engine === ocr) {
                try {
                    ocr.release()
                } catch (_: Throwable) {
                    // Preserve the original inference failure for the caller.
                }
                engine = null
                engineDirectory = null
            }
            throw error
        }
        val regions = run.results.mapNotNull { result ->
            val points = result.box.points
            if (points.isEmpty()) return@mapNotNull null
            OcrRegion(
                text = result.text.trim(),
                left = points.minOf { it.x }.toInt().coerceAtLeast(0),
                top = points.minOf { it.y }.toInt().coerceAtLeast(0),
                right = points.maxOf { it.x }.toInt().coerceAtLeast(0),
                bottom = points.maxOf { it.y }.toInt().coerceAtLeast(0),
            ).takeIf { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
        }
        val ordered = regions.sortedWith(compareBy<OcrRegion> { it.top }.thenBy { it.left })
        val text = ordered.joinToString("\n") { it.text }.trim()
        check(text.isNotBlank()) { "图片中没有识别到文字，请换一张清晰截图" }
        val confidences = run.results.map { it.confidence }
        OfflineOcrResult(
            page = OcrPage(
                text = text,
                width = bounds.first,
                height = bounds.second,
                lines = ordered,
                blocks = ordered,
            ),
            averageConfidence = confidences.takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat()
                ?.coerceIn(0f, 1f)
                ?: 0f,
            lineCount = ordered.size,
            totalTimeMs = run.totalTimeMs,
        )
    }

    suspend fun release() = mutex.withLock {
        engine?.release()
        engine = null
        engineDirectory = null
    }

    private suspend fun getOrCreate(directory: File): PaddleOCR {
        val existing = engine
        if (existing != null && engineDirectory == directory.absolutePath) return existing
        if (!packManager.verifyInstalledFiles()) {
            packManager.markCorrupted()
            error("中文离线 OCR 包校验失败，请重新导入或下载")
        }
        existing?.release()
        val created = PaddleOCR.create(
            context = context,
            config = PaddleOCRConfig(
                // PP-OCRv5 mobile 常用 min=736；高分辨率截图不会被放大，
                // 低分辨率图片会得到足够的检测尺寸，避免“模型已开启但没有文字”。
                detLimitSideLen = 736,
                detMaxSideLimit = 4000,
                detThresh = 0.3f,
                detBoxThresh = 0.6f,
                recScoreThresh = 0.0f,
                recBatchSize = 1,
            ),
            engineConfig = EngineConfig(numThreads = 4),
            detModelAssetPath = packManager.modelFile("det.onnx").absolutePath,
            recModelAssetPath = packManager.modelFile("rec.onnx").absolutePath,
            recConfigAssetPath = packManager.modelFile("rec.yml").absolutePath,
        )
        engine = created
        engineDirectory = directory.absolutePath
        return created
    }

    private fun readImageBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
        check(options.outWidth > 0 && options.outHeight > 0) { "图片无法解码，请重新选择 JPG、PNG 或 WEBP 图片" }
        return options.outWidth to options.outHeight
    }

    private companion object {
        const val MAX_IMAGE_PIXELS = 32_000_000L
        const val MAX_IMAGE_BYTES = 40L * 1024L * 1024L
    }
}

data class OfflineOcrResult(
    val page: OcrPage,
    val averageConfidence: Float,
    val lineCount: Int,
    val totalTimeMs: Long,
)