package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lexicon.LexiconEntry
import com.antigravity.lyrian.data.lexicon.LexiconSense
import com.antigravity.lyrian.data.lexicon.LexiconSource
import com.antigravity.lyrian.data.lexicon.PartOfSpeech
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.LyricLine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新選詞邏輯的驗收測試（對應擁有者給的六條驗收標準）。
 *
 * 用**假詞典**而不是真資料：這裡驗的是規則本身，不該被
 * 「某個字剛好在 CEFR-J 哪一級」影響。
 */
class BlankPickerTest {

    private class FakeLexicon(private val entries: Map<String, LexiconEntry>) : LexiconSource {
        override suspend fun lookup(normalized: String): LexiconEntry? = entries[normalized]
    }

    private fun entry(
        word: String,
        cefr: CefrLevel?,
        rank: Int? = 10_000,
        pos: Set<PartOfSpeech> = setOf(PartOfSpeech.NOUN),
        lemma: String = word,
    ) = LexiconEntry(
        headword = word,
        lemma = lemma,
        cefr = cefr,
        frequencyRank = rank,
        partsOfSpeech = pos,
        gloss = "釋義",
    )

    private fun line(index: Int, text: String) =
        LyricLine(index = index, text = text, startMs = index * 1_000L)

    @Test
    fun `A2 及以下永遠不挖`() = runTest {
        val lexicon = FakeLexicon(
            mapOf(
                "simple" to entry("simple", CefrLevel.A2),
                "harder" to entry("harder", CefrLevel.B1),
            ),
        )
        val result = BlankPicker.pick(listOf(line(0, "simple harder")), lexicon)

        assertEquals(listOf("harder"), result.blanks.map { it.answer })
        assertTrue(result.rejected.any { it.word == "simple" && it.reason.contains("A2") })
    }

    @Test
    fun `同一個原形只挖一次`() = runTest {
        val lexicon = FakeLexicon(mapOf("dream" to entry("dream", CefrLevel.B2)))
        val lines = listOf(
            line(0, "dream dream dream"),
            line(1, "dream again"),
        )

        val result = BlankPicker.pick(lines, lexicon)

        assertEquals("整首歌只挖一次", 1, result.blanks.size)
    }

    @Test
    fun `詞性 PROPN 一律排除`() = runTest {
        val lexicon = FakeLexicon(
            mapOf(
                "vegas" to entry("vegas", CefrLevel.B2, pos = setOf(PartOfSpeech.PROPN)),
                "velvet" to entry("velvet", CefrLevel.B2),
            ),
        )

        val result = BlankPicker.pick(listOf(line(0, "velvet in vegas")), lexicon)

        assertEquals(listOf("velvet"), result.blanks.map { it.answer })
        assertTrue(result.rejected.any { it.word == "vegas" && it.reason.contains("PROPN") })
    }

    @Test
    fun `非句首的大寫視為專名嫌疑`() = runTest {
        val lexicon = FakeLexicon(
            mapOf(
                "vegas" to entry("vegas", CefrLevel.B2),
                "velvet" to entry("velvet", CefrLevel.B2),
            ),
        )

        val result = BlankPicker.pick(listOf(line(0, "velvet into Vegas")), lexicon)

        assertEquals(listOf("velvet"), result.blanks.map { it.answer })
        assertTrue(result.rejected.any { it.word == "vegas" && it.reason.contains("大寫") })
    }

    @Test
    fun `詞典查無此詞則排除`() = runTest {
        val lexicon = FakeLexicon(mapOf("velvet" to entry("velvet", CefrLevel.B2)))
        val result = BlankPicker.pick(listOf(line(0, "skrrt velvet")), lexicon)

        assertEquals(listOf("velvet"), result.blanks.map { it.answer })
        assertTrue(result.rejected.any { it.word == "skrrt" && it.reason.contains("查無此詞") })
    }

    @Test
    fun `每句最多兩個空`() = runTest {
        val lexicon = FakeLexicon(
            listOf("alpha", "bravo", "charlie").associateWith {
                entry(it, CefrLevel.C1, rank = 20_000)
            },
        )

        val result = BlankPicker.pick(listOf(line(0, "alpha bravo charlie")), lexicon)

        assertEquals(2, result.blanks.size)
        assertEquals(1, result.shortfall.let { if (it > 0) 1 else 0 })
    }

