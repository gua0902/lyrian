package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.UNLISTED_SOURCE
import com.antigravity.lyrian.data.lang.WordLevel
import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 逐句選詞（模組 C）的測試。
 *
 * 用**假造的等級表**而不是真資料：這裡要驗的是排序規則與去重，
 * 規則正確性不該被「某個字剛好在 CEFR-J 的哪一級」影響。
 * 真資料的整合測試在 `LanguageDataTest`。
 */
class BlankSelectorTest {

    /** 可控的等級來源。 */
    private class FakeSource(
        private val levels: Map<String, CefrLevel?>,
        private val frequent: Set<String> = emptySet(),
        private val ranks: Map<String, Int> = emptyMap(),
    ) : WordLevelSource {
        override fun lookup(word: String): WordLevel {
            val key = word.lowercase()
            val level = levels[key]
            return WordLevel(
                surface = key,
                lemma = key,
                level = level,
                source = if (level == null) UNLISTED_SOURCE else "CEFR-J",
                frequencyRank = ranks[key],
            )
        }

        override fun isHighFrequency(word: String) = word.lowercase() in frequent
    }

    private fun lines(vararg texts: String): List<LyricLine> =
        texts.mapIndexed { index, text -> LyricLine(index = index, text = text, startMs = index * 1000L) }

    private fun select(
        lines: List<LyricLine>,
        source: WordLevelSource,
        minLevel: CefrLevel = CefrLevel.B1,
        coverage: BlankCoverage = BlankCoverage.ALL,
    ) = BlankSelector.select(lines, minLevel, coverage, source)

    private val baseLevels = mapOf(
        "silhouette" to CefrLevel.C1,
        "velvet" to CefrLevel.B2,
        "whisper" to CefrLevel.B1,
        "lantern" to CefrLevel.A2,
        "happy" to CefrLevel.A1,
        "carousel" to CefrLevel.C2,
    )

    @Test
    fun `每一句最多一個空`() {
        val source = FakeSource(baseLevels)
        val result = select(lines("silhouette velvet carousel"), source)

        assertEquals(1, result.count { it.chosen != null })
        assertEquals(BlankSelector.MAX_BLANKS_PER_LINE, 1)
    }

    @Test
    fun `等級高者優先`() {
        val source = FakeSource(baseLevels)
        // 同一句裡有 A2 / B2 / C1，應該挑 C1 的 silhouette
        val result = select(lines("lantern velvet silhouette"), source)

        assertEquals("silhouette", result.single().chosen?.word)
    }

    @Test
    fun `同級取詞頻更低者`() {
        val source = FakeSource(
            levels = mapOf("velvet" to CefrLevel.B2, "silhouette" to CefrLevel.B2),
            ranks = mapOf("velvet" to 6159, "silhouette" to 11637),
        )
        // silhouette 的 BNC 排名較大＝更罕見，應該優先
        val result = select(lines("velvet silhouette"), source)

        assertEquals("silhouette", result.single().chosen?.word)
        assertTrue(result.single().basis!!.contains("罕見"))
    }

    @Test
    fun `同頻取較長者`() {
        val source = FakeSource(
            levels = mapOf("velvet" to CefrLevel.B2, "carousel" to CefrLevel.B2),
            ranks = mapOf("velvet" to 5000, "carousel" to 5000),
        )
        val result = select(lines("velvet carousel"), source)

        assertEquals("carousel", result.single().chosen?.word)
    }

    @Test
    fun `高頻詞永遠不挖`() {
        val source = FakeSource(
            levels = mapOf("silhouette" to CefrLevel.C1, "party" to CefrLevel.B1),
            frequent = setOf("party"),
        )
        val result = select(lines("party silhouette"), source)

        assertEquals("silhouette", result.single().chosen?.word)
        assertTrue(result.single().rejected.any { it.word == "party" })
    }

