package com.antigravity.lyrian.data.lang

import com.antigravity.lyrian.domain.model.CefrLevel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 離線語言資料的測試，**直接讀 App 真正使用的那三份資產**。
 *
 * 這是刻意的：資料是產生物（`tools/build_assets.py`），若測試自己造一份小資料，
 * 就無法發現「產生器把 `years` 的詞形還原成 `s`」這種事——那正是實際發生過的 bug。
 */
class LanguageDataTest {

    private fun load(): LanguageData {
        val root = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.isDirectory }
        return LanguageData.load { path -> File(root, path).readText() }
    }

    private val data by lazy { load() }

    @Test
    fun `CEFR-J 的 A1 字`() {
        val hope = data.lookup("hope")
        assertEquals(CefrLevel.A1, hope.level)
        assertEquals("CEFR-J", hope.source)
    }

    @Test
    fun `Octanove 補上的 C1 字`() {
        val silhouette = data.lookup("silhouette")
        assertEquals(CefrLevel.C1, silhouette.level)
        assertEquals("Octanove C1/C2", silhouette.source)
    }

    @Test
    fun `詞形還原會找回原形的等級`() {
        val years = data.lookup("years")
        assertEquals("year", years.lemma)
        assertEquals(CefrLevel.A1, years.level)
    }

    @Test
    fun `推估的等級不會蓋掉原形的真等級`() {
        // met 自己有詞頻（會被推估成 C2），但原形 meet 在 CEFR-J 有真等級。
        // 這裡守的是「不可把使用者早就會的字當成高階字挖出來」。
        val met = data.lookup("met")
        assertEquals("meet", met.lemma)
        assertNotNull(met.level)
        assertTrue("推估等級不該蓋掉真資料，實際=${met.level}", met.level != CefrLevel.C2)
    }

    @Test
    fun `大小寫與彎引號都正規化`() {
        assertEquals("abandon", data.lookup("ABANDONED").lemma)
        assertEquals(CefrLevel.B2, data.lookup("Abandoned").level)
    }

    @Test
    fun `詞頻判斷會看原形`() {
        assertTrue(data.isHighFrequency("time"))
        assertTrue("years 的原形 year 是高頻詞，所以 years 也算", data.isHighFrequency("years"))
        assertFalse(data.isHighFrequency("silhouette"))
    }

    @Test
    fun `資料規模沒有縮水`() {
        // 低於這個數量通常代表產生器壞了（例如 CSV 只讀到一半）
        assertTrue("levelCount=${data.levelCount}", data.levelCount > 20_000)
        assertTrue("lemmaCount=${data.lemmaCount}", data.lemmaCount > 20_000)
    }
}
