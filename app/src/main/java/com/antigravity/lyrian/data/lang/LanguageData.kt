package com.antigravity.lyrian.data.lang

import android.content.Context
import com.antigravity.lyrian.domain.model.CefrLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 一個字（或它的原形）的等級判定結果。
 *
 * @property surface 使用者看到的形（例：`abandoned`）
 * @property lemma 原形（例：`abandon`）；找不到對應時等於 [surface]
 * @property level 等級；null 表示四層資料都沒有（UNKNOWN）
 * @property source 判定來源，直接顯示在偵錯輸出與單字卡上
 * @property frequencyRank BNC 詞頻排名（1 = 最常見），**取不到就退回原形的排名**；null 表示沒資料
 * @property surfaceRank **只有這個形本身**的排名；用來做「詞頻前 500 硬性排除」
 * @property isProperNoun 字典裡只有大寫形式（`California`）→ 永遠不挖
 * @property estimated 等級是**推估**的（考試標籤／詞頻），不是官方 CEFR 清單給的
 */
data class WordLevel(
    val surface: String,
    val lemma: String,
    val level: CefrLevel?,
    val source: String,
    val frequencyRank: Int? = null,
    val surfaceRank: Int? = null,
    val isProperNoun: Boolean = false,
    val estimated: Boolean = false,
    /** 詞頻校正前的等級；等於 [level] 表示沒被校正。報表用。 */
    val rawLevel: CefrLevel? = null,
    /** n. / a. / ad. 這類主要詞性；選詞用它判斷「值不值得學」。 */
    val partOfSpeech: String? = null,
    /**
     * 「這個字到底多常見」用的排名：字面與原形**取較常見的那一個**。
     *
     * dreaming 自己的 BNC 排名是 21,585（因為它是屈折形），但原形 dream 是 1,000 多名
     * ——使用者對這個字的熟悉度是後者。選詞算稀缺度時用這個值，
     * 否則每個 A1 的 -ing 形都會被當成罕見字。
     */
    val commonnessRank: Int? = null,
    /**
     * 這個字的**所有**詞性（已去點、小寫），例如 `["vt","n"]`。
     *
     * [partOfSpeech] 只是第一個。選詞看的是整個集合：`love` 的釋義第一行是
     * `n.`、第二行才是 `v.`，只看第一個會讓它拿不到動詞那一半分數。
     */
    val allPartsOfSpeech: Set<String> = emptySet(),
    /** 等級是否來自官方 CEFR 清單（CEFR-J／Octanove）。 */
    val authoritative: Boolean = false,
) {
    /**
     * 沒有等級資料的字，一律視為比 B2 更難。
     *
     * **但不可以排除它**——查不到等級的通常是生僻實詞（`piercing` 這種），
     * 正是最該練的。`BlankSelector` 會把 UNKNOWN 排在**最前面**。
     */
    val treatAsLevel: CefrLevel get() = level ?: CefrLevel.C2

    /** 四層都查不到等級（不是「沒有這個字」，是「有這個字但沒有等級」）。 */
    val isUnlisted: Boolean get() = level == null

    /** 詞頻顯示用：沒有排名時回報「未收錄」。 */
    val frequencyLabel: String get() = frequencyRank?.let { "BNC $it" } ?: "未收錄"
}

/**
 * 挖空判斷需要的語言資料。
 *
 * 抽成介面是為了讓挖空邏輯保持純函式、可在 JVM 上測試；
 * 正式執行時由 [LanguageData] 提供，測試可傳自己造的實作。
 */
interface WordLevelSource {

    /** 查一個字的等級（含詞形還原）。 */
    fun lookup(word: String): WordLevel

    /** 這個字（或它的原形）是否落在 BNC 前 2000 高頻詞內。 */
    fun isHighFrequency(word: String): Boolean

    /** 沒有任何資料時的退路：所有字都當成「未收錄」。 */
    object None : WordLevelSource {
        override fun lookup(word: String): WordLevel =
            WordLevel(word, word.lowercase(), null, UNLISTED_SOURCE)

        override fun isHighFrequency(word: String): Boolean = false
    }
}

/** 四層資料都沒收錄時顯示的來源字串。 */
const val UNLISTED_SOURCE = "未收錄（視為 B2 以上）"

/** 詞頻推估出來的等級的來源標籤。 */
const val FREQ_SOURCE_LABEL = "詞頻推估"