    @Test
    fun `低於設定值的字只在該句沒有達標字時使用`() {
        val source = FakeSource(baseLevels)

        // 這句只有 A2 / A1 → 只能挑 A2 的 lantern（A1 最後）
        val onlyLow = select(lines("lantern happy"), source, minLevel = CefrLevel.B1)
        assertEquals("lantern", onlyLow.single().chosen?.word)

        // 這句有 B1 → 不該用 A2
        val mixed = select(lines("lantern whisper"), source, minLevel = CefrLevel.B1)
        assertEquals("whisper", mixed.single().chosen?.word)
    }

    @Test
    fun `沒有實詞的句子不挖`() {
        val source = FakeSource(baseLevels)
        val result = select(lines("Oh oh oh", "Yeah yeah", "La la la"), source)

        assertTrue(result.all { it.chosen == null })
        assertTrue(result.all { it.skippedAsFiller })
    }

    @Test
    fun `跨句不重複用同一個原形`() {
        // 兩句都只有 velvet 是可挖的字（sky / party 是高頻詞）→ 第二句沒得挖
        val source = FakeSource(
            levels = mapOf("velvet" to CefrLevel.B2),
            frequent = setOf("sky", "party"),
        )
        val result = select(lines("velvet sky", "velvet party"), source)

        val chosen = result.mapNotNull { it.chosen?.word }
        assertEquals(listOf("velvet"), chosen)

        assertNull(result[1].chosen)
        assertTrue(result[1].basis!!.contains("挖過"))
    }

    @Test
    fun `均勻選句不會擠在同一段`() {
        // 20 句、每句一個**不同的**罕見字（跨句去重才不會把後面的句子清空）
        val words = listOf(
            "amber", "blaze", "cider", "dune", "ember", "frost", "glow", "haze",
            "iris", "jade", "karma", "lunar", "mist", "nova", "onyx", "prism",
            "quartz", "raven", "sable", "tide",
        )
        val source = FakeSource(words.associateWith { CefrLevel.B2 })
        val twenty = lines(*words.map { "$it here" }.toTypedArray())

        val result = select(twenty, source, coverage = BlankCoverage.FIVE)
        val picked = result.filter { it.chosen != null }.map { it.line.index }

        assertEquals(5, picked.size)
        assertTrue("第一個選到的最前面幾句內：$picked", picked.first() <= 2)
        assertTrue("最後一個選到的最後幾句內：$picked", picked.last() >= 17)
    }

    @Test
    fun `全部模式會挖光所有合格句`() {
        val source = FakeSource(
            mapOf(
                "amber" to CefrLevel.B2,
                "blaze" to CefrLevel.B2,
                "cider" to CefrLevel.B2,
            ),
        )
        val result = select(
            lines("amber one", "blaze two", "cider three"),
            source,
            coverage = BlankCoverage.ALL,
        )

        assertEquals(3, result.count { it.chosen != null })
    }

    @Test
    fun `未收錄的字視為最難，優先被挖`() {
        val source = FakeSource(mapOf("velvet" to CefrLevel.B2)) // "florble" 不在表內
        val result = select(lines("velvet florble"), source)

        assertEquals("florble", result.single().chosen?.word)
        assertEquals(CefrLevel.C2, result.single().chosen?.level?.treatAsLevel)
    }

    @Test
    fun `選句數的均勻抽樣邊界`() {
        // 沒有候選句
        assertEquals(emptySet<Int>(), BlankSelector.pickEvenly(emptyList(), BlankCoverage.TEN))

        // 目標比句子多 → 全收
        assertEquals(
            setOf(0, 1, 2, 3),
            BlankSelector.pickEvenly(listOf(0, 1, 2, 3), BlankCoverage.TEN),
        )

        // 全部模式 → 全收
        assertEquals(
            setOf(4, 5, 6),
            BlankSelector.pickEvenly(listOf(4, 5, 6), BlankCoverage.ALL),
        )

        // 20 句抽 10 句 → 10 句且不重複
        val picked = BlankSelector.pickEvenly((0 until 20).toList(), BlankCoverage.TEN)
        assertEquals(10, picked.size)
    }
}
