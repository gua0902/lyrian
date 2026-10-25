package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.ChoiceRecord
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine

/**
 * 「句子交界」的計算。
 *
 * ## 為什麼需要這個
 *
 * 擁有者指定的暫停機制是：**一句唱完的那一刻**（＝下一句開始的那一刻）才檢查
 * 「這一句的挖空答了沒」。答了就直接進下一句；沒答就漸出音量、停在交界。
 *
 * 舊版是「一進入含挖空的新句子就暫停」，使用者連聽都沒聽到就被要求作答——
 * 因果完全相反。差別就在這裡用的是**前一句的結束時間**，不是當前句的開始時間。
 *
 * ## 為什麼結束時間要用推的
 *
 * LRC 只記每句的**開始**時間（`LyricsParser` 因此讓 `endMs` 幾乎都是 null）。
 * 一句的結束時間在實務上就是**下一句的開始時間**，這也是播放器畫歌詞的慣例。
 * 只有最後一句沒有「下一句」可用，此時退到「曲目總長」，再不行才用
 * [LAST_LINE_WINDOW_MS] 的估計窗。
 *
 * 純函式：不碰播放器、不碰 UI，因此可以直接在 JVM 上測（見 `LineBoundaryTest`）。
 */
object LineBoundary {

    /**
     * 最後一句的估計長度。
     *
     * 最後一句之後沒有「下一句的開始時間」可以當結束點，曲目總長也可能還沒拿到
     * （播放器尚未載入）。4 秒大約是一句歌詞的長度，足夠讓使用者聽完再判斷。
     */
    const val LAST_LINE_WINDOW_MS = 4_000L

    /**
     * 某一句的結束時間（毫秒）。
     *
     * 優先序：
     *  1. 這一行自己就有 `endMs`（lyricsfile 格式會給）
     *  2. **下一行的開始時間**（LRC 的常態）
     *  3. 曲目總長（最後一行）
     *  4. 開始時間 + [LAST_LINE_WINDOW_MS]（最後一行且總長未知）
     *
     * @return 找不到這一行時回傳 null
     */
    fun endMsOf(lines: List<LyricLine>, lineIndex: Int, durationMs: Long?): Long? {
        val position = lines.indexOfFirst { it.index == lineIndex }
        if (position < 0) return null

        val line = lines[position]

        line.endMs?.takeIf { it > line.startMs }?.let { return it }

        val next = lines.getOrNull(position + 1)
        if (next != null && next.startMs > line.startMs) return next.startMs

        durationMs?.takeIf { it > line.startMs }?.let { return it }

        return line.startMs + LAST_LINE_WINDOW_MS
    }

    /** 這一行對應的題目；這一行沒有挖空時回傳 null。 */
    fun questionOf(lineIndex: Int, questions: List<FillChoiceQuestion>): FillChoiceQuestion? =
        questions.firstOrNull { it.lineIndex == lineIndex }

    /**
     * 這個題目算不算「已作答」。
     *
     * - 沒有題目（該行沒挖空）→ true：**沒有東西要檢查**，交界直接通過
     * - 有題目 → `selections` 裡有這個 blankIndex 才算答過
     *
     * 判定刻意只看「有沒有作答」，不看對錯——擁有者要求的是「答了就可以繼續」。
     */
    fun isAnswered(question: FillChoiceQuestion?, selections: Map<Int, ChoiceRecord>): Boolean =
        question == null || selections.containsKey(question.blankIndex)
}
