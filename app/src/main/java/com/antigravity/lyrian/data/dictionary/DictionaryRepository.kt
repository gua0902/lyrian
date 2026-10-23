package com.antigravity.lyrian.data.dictionary

import android.content.Context
import com.antigravity.lyrian.data.lang.LanguageDataStore
import com.antigravity.lyrian.domain.model.CefrLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一個詞性底下的釋義。 */
data class DictionarySense(
    /** 詞性縮寫，例如 `n.`；沒有標註時為 null */
    val partOfSpeech: String?,
    /** 去掉詞性與領域標註之後的中文釋義 */
    val text: String,
    /** 領域標註，例如 `醫`、`法`、`計`（原始資料寫成 `[醫]`） */
    val domains: List<String> = emptyList(),
)

/** 一個詞形變化，例如「過去式 / passed」。 */
data class DictionaryForm(
    val label: String,
    val word: String,
)

/** 一筆字典資料。欄位對應擁有者指定的字庫欄位：單字、詞性、字義、音標。 */
data class DictionaryEntry(
    val word: String,
    /** 詞性的英文縮寫，例如 `n.` / `v.` / `adj.`；可能是 `n./v.` */
    val partOfSpeech: String?,
    /** **繁體中文**字義的單行摘要（清單列用） */
    val translation: String?,
    /** 國際音標，例如 `/həup/`。ECDICT 的標音以英式為主。 */
    val phonetic: String?,
    /** CEFR 等級；未收錄時為 null。 */
    val level: CefrLevel?,
    /** 提供這筆資料的形（查 `silhouettes` 時可能是 `silhouette`）。 */
    val matchedForm: String,
    /** 依詞性分組的完整釋義（詳情頁用）。 */
    val senses: List<DictionarySense> = emptyList(),
    /** 詞形變化（過去式、複數…）。 */
    val forms: List<DictionaryForm> = emptyList(),
) {
    val hasContent: Boolean get() = !translation.isNullOrBlank() || !phonetic.isNullOrBlank()
}

/** 查詢結果。刻意把「查不到」與「讀取失敗」分開——使用者該做的事不一樣。 */
sealed interface DictionaryResult {
    data class Found(val entry: DictionaryEntry) : DictionaryResult

    /** 字典裡沒有這個字（含詞形還原後仍沒有）。 */
    data object NotFound : DictionaryResult

    /** 資料檔讀取或解析失敗（資產損毀，正常情況不該發生）。 */
    data class Failed(val reason: String) : DictionaryResult
}

/**
 * 離線字典。
 *
 * ## 資料從哪來
 *
 * `tools/build_assets.py` 把 **ECDICT**（MIT，含繁體化的中文釋義、IPA、詞形變化）、
 * **CEFR-J** 與 **Octanove C1/C2** 的等級併成一包，依首字母切成
 * `assets/data/dict/a.json … z.json`。App 只讀自己需要的那一片。
 *
 * ## 為什麼不再連外
 *
 * 舊版查 `freedictionaryapi` + `Wiktionary`：會紅字、會限流、會消失，
 * 而且只給英文釋義。擁有者要的是**繁體中文 + 離線可查**，
 * 那就必須把資料帶在身上。體積約 3.7 MB，換來零網路依賴。
 *
 * ## 分片快取
 *
 * 每個分片解析後是 `Map<String, Entry>`。用 LRU 保留最近 [SHARD_CACHE_SIZE] 片，
 * 因為使用者查的字通常集中在同一兩個字母（同首歌的字首很像）。
 */