    @Test
    fun `候選不足時如實回報 shortfall 而不補簡單詞`() = runTest {
        val lexicon = FakeLexicon(
            mapOf(
                "velvet" to entry("velvet", CefrLevel.B1),
                "simple" to entry("simple", CefrLevel.A1),
            ),
        )
        val lines = (0..30).map { line(it, "simple velvet") }

        val result = BlankPicker.pick(lines, lexicon)

        assertEquals(1, result.blanks.size) // 只有 velvet；simple 是 A1
        assertEquals(BlankPicker.TARGET_BLANKS - 1, result.shortfall)
    }

    @Test
    fun `全域排序：越罕見越前`() = runTest {
        val lexicon = FakeLexicon(
            mapOf(
                "common" to entry("common", CefrLevel.B2, rank = 3_000),
                "rare" to entry("rare", CefrLevel.B1, rank = 25_000),
            ),
        )

        val result = BlankPicker.pick(listOf(line(0, "common"), line(1, "rare")), lexicon)

        assertEquals(listOf("rare", "common"), result.pool.map { it.lemma })
    }

    @Test
    fun `沒有詞頻的候選排在後面`() = runTest {
        val lexicon = FakeLexicon(
            mapOf(
                "nofreq" to entry("nofreq", CefrLevel.C2, rank = null),
                "ranked" to entry("ranked", CefrLevel.B1, rank = 20_000),
            ),
        )

        val result = BlankPicker.pick(listOf(line(0, "nofreq ranked")), lexicon)

        assertEquals(listOf("ranked", "nofreq"), result.pool.map { it.lemma })
    }

    @Test
    fun `填充音在查詞典之前就被剃除`() = runTest {
        // 舊版把 ahh 查到 ECDICT 的 AHH（芳基烴羥化酶）→ 變成 B1+ 候選
        val lexicon = FakeLexicon(
            mapOf(
                "ahh" to entry("ahh", CefrLevel.B2),
                "platinum" to entry("platinum", CefrLevel.B2),
            ),
        )

        val result = BlankPicker.pick(listOf(line(0, "ahhh platinum")), lexicon)

        assertEquals(listOf("platinum"), result.blanks.map { it.answer })
        assertTrue(result.rejected.any { it.word == "ahhh" && it.reason.contains("填充音") })
    }

    @Test
    fun `三個字母以下不進候選池`() = runTest {
        val lexicon = FakeLexicon(
            mapOf("yacht" to entry("yacht", CefrLevel.B2), "deed" to entry("deed", CefrLevel.B2)),
        )

        val result = BlankPicker.pick(listOf(line(0, "ahs yacht deed")), lexicon)

        assertEquals(listOf("yacht", "deed"), result.blanks.map { it.answer })
    }

    @Test
    fun `多義項的詞性與釋義都要保留`() {
        val entry = LexiconEntry(
            headword = "trim",
            lemma = "trim",
            cefr = CefrLevel.B1,
            frequencyRank = 8_000,
            partsOfSpeech = setOf(PartOfSpeech.VERB, PartOfSpeech.NOUN),
            gloss = "修剪",
            senses = listOf(
                LexiconSense(PartOfSpeech.VERB, "修剪"),
                LexiconSense(PartOfSpeech.NOUN, "整齊"),
            ),
        )

        assertEquals("v. · n.", entry.displayPos)
        assertEquals("v. 修剪；n. 整齊", entry.displayGloss)
    }

    @Test
    fun `釋義最多顯示兩個義項`() {
        val entry = LexiconEntry(
            headword = "x",
            lemma = "x",
            cefr = CefrLevel.B2,
            frequencyRank = 20_000,
            partsOfSpeech = setOf(PartOfSpeech.NOUN),
            gloss = null,
            senses = listOf(
                LexiconSense(PartOfSpeech.NOUN, "一"),
                LexiconSense(PartOfSpeech.NOUN, "二"),
                LexiconSense(PartOfSpeech.NOUN, "三"),
            ),
        )

        assertEquals("n. 一；n. 二", entry.displayGloss)
    }

    @Test
    fun `答案使用正規化形式`() = runTest {
        val lexicon = FakeLexicon(mapOf("cafe" to entry("cafe", CefrLevel.B1)))
        val result = BlankPicker.pick(listOf(line(0, "the café is open")), lexicon)

        val blank = result.blanks.singleOrNull()
        assertNotNull(blank)
        assertEquals("cafe", blank!!.answer)
        assertEquals("café", blank.surface)
    }

    @Test
    fun `同一首歌的重複出現只產生一個候選`() = runTest {
        val lexicon = FakeLexicon(mapOf("dream" to entry("dream", CefrLevel.B2)))
        val lines = listOf(line(0, "dream"), line(1, "dreaming"))

        val result = BlankPicker.pick(lines, lexicon)

        assertFalse(result.pool.isEmpty())
        assertEquals(1, result.blanks.size)
    }
}
