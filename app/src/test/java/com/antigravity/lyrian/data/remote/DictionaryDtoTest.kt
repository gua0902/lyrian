package com.antigravity.lyrian.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 字典 DTO 的**反序列化測試**。
 *
 * ## 這裡守著一個真實的錯誤假設
 *
 * 先前的 `DictionaryService` 註解寫著：
 *
 * > FreeDictionaryApi **不提供定義**（實測 `definitions` 全為空）
 *
 * 因此字義改用 Wiktionary 補。**這個結論是錯的**——
 * 這個 API 根本沒有 `definitions` 欄位，字義在 `entries[].senses[].definition`。
 * 我們查了一個不存在的欄位、得到 null，就推論「它沒有字義」，
 * 結果字庫永遠顯示「查不到字典資料」。
 *
 * 這個測試用**真實抓下來的 JSON**（2026-10 實測）驗證欄位對得上，
 * 這樣就不會再靠記憶猜測 API 的形狀。
 */
class DictionaryDtoTest {

    /** 與 `NetworkModule.provideJson()` 相同的設定。 */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * freedictionaryapi.com `/api/v1/entries/en/hope` 的**真實回應**（節錄）。
     *
     * 刻意保留 `forms`、`source`、額外的 entry 等我們沒用到的欄位，
     * 驗證 `ignoreUnknownKeys` 真的有效。
     */
    private val hopeResponse = """
    {
      "word": "hope",
      "entries": [
        {
          "language": "English",
          "partOfSpeech": "verb",
          "pronunciations": [
            { "type": "ipa", "text": "/ˈhəʊ̯p/", "tags": ["Received Pronunciation"] },
            { "type": "ipa", "text": "[ˈhəʊ̯p]", "tags": ["Received Pronunciation"] },
            { "type": "ipa", "text": "/ˈhoʊ̯p/", "tags": ["General American"] }
          ],
          "forms": [ { "word": "hoped", "tags": ["past"] } ],
          "senses": [
            { "definition": "To want (something) to happen, with a sense of expectation that it might [with that (+ clause)]", "tags": [] },
            { "definition": "(catenative) To intend to do something", "tags": ["catenative"] }
          ],
          "synonyms": [ "wish" ]
        },
        {
          "language": "English",
          "partOfSpeech": "noun",
          "pronunciations": [],
          "senses": [
            { "definition": "The feeling of trust, confidence, belief or expectation", "tags": [] }
          ]
        }
      ],
      "source": { "url": "https://en.wiktionary.org/wiki/hope" }
    }
    """.trimIndent()

    @Test
    fun `可以解析真實的 freedictionaryapi 回應`() {
        val dto = json.decodeFromString<FreeDictionaryResponseDto>(hopeResponse)

        assertEquals("hope", dto.word)
        assertEquals(2, dto.entries.size)
    }

    @Test
    fun `字義在 senses 而不是 definitions`() {
        val dto = json.decodeFromString<FreeDictionaryResponseDto>(hopeResponse)
        val first = dto.entries.first()

        assertEquals("verb", first.partOfSpeech)
        assertEquals(2, first.senses.size)
        assertEquals(
            "To want (something) to happen, with a sense of expectation that it might [with that (+ clause)]",
            first.senses[0].definition,
        )
    }

    @Test
    fun `音標的腔調標籤可以解析`() {
        val dto = json.decodeFromString<FreeDictionaryResponseDto>(hopeResponse)
        val pronunciations = dto.entries.first().pronunciations

        assertEquals(3, pronunciations.size)

        val british = pronunciations.first { "Received Pronunciation" in it.tags }
        val american = pronunciations.first { "General American" in it.tags }

        assertTrue(british.text!!.startsWith("/"))
        assertEquals("/ˈhoʊ̯p/", american.text)
    }

    @Test
    fun `未知欄位不會讓解析失敗`() {
        // forms / synonyms / source / language 都是我們沒宣告的欄位
        val dto = json.decodeFromString<FreeDictionaryResponseDto>(hopeResponse)
        assertNotNull(dto.entries.first().senses)
    }

    @Test
    fun `缺少 senses 的條目會得到空清單而非崩潰`() {
        val minimal = """{"word":"x","entries":[{"partOfSpeech":"noun"}]}"""
        val dto = json.decodeFromString<FreeDictionaryResponseDto>(minimal)

        assertTrue(dto.entries.first().senses.isEmpty())
        assertTrue(dto.entries.first().pronunciations.isEmpty())
        assertTrue(dto.entries.first().forms.isEmpty())
    }

    @Test
    fun `完全空的回應也能解析`() {
        val dto = json.decodeFromString<FreeDictionaryResponseDto>("{}")

        assertEquals("", dto.word)
        assertTrue(dto.entries.isEmpty())
    }

    // ---------- Wiktionary 的形狀 ----------

    /**
     * Wiktionary `/api/rest_v1/page/definition/hope` 的真實形狀（節錄）。
     *
     * 最外層是**語言代碼**，不是固定的 `en` 欄位。
     */
    private val wiktionaryResponse = """
    {
      "en": [
        {
          "partOfSpeech": "Verb",
          "language": "English",
          "definitions": [
            { "definition": "To <a rel=\"mw:WikiLink\" href=\"/wiki/want\">want</a> something to happen" }
          ]
        }
      ],
      "nl": [ { "partOfSpeech": "Noun", "language": "Dutch", "definitions": [] } ]
    }
    """.trimIndent()

    @Test
    fun `wiktionary 的最外層是語言代碼`() {
        val map = json.decodeFromString<Map<String, List<WiktionaryEntryDto>>>(wiktionaryResponse)

        assertTrue(map.containsKey("en"))
        assertTrue(map.containsKey("nl"))
        assertEquals("Verb", map["en"]!!.first().partOfSpeech)
    }

    @Test
    fun `wiktionary 的定義含有 HTML 標籤`() {
        val map = json.decodeFromString<Map<String, List<WiktionaryEntryDto>>>(wiktionaryResponse)
        val definition = map["en"]!!.first().definitions.first().definition!!

        // 這正是為什麼 Wiktionary 的字義需要清洗，而 freedictionaryapi 不用
        assertTrue(definition.contains("<a "))
    }
}
