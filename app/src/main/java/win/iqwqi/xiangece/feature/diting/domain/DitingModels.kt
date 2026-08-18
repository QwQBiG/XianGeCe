package win.iqwqi.xiangece.feature.diting.domain

enum class DitingMode(val key: String, val label: String) {
    PROFESSIONAL("professional", "专业课"),
    WATER_CLASS("water_class", "水课"),
}

enum class DitingLanguageMode(val key: String, val label: String) {
    AUTO("auto", "自动识别"),
    CHINESE("zh", "中文"),
    ENGLISH("en", "English"),
    MIXED("mixed", "中英混合"),
}

fun prefersOfflineDitingTranscription(languageMode: String, offlinePackInstalled: Boolean): Boolean =
    offlinePackInstalled

enum class DitingSessionStatus(val key: String) {
    DRAFT("draft"),
    RECORDING("recording"),
    PAUSED("paused"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
}

enum class DitingMarkerType(val key: String, val label: String) {
    MANUAL_HIGHLIGHT("manual_highlight", "重点"),
    MANUAL_QUESTION("manual_question", "问题"),
    AUTO_HIGHLIGHT("auto_highlight", "疑似重点"),
    AUTO_QUESTION("auto_question", "疑似提问"),
}