@Singleton
class DictionaryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val languageData: LanguageDataStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 已解析的分片，最近使用者在最後。 */
    private val shards = object : LinkedHashMap<String, Map<String, ShardEntry>>(
        SHARD_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, ShardEntry>>): Boolean =
            size > SHARD_CACHE_SIZE
    }

    private val mutex = Mutex()

    /**
     * 查一個字。
     *
     * ## 查詢順序（決定使用者看到哪一筆）
     *
     * ```
     * 1. 原字有自己的 CEFR 等級  → 用原字   （abandoned 是形容詞，有 B2，該看自己）
     * 2. 否則用原形的條目        → 用原形   （met 只是 meet 的過去式，該看 meet）
     * 3. 都沒有                  → 查無此詞
     * ```
     *
     * 之所以要靠「有沒有真實等級」來分辨，是因為詞形還原後仍要決定**顯示哪一筆**：
     * `met` 在字典裡有自己的條目（釋義是「meet 的過去式和過去分詞」），
     * 但對學習者而言 `meet` 的「遇見」才有用。
     */
    suspend fun lookup(query: String): DictionaryResult {
        val word = query.trim().lowercase().replace('’', '\'')
        if (word.isEmpty()) return DictionaryResult.NotFound

        val language = runCatching { languageData.get() }
            .getOrElse { return DictionaryResult.Failed(it.message ?: "語言資料載入失敗") }

        val levelInfo = language.lookup(word)

        val surfaceEntry = runCatching { find(word) }
            .getOrElse { return DictionaryResult.Failed(it.message ?: "字典資料讀取失敗") }
        val lemmaEntry = if (levelInfo.lemma != word) {
            runCatching { find(levelInfo.lemma) }
                .getOrElse { return DictionaryResult.Failed(it.message ?: "字典資料讀取失敗") }
        } else {
            null
        }

        val chosen = when {
            surfaceEntry != null && surfaceEntry.l.isNotBlank() -> surfaceEntry
            lemmaEntry != null -> lemmaEntry
            else -> surfaceEntry
        } ?: return DictionaryResult.NotFound

        val senses = parseSenses(chosen.c)

        return DictionaryResult.Found(
            DictionaryEntry(
                word = word,
                partOfSpeech = chosen.p.takeIf { it.isNotBlank() },
                translation = senses.joinToString("；") { it.text }.take(SUMMARY_MAX_CHARS)
                    .takeIf { it.isNotBlank() },
                phonetic = chosen.i.takeIf { it.isNotBlank() },
                level = levelInfo.level ?: CefrLevel.fromLabel(chosen.l),
                matchedForm = chosen.w,
                senses = senses,
                forms = parseForms(chosen.x),
            ),
        )
    }

    /** 在某個分片裡找一筆。分片不存在時回傳 null（`q` 這類字母沒有分片）。 */
    private suspend fun find(word: String): ShardEntry? {
        val letter = word.firstOrNull()?.toString() ?: return null
        val shard = loadShard(letter) ?: return null
        return shard[word]
    }

    private suspend fun loadShard(letter: String): Map<String, ShardEntry>? {
        shards[letter]?.let { return it }
        return mutex.withLock {
            shards[letter]?.let { return@withLock it }

            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("${ASSET_DIR}/dict/$letter.json")
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrNull()
            } ?: return@withLock null

            val entries = json.decodeFromString<List<ShardEntry>>(text)
            val map = HashMap<String, ShardEntry>(entries.size * 2)
            entries.forEach { map[it.w] = it }
            shards[letter] = map
            map
        }
    }

    /**
     * 一次查多個字（結果頁的兩份清單用）。
     *
     * 逐字查看起來很笨，但每個字都只是一個 HashMap 查詢，
     * 而分片已經在快取裡——真正的成本只有第一次讀分片。
     */
    suspend fun lookupAll(words: List<String>): Map<String, DictionaryEntry> {
        val result = LinkedHashMap<String, DictionaryEntry>(words.size)
        for (word in words) {
            val hit = lookup(word)
            if (hit is DictionaryResult.Found) result[word] = hit.entry
        }
        return result
    }

    /** 分片檔案的 JSON 形狀（鍵名壓到一個字母，省體積）。 */
    @Serializable
    private data class ShardEntry(
        val w: String = "",
        /** part of speech（摘要） */
        val p: String = "",
        /** 完整釋義，一行一個詞性 */
        val c: String = "",
        /** IPA */
        val i: String = "",
        /** CEFR level（只有真資料才填） */
        val l: String = "",
        /** ECDICT 的詞形變化原始字串，例如 `p:passed/i:passing` */
        val x: String = "",
    )

    companion object {
        private const val ASSET_DIR = "data"

        /** 同時保留幾個分片。4 片約等於 4–8 百 KB 的常駐記憶體。 */
        private const val SHARD_CACHE_SIZE = 4

        /** 清單列的釋義摘要長度上限。 */
        private const val SUMMARY_MAX_CHARS = 80

        /** 釋義行開頭的詞性，例如 `n.`、`vt.`、`a.` */
        private val POS_PREFIX = Regex("""^([a-z]+\.)\s*""")

        /** 領域標註，例如 `[醫]`、`[法]`、`[計]`。長度限制避免吃掉整句。 */
        private val DOMAIN_TAG = Regex("""\[([^\[\]]{1,6})]""")

        /**
         * ECDICT 的 `exchange` 鍵 → 中文標籤。
         *
         * ⚠️ 這份對照是**照 ECDICT 的實際資料**做的，不是憑印象：
         * ```
         * be    | p:was/3:is/d:been/i:being      → p 過去式、d 過去分詞、i 現在分詞
         * good  | t:best/r:better                → r 比較級、t 最高級
         * child | s:children                     → s 複數
         * ```
         * （任務書裡把 `p` 寫成複數、`i` 寫成過去分詞、`s` 寫成比較級，
         * 那是另一套字典的鍵位；照著寫會把 `passed` 標成「複數」。）
         *
         * `0:`（原形）與 `1:`（這個字自己是什麼變化）不是詞形，直接跳過。
         */
        private val FORM_LABELS = mapOf(
            "p" to "過去式",
            "d" to "過去分詞",
            "i" to "現在分詞",
            "3" to "第三人稱單數",
            "s" to "複數",
            "r" to "比較級",
            "t" to "最高級",
        )

        /** 顯示順序：動詞變化在前，名詞／形容詞在後。 */
        private val FORM_ORDER = listOf("p", "d", "i", "3", "s", "r", "t")

        /** 把 `c` 欄位（一行一個詞性）拆成結構化的釋義。 */
        internal fun parseSenses(raw: String): List<DictionarySense> {
            if (raw.isBlank()) return emptyList()
            return raw.split('\n')
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@mapNotNull null

                    val match = POS_PREFIX.find(trimmed)
                    val pos = match?.groupValues?.get(1)
                    var body = if (match != null && match.range.first == 0) {
                        trimmed.substring(match.range.last + 1)
                    } else {
                        trimmed
                    }

                    val domains = DOMAIN_TAG.findAll(body)
                        .map { it.groupValues[1].trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                    body = DOMAIN_TAG.replace(body, " ")
                        .replace(Regex("""\s+"""), " ")
                        .trim(' ', ',', '，', '；', ';', '、')

                    if (body.isEmpty()) return@mapNotNull null
                    DictionarySense(partOfSpeech = pos, text = body, domains = domains)
                }
        }

        /**
         * 解析詞形變化字串。
         *
         * 輸入：`p:passed/d:passed/i:passing/3:passes`
         * 輸出：`[過去式 passed, 過去分詞 passed, 現在分詞 passing, 第三人稱單數 passes]`
         */
        internal fun parseForms(raw: String): List<DictionaryForm> {
            if (raw.isBlank()) return emptyList()
            val byKey = LinkedHashMap<String, DictionaryForm>()

            raw.split('/').forEach { part ->
                val colon = part.indexOf(':')
                if (colon <= 0) return@forEach
                val key = part.substring(0, colon).trim()
                val value = part.substring(colon + 1).trim()
                if (key !in FORM_LABELS || value.isEmpty()) return@forEach
                if (byKey.containsKey(key)) return@forEach
                byKey[key] = DictionaryForm(FORM_LABELS.getValue(key), value)
            }

            return FORM_ORDER.mapNotNull { byKey[it] }
        }
    }
}
