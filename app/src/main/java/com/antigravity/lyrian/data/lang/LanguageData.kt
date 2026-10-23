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
 * @property level 等級；null 表示三份資料都沒有這個字
 * @property source 判定來源，直接顯示在偵錯輸出與單字卡上
 */
data class WordLevel(
    val surface: String,
    val lemma: String,
    val level: CefrLevel?,
    val source: String,
) {
    /** 沒有等級資料的字，一律視為比 B2 更難（CEFR-J 只收 6,900 個基本字）。 */
    val treatAsLevel: CefrLevel get() = level ?: CefrLevel.C2

    val isUnlisted: Boolean get() = level == null
}

/**
 * 挖空判斷需要的語言資料。
 *
 * 抽成介面是為了讓 `FillChoiceBuilder` 保持純函式、可在 JVM 上測試；
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

/** 三份資料都沒收錄時顯示的來源字串。 */
const val UNLISTED_SOURCE = "未收錄（視為 B2 以上）"

/** 由詞頻推估出來的等級的來源標籤。 */
const val FREQ_SOURCE_LABEL = "詞頻推估"

/**
 * 離線語言資料：等級表 + 詞形還原表 + 高頻詞表。
 *
 * ## 為什麼是三個檔案而不是資料庫
 *
 * 這是**唯讀、整份載入、只查不寫**的資料，用 SQLite 只是多一層成本。
 * 三份 TSV 直接解析成 `HashMap` 後，一次查詢就是一次雜湊，沒有 I/O。
 *
 * 體積（由 `tools/build_assets.py` 產生，`--stats` 可重測）：約 0.94 MB。
 *
 * ## 載入時機
 *
 * 解析約 6 萬行、需要 50–100 ms，**不可以放在主執行緒**。
 * 因此對外一律透過 [LanguageDataStore.get]（suspend、在 IO 執行緒完成）。
 */
class LanguageData internal constructor(
    private val levels: Map<String, Pair<CefrLevel, String>>,
    private val lemmas: Map<String, String>,
    private val frequent: Set<String>,
) : WordLevelSource {

    override fun lookup(word: String): WordLevel {
        val surface = normalize(word)
        if (surface.isEmpty()) return WordLevel(surface, surface, null, UNLISTED_SOURCE)

        val lemma = lemmas[surface] ?: surface
        val direct = levels[surface]
        val fromLemma = if (lemma != surface) levels[lemma] else null

        // ⚠️ 順序很重要：**推估的等級不可以蓋掉真資料**。
        //
        // `met` 自己有詞頻（所以推估出 C2），但它的原形 `meet` 在 CEFR-J 是 A2。
        // 若先看 surface，`met` 會被當成 C2 的高階字而挖出來——那是錯的，
        // 而且錯得很明顯（使用者會看到一個早就會的字被考）。
        // 因此：真資料（CEFR-J／Octanove）優先，推估的等級只在兩邊都沒有真資料時使用。
        val hit = when {
            direct != null && direct.second != FREQ_SOURCE_LABEL -> direct
            fromLemma != null && fromLemma.second != FREQ_SOURCE_LABEL -> fromLemma
            direct != null -> direct
            else -> fromLemma
        }

        return if (hit == null) {
            WordLevel(surface, lemma, null, UNLISTED_SOURCE)
        } else {
            WordLevel(surface, lemma, hit.first, hit.second)
        }
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

        /** `levels.tsv` 第三欄的代碼 → 人看得懂的來源。 */
        internal fun sourceLabel(code: String): String = when (code) {
            "j" -> "CEFR-J"
            "o" -> "Octanove C1/C2"
            "f" -> FREQ_SOURCE_LABEL
            else -> code
        }

        /**
         * 從「路徑 → 內容」的讀取函式建構。
         *
         * 用函式而不是 `AssetManager`，是為了讓同一份邏輯能在 JVM 單元測試裡
         * 直接讀 `app/src/main/assets`——測試與正式執行解析的是**同一份檔案**。
         *
         * @suppress 呼叫端負責確保在 IO 執行緒呼叫（見 [LanguageDataStore]）
         */
        fun load(read: (String) -> String): LanguageData {
            // `trimEnd('\r')` 不是多餘的：這些是逐行解析的 TSV，若檔案在 checkout 時
            // 被換成 CRLF（Windows 上的 core.autocrlf），每行最後一個欄位都會多一個
            // `\r`——等級來源會變成 "j\r"、原形會變成 "year\r"，而且在本機永遠看不出來。
            // `.gitattributes` 已經把資料檔標成 binary，這裡是第二道保險。
            fun lines(text: String) = text.lineSequence().map { it.trimEnd('\r') }

            val levels = HashMap<String, Pair<CefrLevel, String>>(32_000)
            lines(read("$ASSET_DIR/levels.tsv")).forEach { line ->
                if (line.isEmpty()) return@forEach
                val first = line.indexOf('\t')
                val second = line.indexOf('\t', first + 1)
                if (first <= 0 || second <= first) return@forEach
                val word = line.substring(0, first)
                val level = CefrLevel.fromLabel(line.substring(first + 1, second)) ?: return@forEach
                levels[word] = level to sourceLabel(line.substring(second + 1))
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
