package com.antigravity.lyrian.data.dictionary

import com.antigravity.lyrian.data.remote.FreeDictionaryApi
import com.antigravity.lyrian.data.remote.FreeDictionaryEntryDto
import com.antigravity.lyrian.data.remote.FreeDictionaryResponseDto
import com.antigravity.lyrian.data.remote.WiktionaryApi
import com.antigravity.lyrian.data.remote.WiktionaryEntryDto
import com.antigravity.lyrian.data.text.ChineseConverter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 單字的字典資料。
 *
 * 依擁有者指定的字庫欄位：**單字、詞性（英文縮寫）、字義、英式或美式發音（擇一）**。
 */
data class DictionaryEntry(
    val word: String,
    /** 英文縮寫，例如 `n.` / `v.` / `adj.` */
    val partOfSpeech: String?,
    /** 字義。freedictionaryapi 為英文原文；接上 ECDICT 後會改為中文 */
    val definition: String?,
    /** 國際音標，例如 `/həʊp/` */
    val phonetic: String?,
    /** 音標的腔調：`英` 或 `美` */
    val phoneticAccent: String?,
) {
    val hasContent: Boolean
        get() = !definition.isNullOrBlank() || !phonetic.isNullOrBlank()
}

/**
 * 字典查詢。
 *
 * ## 兩個來源，但**主從分明**（2026-10 實測修正）
 *
 * | 需求 | 主要來源 | 退路 |
 * |---|---|---|
 * | 音標（英式／美式擇一） | [FreeDictionaryApi] | — |
 * | 字義 | [FreeDictionaryApi] 的 `senses[].definition` | [WiktionaryApi] |
 *
 * ### ⚠️ 先前的錯誤假設
 *
 * 原本的註解寫著「FreeDictionaryApi **不提供定義**（實測 `definitions` 全為空）」，
 * 因此字義改用 Wiktionary。**這個結論是錯的**——那個 API 根本沒有 `definitions` 欄位，
 * 字義在 `entries[].senses[].definition`。我們查了一個不存在的欄位，得到 null，
 * 就推論「它沒有字義」。
 *
 * 而且它的字義是**已清乾淨的純文字**，不像 Wiktionary 需要剝 HTML 與 CSS。
 * 所以現在優先用它，Wiktionary 只在它沒資料時接手。
 *
 * 兩者都免金鑰、免註冊。查詢結果由 `WordbankViewModel` 快取到 Room，
 * 因此同一個字不會重複連外。
 */
