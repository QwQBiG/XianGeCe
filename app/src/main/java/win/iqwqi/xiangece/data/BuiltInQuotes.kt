package win.iqwqi.xiangece.data

/** The single source of truth for the six built-in daily quotes. */
internal object BuiltInQuotes {
    data class Entry(val text: String, val author: String)

    val entries: List<Entry> = listOf(
        Entry("不积跬步，无以至千里；不积小流，无以成江海。", "荀子"),
        Entry("锲而不舍，金石可镂。", "荀子"),
        Entry("千里之行，始于足下。", "老子"),
        Entry("天行健，君子以自强不息。", "周易"),
        Entry("博观而约取，厚积而薄发。", "苏轼"),
        Entry("为有牺牲多壮志，敢教日月换新天。", "毛泽东"),
    )
}
