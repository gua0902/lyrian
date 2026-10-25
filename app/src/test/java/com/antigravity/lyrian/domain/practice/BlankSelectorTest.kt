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
 * 用**假造的等級表**而不是真資料：這裡要驗的是排序規則、
 * 硬性排除與去重，規則正確性不該被「某個字剛好在 CEFR-J 的哪一級」影響。
 * 真資料的整合測試在 `LanguageDataTest`，題數報表在 `BlankReportTest`。
 */
private val FILLER_LEVEL: CefrLevel = CefrLevel.A1
private const val FILLER_RANK = 1500

/**
 * 40 個互不相同的「實詞」。
 *
 * 必須是純字母（`amber1` 會被 tokenizer 切成 `amber`，40 句變成同一個原形，
 * 保量測試就永遠只有 1 題）。
 */
private val NAMES = listOf(
    "amber", "blaze", "cider", "dune", "ember", "frost", "glow", "haze",
    "iris", "jade", "karma", "lunar", "mist", "nova", "onyx", "prism",
    "quartz", "raven", "sable", "tide", "umber", "vivid", "wisp", "yarn",
    "zephyr", "basalt", "coral", "drift", "echo", "flint", "garnet", "husk",
    "ivory", "jasper", "kite", "loom", "moss", "nectar", "opal", "pearl",
)

class BlankSelectorTest {

    /** 可控的等級來源。 */
    private class FakeSource(
        private val levels: Map<String, CefrLevel?>,
        private val ranks: Map<String, Int> = emptyMap(),
        /** 字典裡只有大寫形式（專有名詞） */
        private val properNouns: Set<String> = emptySet(),
        /**
         * 要當成「功能詞」的字。
         *
         * 真實的 `CommonWordFilter` 有幾百個，假資料只有幾個字；若在測試句裡
         * 寫 the/of/and 又不補這份清單，它們會被當成實詞而選中。
         */
        private val functionWords: Set<String> = emptySet(),
    ) : WordLevelSource {
        override fun lookup(word: String): WordLevel {
            val key = word.lowercase()
            // 沒有登記的字視為「未收錄」＝最高階。測試句裡常見的填充字
            // （sky / night / tonight / again）必須明確標成常見字，
            // 否則它們會以 C2 的身分搶走挖空。
            val registered = levels.containsKey(key)
            val level = if (registered) levels[key] else FILLER_LEVEL
            val rank = if (registered) ranks[key] else FILLER_RANK
            return WordLevel(
                surface = key,
                lemma = key,
                level = level,
                source = if (level == null) UNLISTED_SOURCE else "CEFR-J",
                frequencyRank = rank,
                surfaceRank = rank,
                isProperNoun = key in properNouns,
            )
        }

        override fun isHighFrequency(word: String) = (ranks[word.lowercase()] ?: Int.MAX_VALUE) <= 500

        /**
         * 測試資料裡被當成功能詞的字。
         *
         * ⚠️ `BlankSelector` 真正的功能詞判定走 `CommonWordFilter`，
         * 所以測試句裡要寫清單上的字（the / of / and…）才會被排除；
         * 這份清單只是讓「整句都是虛詞」的測試能自己指定語料。
         */
        fun isFunctionWord(word: String) = word.lowercase() in functionWords
    }

    private fun lines(vararg texts: String): List<LyricLine> =
        texts.mapIndexed { index, text -> LyricLine(index = index, text = text, startMs = index * 1000L) }

    private fun select(
        lines: List<LyricLine>,
        source: WordLevelSource,
        difficulty: BlankDifficulty = BlankDifficulty.ADVANCED,
        maxBlanks: Int = BlankSelector.MAX_BLANKS,
    ) = BlankSelector.select(lines, difficulty, maxBlanks, source)