@Singleton
class DictionaryService @Inject constructor(
    private val freeDictionaryApi: FreeDictionaryApi,
    private val wiktionaryApi: WiktionaryApi,
    private val chineseConverter: ChineseConverter,
) {

    /**
     * 查一個字。
     *
     * **兩個來源並行查詢**（`async`），因為它們互不相依——
     * 循序查會讓每次查詢多等一個 RTT。任一來源失敗都不影響另一個。
     *
     * @return 兩個來源都失敗時回傳 null
     */
    suspend fun lookup(word: String): DictionaryEntry? = coroutineScope {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return@coroutineScope null

        val freeDeferred = async {
            runCatching { freeDictionaryApi.lookup(normalized) }.getOrNull()
        }
        val wiktionaryDeferred = async {
            runCatching { wiktionaryApi.definition(normalized) }.getOrNull()
        }

        val free = freeDeferred.await()
        val wiktionary = wiktionaryDeferred.await()

        val pronunciation = free?.toPronunciation()
        val wiktionaryEntry = wiktionary?.toDefinition()

        // 字義優先取 freedictionaryapi（純文字），沒有才退回 Wiktionary（要清洗）
        val definitionFromFree = free?.toDefinition()
        val definition = definitionFromFree
            ?: wiktionaryEntry?.definitions?.firstOrNull()?.definition
                ?.cleanWiktionaryMarkup()
                ?.takeIf { it.isNotBlank() }

        if (pronunciation == null && definition == null) return@coroutineScope null

        // 詞性優先取「字義所屬」的那個來源，這樣詞性與字義才對得起來
        val partOfSpeech = if (definitionFromFree != null) {
            free?.firstEntryWithDefinition()?.partOfSpeech
        } else {
            wiktionaryEntry?.partOfSpeech
        } ?: pronunciation?.partOfSpeech

        DictionaryEntry(
            word = normalized,
            partOfSpeech = partOfSpeech?.toAbbreviation(),
            definition = definition?.let { chineseConverter.toTraditionalIfNeeded(it) },
            phonetic = pronunciation?.phonetic,
            phoneticAccent = pronunciation?.accent,
        )
    }

    /**
     * 從 freedictionaryapi 取第一條字義。
     *
     * 一個詞會有多個 entry（不同詞性），每個 entry 有多個 sense。
     * 取**第一個有字義的 entry 的第一條 sense**——字庫是速查，不是完整詞典。
     */
    private fun FreeDictionaryResponseDto.toDefinition(): String? =
        firstEntryWithDefinition()
            ?.senses
            ?.firstOrNull { !it.definition.isNullOrBlank() }
            ?.definition
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /** 第一個真的有字義的詞性條目。 */
    private fun FreeDictionaryResponseDto.firstEntryWithDefinition(): FreeDictionaryEntryDto? =
        entries.firstOrNull { entry -> entry.senses.any { !it.definition.isNullOrBlank() } }

    /** 從音標 API 的回應挑出**一個**音標（英式優先，其次美式）。 */
    private fun FreeDictionaryResponseDto.toPronunciation(): Pronunciation? {
        val all = entries.flatMap { it.pronunciations }

        // 只取 IPA 且有序的斜線形式（`/.../`），排除語音方括號 `[...]`
        val ipa = all.filter { it.type == "ipa" && it.text?.startsWith("/") == true }
        if (ipa.isEmpty()) return null

        // 擁有者指定「英式/美式擇一」，因此不並列——英式優先
        val british = ipa.firstOrNull { pron ->
            pron.tags.any { it.contains("Received Pronunciation", ignoreCase = true) }
        }
        val american = ipa.firstOrNull { pron ->
            pron.tags.any { it.contains("General American", ignoreCase = true) }
        }

        val chosen = british ?: american ?: ipa.first()
        val accent = when (chosen) {
            british -> "英"
            american -> "美"
            else -> null
        }

        return Pronunciation(
            phonetic = chosen.text,
            accent = accent,
            partOfSpeech = entries.firstOrNull()?.partOfSpeech,
        )
    }

    private data class Pronunciation(
        val phonetic: String?,
        val accent: String?,
        val partOfSpeech: String?,
    )

    /** 從 Wiktionary 的回應取出第一個英文詞性與其第一條定義。 */
    private fun Map<String, List<WiktionaryEntryDto>>.toDefinition(): WiktionaryEntryDto? =
        this["en"]?.firstOrNull { !it.definitions.isNullOrEmpty() }

    /**
     * 把完整詞性轉成英文縮寫。
     *
     * 擁有者指定「詞性（使用英文縮寫）」，因此 `Verb` → `v.`。
     */
    private fun String.toAbbreviation(): String = when (lowercase()) {
        "noun" -> "n."
        "verb" -> "v."
        "adjective" -> "adj."
        "adverb" -> "adv."
        "pronoun" -> "pron."
        "preposition" -> "prep."
        "conjunction" -> "conj."
        "interjection" -> "interj."
        "determiner" -> "det."
        "numeral" -> "num."
        "article" -> "art."
        "particle" -> "part."
        "phrase" -> "phr."
        "abbreviation" -> "abbr."
        else -> this
    }

    /**
     * 清掉 Wiktionary 定義中的 HTML 標籤與樣式殘留。
     *
     * 實測定義會夾帶 `<a>` 連結、`.mw-parser-output .object-usage-tag` 這類 CSS 類別，
     * 以及 `[with to (+ infinitive)]` 這類語法標註——都必須清掉才適合顯示。
     *
     * **順序很重要**：必須先移除 CSS 類別片段，再壓縮空白。
     * 反過來做的話，空白壓縮會把 `.mw-parser-output` 切成 `.object-usage-tag`
     * 這種無法辨識的殘留（實際踩過）。
     */
    private fun String.cleanWiktionaryMarkup(): String =
        this
            // 1. HTML 標籤
            .replace(Regex("""<[^>]+>"""), "")
            // 2. CSS 類別片段：.mw-parser-output 之後的一串非空白字元
            .replace(Regex("""\.\S+"""), "")
            // 3. 語法標註，例如 [with to (+ infinitive)]
            .replace(Regex("""\[[^\]]*]"""), "")
            // 4. 最後才壓縮空白
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim(' ', ',', '.', ';', ':')
            .let { if (it.isNotEmpty()) "$it." else it }
}
