package win.iqwqi.xiangece.feature.diting.offline

/** LFS SHA-256 IDs verified upstream; non-LFS text downloaded and hashed separately. */
internal object DitingModernPacks {
    private const val SENSE_REPO = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"
    private const val SENSE_REV = "2365baeacb507f821a0c8120fcee3d484dba7a07"
    private const val NANO_REPO = "sherpa-onnx-funasr-nano-int8-2025-12-30"
    private const val NANO_REV = "6f16bd378457e13f36ccf3910df9017f96c346fb"

    val senseVoice = DitingOfflinePack(
        id = DitingOfflinePackCatalog.SENSE_VOICE_ID,
        title = "轻量 · SenseVoice Small",
        description = "支持中文、英文、日文、韩文和粤语。下载较小，适合日常使用。",
        totalBytes = 239_549_735L,
        files = listOf(
            file(SENSE_REPO, SENSE_REV, "model.int8.onnx", 239_233_841L,
                "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"),
            file(SENSE_REPO, SENSE_REV, "tokens.txt", 315_894L,
                "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"),
        ),
    )

    val funAsrNano = DitingOfflinePack(
        id = DitingOfflinePackCatalog.FUN_ASR_NANO_ID,
        title = "增强 · Fun-ASR-Nano",
        description = "可填写课程中的专业词语，帮助识别。占用空间较大，处理可能较慢。",
        totalBytes = 1_009_605_061L,
        files = listOf(
            file(NANO_REPO, NANO_REV, "encoder_adaptor.int8.onnx", 237_792_748L,
                "f36dea2e30fbc33b5db1d7a7265cc976c5e5586c77b042d5adb1ad27c72db422"),
            file(NANO_REPO, NANO_REV, "embedding.int8.onnx", 155_584_380L,
                "95e61cd0c9c3b9543339a4cf973c95c116815e745ccc1e0285cbd81f76d18644"),
            file(NANO_REPO, NANO_REV, "llm.int8.onnx", 600_356_593L,
                "dfbf9aa3be41bccc257587f151e15c63fbe1b549f2b517f5ccd5bdce3bf4322a"),
            file(NANO_REPO, NANO_REV, "Qwen3-0.6B/merges.txt", 1_671_853L,
                "8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5"),
            file(NANO_REPO, NANO_REV, "Qwen3-0.6B/tokenizer.json", 11_422_654L,
                "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"),
            file(NANO_REPO, NANO_REV, "Qwen3-0.6B/vocab.json", 2_776_833L,
                "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910"),
        ),
    )

    private fun file(repo: String, revision: String, name: String, bytes: Long, hash: String) =
        DitingOfflinePackFile(
            name = name,
            bytes = bytes,
            sha256 = hash,
            domesticUrl = "https://hf-mirror.com/csukuangfj/$repo/resolve/$revision/$name",
            officialUrl = "https://huggingface.co/csukuangfj/$repo/resolve/$revision/$name",
        )
}