/** 考試標籤推出來的等級的來源標籤（ECDICT 的 zk/gk/cet4…）。 */
const val TAG_SOURCE_LABEL = "考試標籤"

/** BNC 詞頻排名推出來的等級的來源標籤。 */
const val BNC_SOURCE_LABEL = "詞頻推估"

/**
 * 所有「推估」來源的標籤。
 *
 * 擁有者要求：字庫只寫入**官方 CEFR 清單**給的等級，推估的留空
 * （否則字庫會聲稱一個官方從沒做過的判定，例如 `blah = C2`）。
 */
val ESTIMATED_SOURCE_LABELS = setOf(TAG_SOURCE_LABEL, BNC_SOURCE_LABEL, FREQ_SOURCE_LABEL)

/**
 * 離線語言資料：等級表 + 詞形還原表 + 高頻詞表。
 *
 * ## 為什麼是三個檔案而不是資料庫
 *
 * 這是**唯讀、整份載入、只查不寫**的資料，用 SQLite 只是多一層成本。
 * 三份 TSV 直接解析成 `HashMap` 後，一次查詢就是一次雜湊，沒有 I/O。
 *
 * 體積（由 `tools/build_assets.py` 產生，`--stats` 可重測）：約 1.7 MB。
 *
 * ## 載入時機
 *
 * 解析約 4.7 萬行、需要 50–150 ms，**不可以放在主執行緒**。
 * 因此對外一律透過 [LanguageDataStore.get]（suspend、在 IO 執行緒完成）。
 */
