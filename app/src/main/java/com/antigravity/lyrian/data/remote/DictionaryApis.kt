package com.antigravity.lyrian.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 免費字典 API（freedictionaryapi.com）。
 *
 * **為什麼用它**：實測是唯一提供**英式／美式音標區分**的免費來源
 * （`Received Pronunciation` / `General American`），正好符合擁有者要的「英式/美式發音擇一」。
 *
 * ## ⚠️ 修正：它**有**提供字義，欄位叫 `senses`
 *
 * 先前的註解寫著「`definitions` 實測皆為空」，因此字義改用 Wiktionary 補——
 * **那是錯的**。這個 API 根本沒有 `definitions` 欄位，字義在 `entries[].senses[].definition`。
 *
 * 而且它的字義是**已清乾淨的純文字**，不像 Wiktionary 夾帶一堆 HTML 與 CSS：
 *
 * ```
 * freedictionaryapi：To want (something) to happen, with a sense of expectation that it might.
 * Wiktionary       ：To <a ...>want</a> ... <style>.mw-parser-output .object-usage-tag{...}</style>...
 * ```
 *
 * 因此現在**優先用這裡的字義**，Wiktionary 只當退路（有些字這裡沒有、那裡有）。
 */
interface FreeDictionaryApi {

    @GET("api/v1/entries/en/{word}")
    suspend fun lookup(@Path("word") word: String): FreeDictionaryResponseDto
}

@Serializable
data class FreeDictionaryResponseDto(
    val word: String = "",
    val entries: List<FreeDictionaryEntryDto> = emptyList(),
)

@Serializable
data class FreeDictionaryEntryDto(
    val partOfSpeech: String? = null,
    val pronunciations: List<FreeDictionaryPronunciationDto> = emptyList(),
    /** 字義。實測一個詞會有多個 entry（不同詞性），每個 entry 有多個 sense。 */
    val senses: List<FreeDictionarySenseDto> = emptyList(),
    /** 詞形變化，例如 `hoped` / `hoping` / `hopes` */
    val forms: List<FreeDictionaryFormDto> = emptyList(),
)

@Serializable
data class FreeDictionarySenseDto(
    /** **已是純文字**，可能含 `[with that (+ clause)]` 這類語法標註 */
    val definition: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class FreeDictionaryFormDto(
    val word: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class FreeDictionaryPronunciationDto(
    /** 實測為 `ipa` */
    val type: String? = null,
    /** 音標本身，例如 `/ˈhəʊ̯p/` */
    val text: String? = null,
    /** 實測為 `Received Pronunciation`（英）或 `General American`（美） */
    val tags: List<String> = emptyList(),
)

/**
 * Wiktionary REST API。
 *
 * **為什麼用它**：免費、免金鑰、定義品質好。
 * 回傳的定義含 HTML 標籤，需清洗（見 `DictionaryService`）。
 */
interface WiktionaryApi {

    @GET("api/rest_v1/page/definition/{word}")
    suspend fun definition(@Path("word") word: String): Map<String, List<WiktionaryEntryDto>>
}

@Serializable
data class WiktionaryEntryDto(
    val partOfSpeech: String? = null,
    val language: String? = null,
    val definitions: List<WiktionaryDefinitionDto> = emptyList(),
)

@Serializable
data class WiktionaryDefinitionDto(
    /** 含 HTML 標籤，例如 `To <a ...>want</a> something to happen` */
    val definition: String? = null,
    val parsedExamples: List<WiktionaryExampleDto> = emptyList(),
)

@Serializable
data class WiktionaryExampleDto(
    val example: String? = null,
)
