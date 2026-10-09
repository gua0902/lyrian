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
 * 注意：它**不提供字義**（`definitions` 實測皆為空），字義由 [WiktionaryApi] 補。
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