    @Test
    fun `功能詞永遠不挖`() {
        // 這句全部都是功能詞 → 硬性排除後沒有候選
        val source = FakeSource(emptyMap())
        val result = select(lines("the of and to you"), source)

        assertNull(result.single().chosen)
        assertTrue(result.single().rejected.isNotEmpty())
    }

    @Test
    fun `專有名詞不挖——歌詞裡永遠大寫且不在句首`() {
        // 字典查得到小寫，但歌詞每次都寫 Vegas、而且從不在句首 → 仍是專有名詞
        val source = FakeSource(
            levels = mapOf("vegas" to CefrLevel.C2, "velvet" to CefrLevel.B1),
        )
        val result = select(lines("velvet into Vegas tonight"), source)

        assertEquals("velvet", result.single().chosen?.word?.lowercase())
        assertTrue(
            "Vegas 應該被當成專有名詞排除，實際 rejected=${result.single().rejected}",
            result.single().rejected.any { it.word == "Vegas" && it.levelSource == "專有名詞" },
        )
    }

    @Test
    fun `句首大寫不算專有名詞`() {
        // "Hello" 在句首被大寫，但第二句是小寫 → 不是專有名詞，可以挖
        val source = FakeSource(levels = mapOf("hello" to CefrLevel.A1))
        val result = select(lines("Hello there", "say hello again"), source)

        // 兩句都只有 hello 是可挖的實詞；第一句會先被選中（均勻取樣含第一句）
        assertTrue(result.any { it.chosen?.word?.lowercase() == "hello" })
        assertTrue(result.first().rejected.none { it.levelSource == "專有名詞" })
    }

    @Test
    fun `高頻詞是降權不是排除`() {
        // silhouette 在 BNC 538（> 500，不算硬性排除）；wondering 在 444（前 500 → 硬性排除）
        val source = FakeSource(
            levels = mapOf("silhouette" to CefrLevel.A2, "wondering" to CefrLevel.A1),
            ranks = mapOf("silhouette" to 538, "wondering" to 444),
        )
        val result = select(lines("wondering silhouette"), source)

        // 兩個都不是功能詞，但 wondering 落在前 500 → 硬性排除；silhouette 仍可挖
        assertEquals("silhouette", result.single().chosen?.word?.lowercase())
    }

    @Test
    fun `只有前 500 名被硬性排除`() {
        val source = FakeSource(
            levels = mapOf("hello" to CefrLevel.A1, "love" to CefrLevel.A1),
            ranks = mapOf("hello" to 2319, "love" to 670),
        )
        // hello 不在前 500 → 可以挖（這是上一版挖不到的關鍵差異）
        assertEquals("hello", select(lines("hello"), source).single().chosen?.word)
        // love 在 670 也不在前 500 → 也可以挖
        assertEquals("love", select(lines("love"), source).single().chosen?.word)
    }

    @Test
    fun `專有名詞不挖——字典只有大寫形式`() {
        val source = FakeSource(
            levels = mapOf("california" to CefrLevel.C1, "velvet" to CefrLevel.B2),
            properNouns = setOf("california"),
        )
        val result = select(lines("velvet in california"), source)

        assertEquals("velvet", result.single().chosen?.word)
        assertTrue(result.single().rejected.any { it.word == "california" && it.levelSource == "專有名詞" })
    }

    fun `等級高者優先`() {
        val source = FakeSource(
            levels = mapOf("lantern" to CefrLevel.A2, "velvet" to CefrLevel.B2),
        )
        assertEquals(
            "velvet",
            select(lines("lantern velvet"), source).single().chosen?.word,
        )
    }

    @Test
    fun `同級取罕見`() {
        val source = FakeSource(
            levels = mapOf("velvet" to CefrLevel.B2, "silhouette" to CefrLevel.B2),
            ranks = mapOf("velvet" to 6159, "silhouette" to 11637),
        )
        val result = select(lines("velvet silhouette"), source)
        assertEquals("silhouette", result.single().chosen?.word)
        assertTrue("依據要說得出是分數決定的：${result.single().basis}", result.single().basis!!.contains("分數"))
    }

