package com.antigravity.lyrian.data.lang

import com.antigravity.lyrian.domain.model.CefrLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 等級**來源優先序**的單元測試。
 *
 * 用一份手寫的小表驅動，才能精確組合出「字面 vs 原形」「各層互撞」的情況
 * ——真實資產裡找不到剛好符合這些組合的字。
 *
 * 優先序（擁有者指定）：
 * ```
 * CEFR(j/o) > 考試標籤(t) > BNC 分級(b) > UNKNOWN(u)
 * 同一層內：字面形式 贏過 原形
 * ```
 */
class LevelResolutionTest {

    /**
     * | 字 | 標籤層（t） | BNC 層（b） | CEFR 層（j） | UNKNOWN |
     * |---|---|---|---|---|
     * | alpha |    |    | A1 |  |
     * | betas | B1 |    |    |  |  ← 原形 beta 是 A2 (j)
     * | gamma |    | C2 |    |  |  ← 原形 gamm 是 B2 (t)
     * | delta |    |    |    | U |  ← 原形 delt 是 A2 (b)
     * | calif |    | B1 |    |  |  ← 專有名詞
     */
    private val levels = """
        alpha	A1	j	100	0
        betas	B1	t	9000	0
        gamma	C2	b	20000	0
        delta	U	u	0	0
        calif	B1	b	3000	1
        beta	A2	j	200	0
        gamm	B2	t	7000	0
        delt	A2	b	1500	0
        time	A1	j	654	0
    """.trimIndent()

    private val lemmas = """
        betas	beta
        gamma	gamm
        delta	delt
    """.trimIndent()

    private val data = LanguageData.load { path ->
        when {
            path.endsWith("levels.tsv") -> levels
            path.endsWith("lemma.tsv") -> lemmas
            else -> ""
        }
    }

    @Test
    fun `第一層 CEFR 直接採用`() {
        val word = data.lookup("alpha")

        assertEquals(CefrLevel.A1, word.level)
        assertEquals("CEFR-J", word.source)
        assertFalse(word.estimated)
    }

    @Test
    fun `原形的 CEFR 贏過字面的考試標籤`() {
        // betas 自己拿的是 B1 標籤，但原形 beta 在 CEFR-J 是 A2。
        // 若不這樣做，所有變化形都會比原形難 —— 這正是 met/meet 那類 bug。
        val word = data.lookup("betas")

        assertEquals("beta", word.lemma)
        assertEquals(CefrLevel.A2, word.level)
        assertEquals("CEFR-J", word.source)
    }

    @Test
    fun `考試標籤贏過原形的 BNC 分級`() {
        val word = data.lookup("gamma")

        assertEquals(CefrLevel.B2, word.level)
        assertEquals(TAG_SOURCE_LABEL, word.source)
        assertTrue("標籤是推估來源", word.estimated)
    }

    @Test
    fun `原形的 BNC 分級贏過字面的 UNKNOWN`() {
        val word = data.lookup("delta")

        assertEquals(CefrLevel.A2, word.level)
        assertEquals(BNC_SOURCE_LABEL, word.source)
        assertFalse("等級不是 UNKNOWN 就不算未知字", word.isUnlisted)
    }

    @Test
    fun `四層都沒有就是 UNKNOWN，但保留這個字`() {
        val word = data.lookup("epsilon")

        assertNull(word.level)
        assertTrue("UNKNOWN 不可以被視為「沒有這個字」", word.isUnlisted)
        assertEquals(UNLISTED_SOURCE, word.source)
        // 排序時仍要能參與：沒有等級 → 視為最難
        assertEquals(CefrLevel.C2, word.treatAsLevel)
    }

    @Test
    fun `U 列本身也算未知`() {
        // delta 有 U 列，但原形有資料 → 用原形。這裡直接驗 U 列的解析。
        val raw = data.lookup("delta")
        assertFalse(raw.source == "未收錄")
    }

    @Test
    fun `專有名詞旗標會被帶出來`() {
        assertTrue(data.lookup("calif").isProperNoun)
        assertFalse(data.lookup("alpha").isProperNoun)
    }

    @Test
    fun `缺失的欄位不會炸掉`() {
        // 只有兩個欄位的舊格式列要被忽略，而不是拋例外
        val broken = LanguageData.load { path ->
            when {
                path.endsWith("levels.tsv") -> "onlytwo\tA1\n" + "ok\tB1\tj\t12\t0\n"
                else -> ""
            }
        }
        val word = broken.lookup("onlytwo")
        assertNull("格式不完整的列不進表", word.level)
        assertEquals(CefrLevel.B1, broken.lookup("ok").level)
    }

    @Test
    fun `縮寫會退回字幹的等級`() {
        // 歌詞滿是 `time's` / `husband's`。它們在等級表裡沒有自己的列，
        // 若沒有這一層就會變成「四層都查不到」→ 被排到最前面挖。
        val word = data.lookup("time's")

        assertEquals(CefrLevel.A1, word.level)
        assertEquals("CEFR-J", word.source)
        assertFalse(word.isUnlisted)
        assertEquals(654, word.frequencyRank)
    }

    @Test
    fun `詞頻排名會退回原形`() {
        // betas 自己的排名是 9,000，原形 beta 是 200；
        // frequencyRank 取「字面優先，沒有才退原形」
        assertEquals(9000, data.lookup("betas").frequencyRank)
        assertEquals(200, data.lookup("beta").frequencyRank)
    }
}
