package com.antigravity.lyrian.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 字典資產的解析測試（模組 E / F）。
 *
 * 這裡直接餵**真實資產裡的原始字串**，包括：
 *  - `p:passed/d:passed/i:passing/3:passes`（ECDICT 的 exchange 格式）
 *  - `n. 希望, 信心, 期待\nv. 希望, 期望, 信賴`（一行一個詞性）
 *  - `[醫] 懷鄉病`（領域標註）
 */
class DictionaryParsingTest {

    @Test
    fun `詞形變化依 ECDICT 的鍵位解析`() {
        val forms = DictionaryRepository.parseForms("p:passed/d:passed/i:passing/3:passes/s:passes")

        assertEquals(
            listOf("過去式 passed", "過去分詞 passed", "現在分詞 passing", "第三人稱單數 passes", "複數 passes"),
            forms.map { "${it.label} ${it.word}" },
        )
    }

    @Test
    fun `形容詞的比較級與最高級`() {
        val forms = DictionaryRepository.parseForms("r:happier/t:happiest")
        assertEquals(listOf("比較級 happier", "最高級 happiest"), forms.map { "${it.label} ${it.word}" })
    }

    @Test
    fun `原形與變化類型的鍵位不是詞形`() {
        // `0:` 是原形、`1:` 是「這個字自己是什麼變化」，兩者都不該出現在詞形列表
        val forms = DictionaryRepository.parseForms("0:abandon/1:dp/p:abandoned/d:abandoned")
        assertEquals(listOf("過去式 abandoned", "過去分詞 abandoned"), forms.map { "${it.label} ${it.word}" })
    }

    @Test
    fun `空字串沒有詞形`() {
        assertTrue(DictionaryRepository.parseForms("").isEmpty())
    }

    @Test
    fun `釋義按詞性分組`() {
        val senses = DictionaryRepository.parseSenses("n. 希望, 信心\nv. 希望, 期望")

        assertEquals(2, senses.size)
        assertEquals("n.", senses[0].partOfSpeech)
        assertEquals("希望, 信心", senses[0].text)
        assertEquals("v.", senses[1].partOfSpeech)
    }

    @Test
    fun `領域標註抽出來不混在句子裡`() {
        val senses = DictionaryRepository.parseSenses("n. 糖果, 五彩紙屑\n[醫] 懷鄉病")

        assertEquals(2, senses.size)
        assertEquals(listOf("醫"), senses[1].domains)
        assertEquals("懷鄉病", senses[1].text)
    }

    @Test
    fun `沒有詞性前綴時仍保留釋義`() {
        val senses = DictionaryRepository.parseSenses("just a plain gloss")
        assertEquals(1, senses.size)
        assertEquals(null, senses[0].partOfSpeech)
        assertEquals("just a plain gloss", senses[0].text)
    }
}
