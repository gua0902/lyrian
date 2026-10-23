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

/** 一筆字典資料。欄位對應擁有者指定的字庫欄位：單字、詞性、字義、音標。 */
data class DictionaryEntry(
    val word: String,
    /** 詞性的英文縮寫，例如 `n.` / `v.` / `adj.`；可能是 `n./v.` */
    val partOfSpeech: String?,
    /** **繁體中文**字義。 */
    val translation: String?,
    /** 國際音標，例如 `/həup/`。ECDICT 的標音以英式為主。 */
    val phonetic: String?,
    /** CEFR 等級；未收錄時為 null。 */
    val level: CefrLevel?,
    /** 提供這筆資料的形（查 `silhouettes` 時可能是 `silhouette`）。 */
    val matchedForm: String,
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
 * `tools/build_assets.py` 把 **ECDICT**（MIT，含繁體化的中文釋義與 IPA）、
 * **CEFR-J** 與 **Octanove C1/C2** 的等級併成一包，依首字母切成
 * `assets/data/dict/a.json … z.json`。App 只讀自己需要的那一片。
 *
 * ## 為什麼不再連外
 *
 * 舊版查 `freedictionaryapi` + `Wiktionary`：會紅字、會限流、會消失，
 * 而且只給英文釋義。擁有者要的是**繁體中文 + 離線可查**，
 * 那就必須把資料帶在身上。體積約 2.9 MB，換來零網路依賴。
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
     * `met` 在字典裡有自己的條目（釋義是「meet的過去式和過去分詞」），
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

        return DictionaryResult.Found(
            DictionaryEntry(
                word = word,
                partOfSpeech = chosen.p.takeIf { it.isNotBlank() },
                translation = chosen.c.takeIf { it.isNotBlank() },
                phonetic = chosen.i.takeIf { it.isNotBlank() },
                level = levelInfo.level ?: CefrLevel.fromLabel(chosen.l),
                matchedForm = chosen.w,
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
        /** part of speech */
        val p: String = "",
        /** 中文釋義 */
        val c: String = "",
        /** IPA */
        val i: String = "",
        /** CEFR level */
        val l: String = "",
    )

    private companion object {
        const val ASSET_DIR = "data"

        /** 同時保留幾個分片。4 片約等於 4–8 百 KB 的常駐記憶體。 */
        const val SHARD_CACHE_SIZE = 4
    }
}
