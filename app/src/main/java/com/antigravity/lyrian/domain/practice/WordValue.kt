package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.CefrLevel
import kotlin.math.ln

/**
 * 一個候選字的**學習價值分數**（0–100）。
 *
 * ## 為什麼需要分數，而不是只看等級
 *
 * 舊版在「每一句」裡挑等級最高的字，於是：
 * ```
 * 第 3 句 只剩 sorry (A1)      → 挖 sorry
 * 第 30 句 只剩 desperate (B1) → 挖 desperate
 * ```
 * 兩題的「學習價值」天差地遠，但每句都只有一個候選，等級排序完全看不出差別。
 * 分數讓**跨句子**可以比較，選題時才能真正挑「最值得學的」。
 *
 * ## 組成
 *
 * | 分項 | 上限 | 說明 |
 * |---|---|---|
 * | 等級契合 | 30 | 越接近這次的難度目標越高 |
 * | 詞頻稀缺 | 25 | BNC 排名越後面越有價值；**沒有排名給中段 12** |
 * | 詞性價值 | 20 | 名詞／動詞／形容詞／副詞才是「學得到的詞」 |
 * | 詞形價值 | 15 | 原形 > 派生 > 屈折（原形最值得記） |
 * | 語境 | 10 | 句中位置、這一句有沒有其他實詞 |
 * | 扣分 | −30 | 全大寫、很常見、出現太多次 |
 *
 * ## 未知等級不是最高難度
 *
 * 擁有者明確要求：查不到等級的字**不得排除，也不得一律視為最難**。
 * 所以 `level == null` 給 12 分（中段），而不是 30 分。
 * 真正罕見的未知字還是會靠「稀缺」與「詞性」拿到中高分，
 * 但不會因為「查不到」就壓過一個扎實的 B2 實詞。
 *
 * 純函式，可單元測試（見 `WordValueTest`）。
 */
object WordValue {

    /** 分數上限。 */
    const val MAX = 100

    /** 未知等級的等級契合分數（中段，不是最高）。 */
    const val UNKNOWN_LEVEL_FIT = 12

    /** 沒有 BNC 排名時的稀缺分數（中段）。 */
    const val UNKNOWN_RARITY = 12

    /** 名詞／動詞／形容詞／副詞 → 值得學的實詞。 */
    private val CONTENT_POS = setOf("n", "v", "vt", "vi", "a", "adj", "ad", "adv")

    /** 這些詞性即使沒被功能詞表擋掉，也不該拿高分。 */
    private val LOW_VALUE_POS = setOf(
        "pron", "prep", "conj", "art", "num", "int", "abbr", "aux", "det", "part",
    )

    /** 構詞上「多學一層」的後綴：派生詞比單純屈折更有價值。 */
    private val DERIVATIONAL_SUFFIXES = listOf(
        "tion", "sion", "ment", "ness", "ity", "ous", "ive", "able", "ible", "ally", "ly",
    )

    /**
     * 算一個候選字的價值。
     *
     * @param level 等級；null = UNKNOWN
     * @param rank BNC 排名；null/0 = 沒有資料
     * @param partOfSpeech 詞性縮寫（`n.` / `a.` / `ad.`）；null = 未知
     * @param surface 字面形式
     * @param lemma 原形
     * @param capitalizedOnly 每次都大寫（歌詞強調、或名字）→ 扣分
     * @param occurrences 這首歌出現幾次
     */
    fun score(
        level: CefrLevel?,
        rank: Int?,
        partOfSpeech: String?,
        surface: String,
        lemma: String,
        difficulty: BlankDifficulty,
        capitalizedOnly: Boolean = false,
        occurrences: Int = 1,
    ): Int {
        val raw = levelFit(level, difficulty) +
            rarity(rank) +
            posValue(partOfSpeech) +
            formValue(surface, lemma) +
            contextValue(capitalizedOnly) -
            penalty(rank, capitalizedOnly, occurrences)

        return raw.coerceIn(0, MAX)
    }

    /** 等級越接近這次的難度目標越高（0–30）。 */
    fun levelFit(level: CefrLevel?, difficulty: BlankDifficulty): Int {
        if (level == null) return UNKNOWN_LEVEL_FIT
        val target = when (difficulty) {
            BlankDifficulty.BASIC -> CefrLevel.A2
            BlankDifficulty.ADVANCED -> CefrLevel.B2
            BlankDifficulty.CHALLENGE -> CefrLevel.C1
        }
        val distance = kotlin.math.abs(level.ordinal - target.ordinal)
        return (30 - distance * 10).coerceAtLeast(0)
    }

    /** 排名越後面（越罕見）越高（0–25）。 */
    fun rarity(rank: Int?): Int {
        if (rank == null || rank <= 0) return UNKNOWN_RARITY
        val normalized = (ln(rank.toDouble()) / ln(MAX_RANK)).coerceIn(0.0, 1.0)
        // 四捨五入而不是截斷：截斷會讓 20k 與 29k 同分，稀有度就失去區辨力
        return Math.round(normalized * 25).toInt().coerceIn(0, 25)
    }

    /** 實詞才有學習價值（0–20）。 */
    fun posValue(partOfSpeech: String?): Int {
        val key = partOfSpeech?.trim()?.trimEnd('.')?.lowercase() ?: return 10
        return when {
            key in CONTENT_POS -> 20
            key in LOW_VALUE_POS -> 6
            else -> 10
        }
    }

    /**
     * 詞形價值（0–15）：**原形 > 派生 > 屈折**。
     *
     * 判斷看的是**字面形式**的構詞後綴（`-tion`、`-ness`…）：一個字自己就是派生詞
     * （`consideration`）時，它同時是原形也是「多一層構詞」，給中間分；
     * 純屈折（`convinced` ← `convince`）最低。
     */
    fun formValue(surface: String, lemma: String): Int {
        val derived = hasDerivationalSuffix(surface)
        if (surface.equals(lemma, ignoreCase = true)) {
            return if (derived) 13 else 15
        }
        return if (derived) 12 else 8
    }

    private fun hasDerivationalSuffix(word: String): Boolean {
        val lower = word.lowercase()
        return DERIVATIONAL_SUFFIXES.any { suffix ->
            lower.endsWith(suffix) && lower.length > suffix.length + 2
        }
    }

    /** 語境分（0–10）：全大寫／被強調的字不適合當題目。 */
    fun contextValue(capitalizedOnly: Boolean): Int = if (capitalizedOnly) 4 else 10

    /** 扣分（0–30）。 */
    fun penalty(rank: Int?, capitalizedOnly: Boolean, occurrences: Int): Int {
        var total = 0
        if (capitalizedOnly) total += 15
        // 前 2000 名太常見（前 500 已在更前面硬性排除）
        if (rank != null && rank in 1..2000) total += 10
        // 副歌一直重複的字，挖了會一直看到同一個答案
        if (occurrences > 3) total += 5
        return total
    }

    /** 稀有度的對數基準（大約是 BNC 的詞表長度）。 */
    private const val MAX_RANK = 30_000.0
}
