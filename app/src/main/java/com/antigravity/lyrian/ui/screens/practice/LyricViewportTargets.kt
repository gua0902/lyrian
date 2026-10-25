package com.antigravity.lyrian.ui.screens.practice

/**
 * 歌詞區的兩個「目標」——**捲動位置**與**高亮行**。
 *
 * ## 為什麼要分開
 *
 * 舊版只有一個 `targetLineIndex`，同時餵給捲動與高亮：
 * ```
 * targetLineIndex = singingLineIndex ?: 第一句
 * ```
 * 歌曲一進來、音訊還沒到第一句的時間戳時，`singingLineIndex` 是 null，
 * 這個運算式就退回「第一句」——於是第一句**跟三點同時亮起**。
 *
 * 正確的行為：
 * ```
 * 捲動：還沒開始唱就先對到第一句（三點就在它上方）→ 這沒有錯
 * 高亮：只有音訊真的唱到那一行才亮；沒到 → 不亮任何一行
 * ```
 *
 * 這個型別把兩者分開，讓「高亮不得提前」變成一條可測試的規則，
 * 而不是散在 composable 裡的兩個三元運算式。
 *
 * @property scrollTo 要 `animateScrollToItem` 到哪一行
 * @property highlight 要高亮哪一行；null = 沒有任何一行該亮
 */
internal data class LyricViewportTargets(
    val scrollTo: Int,
    val highlight: Int?,
) {
    companion object {
        /**
         * @param singingLineIndex 音訊目前唱到的行；還沒開始唱時為 null
         * @param firstLineIndex 歌詞第一行的索引
         * @param fallbackHighlight **沒有時間軸**時要亮哪一行（依作答進度）；
         *   有時間軸時必須傳 null——那條路徑由音訊決定，預先亮第一行就是 bug
         * @param fallbackLineIndex 沒有任何歌詞時的退路（通常是 0）
         */
        fun of(
            singingLineIndex: Int?,
            firstLineIndex: Int?,
            fallbackHighlight: Int? = null,
            fallbackLineIndex: Int = 0,
        ): LyricViewportTargets = LyricViewportTargets(
            scrollTo = singingLineIndex
                ?: fallbackHighlight
                ?: firstLineIndex
                ?: fallbackLineIndex,
            // ⚠️ **不要**在這裡退回第一行：那正是「第一句提前亮起」的 bug。
            // 只有「沒有時間軸」的歌才由作答進度決定亮哪一行。
            highlight = singingLineIndex ?: fallbackHighlight,
        )
    }
}
