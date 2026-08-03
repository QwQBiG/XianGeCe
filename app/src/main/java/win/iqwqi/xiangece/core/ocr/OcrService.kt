package win.iqwqi.xiangece.core.ocr

data class OcrRegion(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

data class OcrPage(
    val text: String,
    val width: Int,
    val height: Int,
    val lines: List<OcrRegion>,
    val blocks: List<OcrRegion>,
)