class LanguageData internal constructor(
    private val levels: Map<String, LevelRecord>,
    private val lemmas: Map<String, String>,
    private val frequent: Set<String>,
) : WordLevelSource {

    /**
     * 一筆等級資料。
     *
     * @property level null = `U` 列（四層都查不到，但**這個字存在**）
     * @property sourceCode `j`/`o`/`t`/`b`/`u`
     * @property partsOfSpeech 這個字**所有**詞性（已去點、小寫），例如 `["vt","n"]`
     */
    internal data class LevelRecord(
        val level: CefrLevel?,
        val sourceCode: String,
        val rank: Int,
        val properNoun: Boolean,
        /** 詞頻校正**之前**的等級（報表用；等於 [level] 表示沒被校正）。 */
        val rawLevel: CefrLevel? = level,
        /** 主要詞性；空字串代表資料裡沒有。 */
        val partOfSpeech: String = "",
        /** 這個字所有的詞性；[partOfSpeech] 只是第一個。 */
        val partsOfSpeech: Set<String> = emptySet(),
        /**
         * 等級是否來自**官方 CEFR 清單**（CEFR-J／Octanove）。
         *
         * 第八欄的 `a`/`e`/`u`；舊資產沒有這一欄時退回看 `sourceCode`，
         * 所以買舊資料也不會錯判。
         */
        val authoritative: Boolean = sourceCode == "j" || sourceCode == "o",
    )

    override fun lookup(word: String): WordLevel {
        val surface = normalize(word)
        if (surface.isEmpty()) return WordLevel(surface, surface, null, UNLISTED_SOURCE)

        val lemma = lemmas[surface] ?: surface
        // 縮寫（`time's` / `husband's` / `can't`）在等級表裡不會有自己的條目，
        // 但它的字幹是實詞。少了這一層，`time's` 會被當成「四層都查不到」的
        // 生僻字而**排到最前面被挖**——歌詞裡這種字很多，會毀掉整首歌的題目。
        val base = contractionBaseOf(surface)

        val direct = levels[surface]
        val fromLemma = if (lemma != surface) levels[lemma] else null
        val fromBase = base
            ?.takeIf { it != surface && it != lemma }
            ?.let { levels[it] }

        // ---- 四層來源的優先序（擁有者指定）----
        //
        // ```
        // 1. 官方 CEFR（j/o）      最可信
        // 2. 考試標籤（t）
        // 3. BNC 詞頻（b）
        // 4. UNKNOWN（u / 沒有這一列）
        // ```
        // 同一層裡：字面形式 → 原形 → 縮寫字幹。鍵值用 `tier * 10 + 序`，
        // 免得「下一層的字面」與「上一層的原形」撞在同一格。
        //
        // ⚠️ 為什麼原形的 CEFR 要贏過字面的標籤：
        // `met` 自己有 BNC 排名（會被推成 B1），但它的原形 `meet` 在 CEFR-J 是 A2。
        // 若照「字面優先」，`met` 會被當成進階字挖出來——使用者會看到一個早就會的字
        // 被考。同理 `healing`（托福標籤 B2）應該跟著 `heal` 走。
        val hit = listOfNotNull(
            direct?.let { it to tierOf(it) * 10 },
            fromLemma?.let { it to tierOf(it) * 10 + 1 },
            fromBase?.let { it to tierOf(it) * 10 + 2 },
        ).minByOrNull { it.second }?.first

        val surfaceRank = direct?.rank?.takeIf { it > 0 }
        val fallbackRank = surfaceRank
            ?: fromLemma?.rank?.takeIf { it > 0 }
            ?: fromBase?.rank?.takeIf { it > 0 }
        val commonness = listOfNotNull(
            surfaceRank,
            fromLemma?.rank?.takeIf { it > 0 },
            fromBase?.rank?.takeIf { it > 0 },
        ).minOrNull()

        return WordLevel(
            surface = surface,
            lemma = lemma,
            level = hit?.level,
            source = hit?.let { sourceLabel(it.sourceCode) } ?: UNLISTED_SOURCE,
            frequencyRank = fallbackRank,
            surfaceRank = surfaceRank,
            isProperNoun = (direct?.properNoun ?: false) ||
                (fromLemma?.properNoun ?: false) ||
                (fromBase?.properNoun ?: false),
            estimated = hit?.authoritative == false,
            rawLevel = hit?.rawLevel,
            partOfSpeech = hit?.partOfSpeech?.takeIf { it.isNotBlank() },
            allPartsOfSpeech = hit?.partsOfSpeech.orEmpty(),
            authoritative = hit?.authoritative == true,
            commonnessRank = commonness,
        )
    }

    /**
     * 縮寫的字幹：`time's` → `time`、`can't` → `can`、`we're` → `we`。
     *
     * **只做字形推導，不查詞典**（與 `CommonWordFilter.expandContraction` 同一原則）：
     * 歌詞的縮寫就是固定那幾種結尾，推導足以命中。
     */
    private fun contractionBaseOf(surface: String): String? {
        val apostrophe = surface.indexOf('\'')
        if (apostrophe <= 0) return null
        val base = surface.substring(0, apostrophe)
        if (base.length < 2 || !base.all { it in 'a'..'z' }) return null
        return base
    }

    /**
     * 來源的優先層級（小的贏）。
     *
     * `u`（UNKNOWN 列）排在最後：它只代表「有這個字但沒等級」，
     * 一旦原形有真資料就該採用原形的。
     */
    private fun tierOf(record: LevelRecord): Int = when (record.sourceCode) {
        "j", "o" -> 0
        "t" -> 1
        "b", "f" -> 2
        else -> 3
    }

    /** 詞頻排名：先看自己，再看原形。 */
    fun frequencyRankOf(word: String): Int? {
        val surface = normalize(word)
        if (surface.isEmpty()) return null
        levels[surface]?.rank?.takeIf { it > 0 }?.let { return it }
        val lemma = lemmas[surface] ?: return null
        return levels[lemma]?.rank?.takeIf { it > 0 }
    }

    /**
     * 高頻詞判定會**同時看原形**：`years` 不在表內，但它的原形 `year` 在，
     * 所以 `years` 也算高頻。若不看原形，所有複數形都會漏掉。
     */
    override fun isHighFrequency(word: String): Boolean {
        val surface = normalize(word)
        if (surface.isEmpty()) return false
        if (surface in frequent) return true
        val lemma = lemmas[surface] ?: return false
        return lemma in frequent
    }

    /** 表內有幾個字（供設定頁顯示資料版本）。 */
    val levelCount: Int get() = levels.size

    val lemmaCount: Int get() = lemmas.size

    private fun normalize(value: String): String =
        value.trim().lowercase().replace('’', '\'')

    companion object {
        /** 資料檔在 assets 下的目錄。 */
        const val ASSET_DIR = "data"

        /** `levels.tsv` 裡代表「有這個字、但沒有等級」的代碼。 */
        const val UNKNOWN_LEVEL_CODE = "U"

        /** `levels.tsv` 第三欄的代碼 → 人看得懂的來源。 */
        internal fun sourceLabel(code: String): String = when (code) {            "j" -> "CEFR-J"
            "o" -> "Octanove C1/C2"
            "t" -> TAG_SOURCE_LABEL
            "b", "f" -> BNC_SOURCE_LABEL
            "u" -> "未收錄"
            else -> code
        }

        /** `vt.` → `vt`；空白 → null。詞性比對一律用去點小寫。 */
        internal fun normalizePos(raw: String): String? =
            raw.trim().trimEnd('.').lowercase().takeIf { it.isNotEmpty() }

        /**
         * 從「路徑 → 內容」的讀取函式建構。
         *
         * 用函式而不是 `AssetManager`，是為了讓同一份邏輯能在 JVM 單元測試裡
         * 直接讀 `app/src/main/assets`——測試與正式執行解析的是**同一份檔案**。
         */
        fun load(read: (String) -> String): LanguageData {
            // `trimEnd('\r')` 不是多餘的：這些是逐行解析的 TSV，若檔案在 checkout 時
            // 被換成 CRLF（Windows 上的 core.autocrlf），每行最後一個欄位都會多一個
            // `\r`——等級來源會變成 "j\r"、原形會變成 "year\r"，而且在本機永遠看不出來。
            // `.gitattributes` 已經把資料檔標成 binary，這裡是第二道保險。
            fun lines(text: String) = text.lineSequence().map { it.trimEnd('\r') }

            val levels = HashMap<String, LevelRecord>(52_000)
            lines(read("$ASSET_DIR/levels.tsv")).forEach { line ->
                if (line.isEmpty()) return@forEach
                val fields = line.split('\t')
                if (fields.size < 3) return@forEach
                // `U` = 四層都查不到「等級」，但這個字確實存在（擁有者指定的第四層）。
                // 它仍然要進表：查得到這一列才知道「這是實詞，只是沒有等級」，
                // 才不會跟「根本不是英文詞」混為一談。
                val level = if (fields[1] == UNKNOWN_LEVEL_CODE) {
                    null
                } else {
                    CefrLevel.fromLabel(fields[1]) ?: return@forEach
                }
                val rawPos = fields.getOrNull(6).orEmpty()
                val parts = rawPos.split('/').mapNotNull(::normalizePos).toSet()
                levels[fields[0]] = LevelRecord(
                    level = level,
                    sourceCode = fields[2],
                    rank = fields.getOrNull(3)?.toIntOrNull() ?: 0,
                    properNoun = fields.getOrNull(4) == "1",
                    rawLevel = fields.getOrNull(5)?.let { raw ->
                        if (raw == UNKNOWN_LEVEL_CODE) null else CefrLevel.fromLabel(raw)
                    } ?: level,
                    partOfSpeech = rawPos.split('/').firstOrNull()?.trim().orEmpty(),
                    partsOfSpeech = parts,
                    // 第八欄：a=官方、e=推估、u=無。舊資產沒有這一欄 → 看來源代碼。
                    authoritative = when (fields.getOrNull(7)) {
                        "a" -> true
                        "e", "u" -> false
                        else -> fields[2] == "j" || fields[2] == "o"
                    },
                )
            }

            val lemmas = HashMap<String, String>(36_000)
            lines(read("$ASSET_DIR/lemma.tsv")).forEach { line ->
                if (line.isEmpty()) return@forEach
                val tab = line.indexOf('\t')
                if (tab <= 0 || tab == line.length - 1) return@forEach
                lemmas[line.substring(0, tab)] = line.substring(tab + 1)
            }

            val frequent = HashSet<String>(4_000)
            lines(read("$ASSET_DIR/freq.tsv")).forEach { line ->
                if (line.isNotEmpty()) frequent += line
            }

            return LanguageData(levels = levels, lemmas = lemmas, frequent = frequent)
        }
    }
}

/**
 * [LanguageData] 的持有者：負責只載入一次、且在 IO 執行緒載入。
 *
 * 用 `@Singleton` 是因為三份表加起來約 6 萬筆，重複解析沒有意義。
 */
@Singleton
class LanguageDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cached: LanguageData? = null

    private val mutex = Mutex()

    /** 取得（必要時載入）語言資料。可從任何執行緒呼叫。 */
    suspend fun get(): LanguageData {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                LanguageData.load { path ->
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }
            }.also { cached = it }
        }
    }
}