    @Test
    fun `跨句不重複用同一個原形`() {
        val source = FakeSource(levels = mapOf("velvet" to CefrLevel.B2))
        val result = select(lines("velvet light", "velvet"), source)

        assertEquals(listOf("velvet"), result.mapNotNull { it.chosen?.word })
        assertNull(result[1].chosen)
        assertTrue(result[1].basis!!.contains("挖過"))
    }

    @Test
    fun `難度只換字不換題數（保量政策）`() {
        // ⚠️ 行為契約（2026-10 擁有者核定）：
        // 題數由 BlankCountPolicy 決定（下限 20、句子多時 0.6 倍），
        // **難度不影響題數**——難度決定「哪些字值得挖」與「補題先補誰」。
        val base = lines("happy velvet", "happy daylight", "velvet lantern")
        val source = FakeSource(
            levels = mapOf(
                "happy" to CefrLevel.A1,
                "velvet" to CefrLevel.B2,
                "lantern" to CefrLevel.A2,
                "daylight" to CefrLevel.B1,
            ),
        )

        val results = BlankDifficulty.entries.map { difficulty ->
            difficulty to select(base, source, difficulty).filter { it.chosen != null }
        }

        val counts = results.map { it.second.size }.toSet()
        assertEquals("三種難度的題數必須相同，實際 ${results.map { it.first to it.second.size }}", 1, counts.size)

        // 但選中的字會不同：挑戰模式不會挖 happy（A1）
        val challenge = results.first { it.first == BlankDifficulty.CHALLENGE }.second
        assertTrue(
            "挑戰模式不該挖 happy：${challenge.mapNotNull { it.chosen?.word }}",
            challenge.none { it.chosen?.word == "happy" },
        )
    }

    @Test
    fun `保量：合格句夠多時至少 20 題`() {
        // 24 個相異實詞、每個一句 → 24 個合格句 → 目標 max(20, 14) = 20
        val source = FakeSource(NAMES.take(24).associateWith { CefrLevel.B2 })
        val twentyFour = lines(*NAMES.take(24).map { "$it here" }.toTypedArray())

        val result = select(twentyFour, source, BlankDifficulty.ADVANCED)

        assertEquals(20, result.count { it.chosen != null })
    }

    @Test
    fun `保量：句子多時按比例拉高`() {
        val source = FakeSource(NAMES.associateWith { CefrLevel.B2 })
        val forty = lines(*NAMES.map { "$it here" }.toTypedArray())

        val result = select(forty, source, BlankDifficulty.ADVANCED, maxBlanks = 40)

        assertEquals("40 × 0.6 = 24", 24, result.count { it.chosen != null })
    }

    @Test
    fun `均勻選句不會擠在同一段`() {
        val words = listOf(
            "amber", "blaze", "cider", "dune", "ember", "frost", "glow", "haze",
            "iris", "jade", "karma", "lunar", "mist", "nova", "onyx", "prism",
            "quartz", "raven", "sable", "tide",
        )
        val source = FakeSource(words.associateWith { CefrLevel.B2 })
        val twenty = lines(*words.map { "$it here" }.toTypedArray())

        val result = select(twenty, source, maxBlanks = 5)
        val picked = result.filter { it.chosen != null }.map { it.line.index }

        assertEquals(5, picked.size)
        assertTrue("第一個選到的最前面幾句內：$picked", picked.first() <= 2)
        assertTrue("最後一個選到的最後幾句內：$picked", picked.last() >= 17)
    }

    @Test
    fun `題數上限 40`() {
        assertEquals(40, BlankSelector.MAX_BLANKS)
        val words = (1..60).map { "word$it" }
        val indices = (0 until 60).toList()
        assertEquals(40, BlankSelector.pickEvenly(indices, BlankSelector.MAX_BLANKS).size)
        assertTrue(words.isNotEmpty())
    }
}
