package com.antigravity.lyrian.domain.practice

import kotlin.math.roundToInt

/**
 * 一首歌要出幾題（擁有者 2026-10 核定）。
 *
 * ## 規則
 *
 * ```
 * 題數 = min(maxBlanks, max(MIN_BLANKS, round(可用句數 × RATIO)), 可用句數)
 * 可用句數 = 掃描後「至少有一個通過硬性排除的候選」的句子數
 * ```
 *
 * ## 為什麼是「下限 20 優先、0.6 只在句子多時拉高」
 *
 * 擁有者先要求「保量（回到 20 題以上）」，但長歌若照比例出題會變成 40 題以上
 * 的轟炸。並存的方式是：
 *
 * | 可用句數 | 0.6 × 句數 | 實際題數 |
 * |---|---|---|
 * | 15 | 9 | 15（句子不夠，全出） |
 * | 20 | 12 | 20（下限優先） |
 * | 34 | 20 | 20（剛好） |
 * | 48 | 29 | 29（比例拉高） |
 * | 80 | 48 | 48 → 被 maxBlanks（預設 30／上限 40）截斷 |
 *
 * 短歌不會因為「比例」而少到像半成品（Yellow 曾只有 3 題）；長歌也不會無限膨脹。
 *
 * ## 與難度的關係
 *
 * 題數**不看難度**——難度決定「哪些字值得挖」與「補題時先補誰」，
 * 不決定題數。這是擁有者最後一次定案的語意（先前「難度改題數」已被取代）。
 */
object BlankCountPolicy {

    /**
     * 保量下限。
     *
     * 20 是擁有者指定的數字：低於 20 題的練習「像半成品」。
     * 句子真的不夠時（短歌）以可用句數為準——不做假。
     */
    const val MIN_BLANKS = 20

    /** 可用句數多時，拿這個比例當目標。 */
    const val RATIO = 0.6

    /**
     * 算這次要出幾題。
     *
     * @param eligibleLineCount 通過硬性排除、至少有一個候選的句子數
     * @param maxBlanks 使用者設定的上限（[BlankSelector.MAX_BLANKS] 為硬上限）
     */
    fun target(eligibleLineCount: Int, maxBlanks: Int): Int {
        if (eligibleLineCount <= 0 || maxBlanks <= 0) return 0
        val spacious = (eligibleLineCount * RATIO).roundToInt()
        return minOf(maxBlanks, maxOf(MIN_BLANKS, spacious), eligibleLineCount)
    }
}
