package com.antigravity.lyrian.data.audio

/**
 * 把一首歌的**期望時長**對應到最合適的 YouTube Music 版本。
 *
 * ## 為什麼需要這個
 *
 * 歌詞時間戳來自 LRCLIB，音訊來自 YouTube Music——**兩者是不同的來源**。
 * 同一首歌在 YouTube 上可能有錄音室版、現場版、MV 版、remaster 版，
 * 長度都不一樣。若隨便挑一個，歌詞會整首對不上，而使用者只會覺得「App 壞了」。
 *
 * 因此用**時長**當主要判準：時長最接近的版本，最可能就是同一個錄音。
 *
 * ## 刻意做成純函式
 *
 * 這裡不碰網路、不碰 Android，因此匹配邏輯可以在 JVM 上完整單元測試
 * （見 `AudioTrackMatcherTest`）——這種「差幾秒算不合格」的規則最需要測試。
 */
object AudioTrackMatcher {

    /**
     * 時長容忍度（毫秒）。
     *
     * 3 秒足以吸收「開頭靜音長度不同」與「編碼誤差」，
     * 但不足以讓現場版（通常差 30 秒以上）混進來。
     */
    const val DEFAULT_TOLERANCE_MS = 3_000L

    /**
     * 偏好的版本關鍵字（分數越高越優先）。
     *
     * 學習用途需要**穩定的原版歌詞對應**，因此 remaster / album version 可以接受，
     * 現場與翻唱則要排除。
     */
    private val PENALIZED_KEYWORDS = listOf(
        "live", "concert", "acoustic", "cover", "remix", "instrumental",
        "karaoke", "demo", "rehearsal", "session", "unplugged", "現場", "演唱會",
    )

    /** 從標題推測的結果。 */
    data class Match(
        val candidate: YouTubeSong,
        /** 與期望時長的差距（毫秒）；期望時長未知時為 0 */
        val durationDeltaMs: Long,
        /**
         * 是否**確信**這是同一個錄音。
         *
         * `false` 代表時長差太多或資訊不足——此時**不應該**用這條音源，
         * 否則歌詞會錯位。UI 應提示使用者而非硬播。
         */
        val isConfident: Boolean,
    )

    /**
     * 挑出最合適的版本。
     *
     * @param candidates YouTube Music 的搜尋結果
     * @param expectedDurationMs LRCLIB 的時長（毫秒）；null 表示未知
     * @param toleranceMs 時長容忍度
     * @return 最佳匹配；候選為空時回傳 null
     */
    fun pick(
        candidates: List<YouTubeSong>,
        expectedDurationMs: Long?,
        toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    ): Match? {
        if (candidates.isEmpty()) return null

        val scored = candidates.map { candidate ->
            val delta = durationDelta(candidate, expectedDurationMs)
            Match(
                candidate = candidate,
                durationDeltaMs = delta ?: Long.MAX_VALUE,
                isConfident = delta != null && delta <= toleranceMs,
            )
        }

        // 排序：確信的優先 → **乾淨版本優先** → 時長差距小的優先
        //
        // ⚠️ 順序很重要。「是否有現場／翻唱關鍵字」必須排在時長之前，
        // 否則會出現這個真實情境：
        //   錄音室版 295s vs 期望 295.502s → 差 0.502s
        //   翻唱版   296s vs 期望 295.502s → 差 0.498s
        // 純看時長，翻唱版會贏 4 毫秒。時長是用來**區分不同錄音**的，
        // 但同一首歌的翻唱與原版長度本來就幾乎一樣，此時標題才是有效訊號。
        return scored.minWithOrNull(
            compareBy(
                { if (it.isConfident) 0 else 1 },
                { penaltyFor(it.candidate) },
                { it.durationDeltaMs },
            ),
        )
    }

    /**
     * 候選與期望時長的差距。
     *
     * @return 任一邊時長未知時回傳 null（**無法判斷**，不等於差距 0）
     */
    fun durationDelta(candidate: YouTubeSong, expectedDurationMs: Long?): Long? {
        val expectedSeconds = expectedDurationMs?.takeIf { it > 0 }?.let { it / 1000.0 }
            ?: return null
        val actualSeconds = candidate.durationSeconds?.takeIf { it > 0 }?.toDouble()
            ?: return null

        return kotlin.math.abs((actualSeconds - expectedSeconds) * 1000).toLong()
    }

    /** 標題含現場／翻唱等關鍵字時扣分（數字越小越好）。 */
    fun penaltyFor(candidate: YouTubeSong): Int {
        val haystack = "${candidate.title} ${candidate.album.orEmpty()}".lowercase()
        return if (PENALIZED_KEYWORDS.any { it in haystack }) 1 else 0
    }

    /**
     * 清除 YouTube 標題裡的雜訊後比對。
     *
     * 例如 `"Hello (Remastered 2015)"` 與 `"Hello"` 應該視為同一首。
     */
    fun normalizeTitle(value: String): String = value
        .lowercase()
        .replace(Regex("""[\(\[\{][^)\]\}]*[\)\]\}]"""), " ")
        .replace(Regex("""\b(official|video|audio|lyrics?|hd|hq|mv|4k)\b"""), " ")
        .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
