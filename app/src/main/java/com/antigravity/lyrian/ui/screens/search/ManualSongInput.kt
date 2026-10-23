package com.antigravity.lyrian.ui.screens.search

/**
 * 手動輸入的解析（模組 A 的「手動兜底」）。
 *
 * 外呼被擋（503／斷網）時，使用者仍要能練——
 * 所以搜尋頁提供兩個輸入框：YouTube 連結、LRC 全文。
 * 這裡只做純字串處理，方便單元測試。
 */
object ManualSongInput {

    /** YouTube 影片 id 固定 11 個字元，只含 `[A-Za-z0-9_-]`。 */
    private val VIDEO_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

    /** `watch?v=ID`、`youtu.be/ID`、`embed/ID`、`shorts/ID`、`live/ID`。 */
    private val URL_PATTERNS = listOf(
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""/embed/([A-Za-z0-9_-]{11})"""),
        Regex("""/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""/live/([A-Za-z0-9_-]{11})"""),
    )

    /**
     * 從使用者貼上的東西取出影片 id。
     *
     * 接受：完整網址、分享短網址、純 id。其他一律回 null（不要亂猜）。
     */
    fun extractVideoId(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        if (VIDEO_ID.matches(text)) return text

        URL_PATTERNS.forEach { pattern ->
            pattern.find(text)?.let { return it.groupValues[1] }
        }
        return null
    }

    /**
     * 判斷貼上的歌詞看起來像不像 LRC。
     *
     * LRC 的時間標記長這樣：`[00:12.34]`。有時間標記才能跟著音樂跑；
     * 沒有的話仍可練習，只是歌詞不會跟著歌曲推進（`LyricsParser` 會補間隔）。
     */
    fun looksLikeLrc(text: String): Boolean = Regex("""\[\d{1,2}:\d{2}([.:]\d{1,3})?]""").containsMatchIn(text)

    /**
     * 手動 session 的 id。
     *
     * 用負數，跟 LRCLIB 的正整數 id 區隔開來——這樣在 Room 的快取與除錯輸出裡
     * 一眼就能看出「這首是手動輸入的」。
     */
    fun manualSessionId(key: String): Long {
        val hash = key.hashCode().toLong()
        val magnitude = (hash % 1_000_000_000L)
        return if (magnitude == 0L) -1L else -kotlin.math.abs(magnitude)
    }
}
