package com.antigravity.lyrian.data.dictionary

import com.antigravity.lyrian.data.remote.FreeDictionaryApi
import com.antigravity.lyrian.data.remote.FreeDictionaryResponseDto
import com.antigravity.lyrian.data.remote.WiktionaryApi
import com.antigravity.lyrian.data.remote.WiktionaryEntryDto
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
    /** 字義。目前是英文（來自 Wiktionary） */
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
 * **為什麼用兩個 API 而不是一個**（皆已實測，2026-10）：
 *
 * | 需求 | 來源 | 原因 |
 * |---|---|---|
 * | 音標（英式／美式） | [FreeDictionaryApi] | 唯一提供 `Received Pronunciation`（英）與 `General American`（美）區分的免費來源 |
 * | 字義 | [WiktionaryApi] | FreeDictionaryApi **不提供定義**（實測 `definitions` 全為空）|
 *
 * 兩者都免金鑰、免註冊。查詢結果由 [DictionaryRepository] 快取到 Room，
 * 因此同一個字只會連外一次。
 */
@Singleton
class DictionaryService @Inject constructor(
    private val freeDictionaryApi: FreeDictionaryApi,
    private val wiktionaryApi: WiktionaryApi,
) {

    /**
     * 查一個字。
     *
     * **兩個來源並行查詢**（`async`），因為它們互不相依——
     * 循序查會讓每次查詢多等一個 RTT。
     *
     * 任一來源失敗都不影響另一個；兩者都失敗才回傳 null。
     */
    suspend fun lookup(word: String): DictionaryEntry? = coroutineScope {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return@coroutineScope null

        val pronunciationDeferred = async {
            runCatching { freeDictionaryApi.lookup(normalized).toPronunciation() }.getOrNull()
        }
        val definitionDeferred = async {
            runCatching { wiktionaryApi.definition(normalized).toDefinition() }.getOrNull()
        }
        val pronunciation = pronunciationDeferred.await()
        val definition = definitionDeferred.await()

        if (pronunciation == null && definition == null) return@coroutineScope null

        // 詞性優先取定義來源的（它與字義對應；音標來源的詞性可能有多個重複條目）
        val partOfSpeech = definition?.partOfSpeech ?: pronunciation?.partOfSpeech

        DictionaryEntry(
            word = normalized,
            partOfSpeech = partOfSpeech?.toAbbreviation(),
            // 取第一條定義即可——字庫是速查，不是完整詞典
            definition = definition?.definitions?.firstOrNull()?.definition
                ?.cleanWiktionaryMarkup()
                ?.takeIf { it.isNotBlank() },
            phonetic = pronunciation?.phonetic,
            phoneticAccent = pronunciation?.accent,
        )
    }

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
