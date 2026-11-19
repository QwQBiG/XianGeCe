package win.iqwqi.xiangece.core.ocr

data class OfflineOcrPackDescriptor(
    val id: String,
    val title: String,
    val description: String,
    val files: List<OfflineOcrPackFile>,
    val detLimitSideLen: Int = 736,
    val detMaxSideLimit: Int = 4000,
    val detLimitType: String = "min",
) {
    val totalBytes: Long get() = files.sumOf { it.bytes }
}

/** PaddlePaddle official ONNX releases, pinned to metadata checked on 2026-09-11. */
internal object OfflineOcrServerPack {
    val descriptor = OfflineOcrPackDescriptor(
        id = "pp_ocrv5_server_zh_en_onnx",
        title = "中文 OCR · 增强",
        description = "PP-OCRv5 Server · 适合复杂文字与文档，占用更多内存，识别较慢",
        // Bound detector input on phones: the server model is substantially larger.
        detLimitSideLen = 1280,
        detMaxSideLimit = 1280,
        detLimitType = "max",
        files = listOf(
            file(
                "det.onnx", "PP-OCRv5_server_det_onnx", "inference.onnx",
                "dcf248c4dcc064c6d030db241255f9e8fbc6733a", 88_116_791L,
                "10803475a591f7dc623e24670fb5752ec94d39a1f8cf069aac1b6f0ce19cfc85",
            ),
            file(
                "rec.onnx", "PP-OCRv5_server_rec_onnx", "inference.onnx",
                "b70df217f4fd99d14f970bad092cebe7d74cc4d1", 84_503_027L,
                "d9dc333c9c7b042c6dffb8e33d72b6f65c9c1d463d0a3c2f78174fea55e94752",
            ),
            file(
                "rec.yml", "PP-OCRv5_server_rec_onnx", "inference.yml",
                "b70df217f4fd99d14f970bad092cebe7d74cc4d1", 148_345L,
                "2c719dba044c4e2228aef8ff92f5f575394d75d24c16de096a33b7cfd902f66d",
            ),
        ),
    )

    private fun file(
        name: String, repo: String, remoteName: String, revision: String, bytes: Long, sha256: String,
    ): OfflineOcrPackFile {
        val path = "PaddlePaddle/$repo/resolve/$revision/$remoteName?download=true"
        return OfflineOcrPackFile(
            localName = name,
            bytes = bytes,
            sha256 = sha256,
            domesticUrl = "https://hf-mirror.com/$path",
            officialUrl = "https://huggingface.co/$path",
            mirrorUrls = listOf("https://hf-cdn.sufy.com/$path", "https://alpha.hf-mirror.com/$path"),
        )
    }
}
