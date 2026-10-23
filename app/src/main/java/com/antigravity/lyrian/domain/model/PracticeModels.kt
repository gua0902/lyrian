package com.antigravity.lyrian.domain.model

/**
 * 一行歌詞，**可選擇是否已挖空**。
 *
 * 依擁有者指定：整首歌詞在練習開始時就**全部挖空**，
 * 不是等到輪到那一句才挖——否則使用者會先把後面的歌詞看光，
 * 輪到時就沒有挑戰性了。
 *
 * 時間軸單位一律是**毫秒**。來源優先序見
 * [com.antigravity.lyrian.data.lyrics.LyricsParser]。
 *
 * @property blankStart 空格在 [text] 中的起始索引；-1 表示沒有挖空
 * @property blankEnd 空格在 [text] 中的結束索引（不含）
 * @property answer 該空格的答案；null 表示這行沒有挖空
 */
data class LyricLine(
    val index: Int,
    val text: String,
    val startMs: Long,
    val endMs: Long? = null,
    val blankStart: Int = -1,
    val blankEnd: Int = -1,
    val answer: String? = null,
) {
    /** 這行是否有挖空。 */
    val hasBlank: Boolean get() = answer != null && blankStart >= 0

    /** 空格前的文字。 */
    val prefix: String get() = if (hasBlank) text.substring(0, blankStart) else text

    /** 空格後的文字。 */
    val suffix: String get() = if (hasBlank) text.substring(blankEnd) else ""

    /**
     * 逐字稿中要顯示的內容。
     *
     * @param filled 有值時顯示這個字（已作答）；null 則顯示底線
     * @param showBlank 是否顯示空格。未輪到的句子仍會挖空（依擁有者指定），
     *   但 UI 可選擇用較低對比呈現
     */
    fun render(filled: String? = null, showBlank: Boolean = true): String {
        if (!hasBlank || !showBlank) return text
        return prefix + (filled ?: "____") + suffix
    }
}

/**
 * 一個挖空題目。
 *
 * [prefix] + [answer] + [suffix] 必須能還原原始歌詞行——
 * 這是不變式，由單元測試守住（見 `FillChoiceBuilderTest`）。
 *
 * @property blankIndex 在所有空格中的序號（0-based）。UI 顯示時 +1
 */
data class FillChoiceQuestion(
    val blankIndex: Int,
    val lineIndex: Int,
    val prefix: String,
    val answer: String,
    val suffix: String,
    val startMs: Long,
    val endMs: Long? = null,
) {
    /** 還原後的完整原句。 */
    val originalLine: String get() = "$prefix$answer$suffix"
}

/** 一首歌的完整四選一資料集。 */
data class FillChoiceDataset(
    /** 全部歌詞行（含沒有挖空的行），供歌詞流渲染 */
    val lines: List<LyricLine>,
    /** 全部題目，依歌詞順序排列 */
    val questions: List<FillChoiceQuestion>,
    /**
     * 每一行的挖空決策（含被過濾掉的候選字與其等級來源）。
     *
     * 供偵錯輸出用——擁有者要能驗證「挖了哪個字、為什麼」，
     * 由 `PracticeViewModel` 把這份資料印成 log。
     * 預設空清單，讓只關心題目的呼叫端不必理會。
     */
    val debug: List<BlankDecision> = emptyList(),
)

/**
 * 一行的挖空決策。
 *
 * @property chosenWord 選中的字；null 表示這句不挖
 * @property chosenLemma 選中字的原形（`abandoned` → `abandon`）
 * @property chosenLevel 選中字的 CEFR 等級；未收錄時為 C2（視為高階）
 * @property chosenLevelSource 選中字的等級**來源**
 * @property frequencyRank BNC 詞頻排名（1 = 最常見）；null 表示沒有排名
 * @property basis 為什麼選這個字（排序依據），例如 `等級優先（B2 > B1）`
 * @property rejected 這句被排除的字與各自的原因
 */
data class BlankDecision(
    val lineIndex: Int,
    val chosenWord: String?,
    val chosenLemma: String? = null,
    val chosenLevel: String? = null,
    val chosenLevelSource: String? = null,
    val frequencyRank: Int? = null,
    val basis: String? = null,
    val rejected: List<RejectedCandidate> = emptyList(),
)

/** 被過濾掉的候選字。 */
data class RejectedCandidate(
    val word: String,
    /** 被排除的原因，例如 `Top 1000 高頻詞`、`CEFR-J A2（低於 B1）` */
    val levelSource: String,
)

/** 作答進度。對應 UI 的「作答進度：2 / 10 正確（20%）」。 */
data class Progress(
    val correct: Int,
    val total: Int,
) {
    val percent: Int get() = if (total > 0) Math.round(correct * 100f / total) else 0
}

/** 練習模式。目前只有 [FILL_CHOICE] 有實作。 */
enum class PracticeModeId {
    /** 四選一填空。完全離線，零 AI 依賴。 */
    FILL_CHOICE,

    /** 英翻中翻譯，需 BYOK AI。 */
    ZH_TRANSLATE,
}

/** 單次作答紀錄。`Attempt` 是所有練習模式的**唯一收斂點**。 */
data class Attempt(
    val modeId: PracticeModeId,
    val lineIndex: Int,
    val blankIndex: Int,
    val prompt: String,
    val userAnswer: String,
    val correctAnswer: String,
    val isCorrect: Boolean,
    /** 0f..1f。選擇題為 0 或 1；翻譯題為相似度分數 */
    val score: Float,
)

/** 使用者對某個空格的作答結果（供 UI 渲染對錯樣式）。 */
data class ChoiceRecord(
    val choice: String,
    val isCorrect: Boolean,
)

/**
 * 結果頁要顯示的一個字。
 *
 * 依擁有者指定，列表**每條只顯示 eng、詞性、中文釋義三項**，
 * 不顯示例句、不顯示等級細節——那是單字詳情頁的事。
 *
 * @property word 顯示的形（錯題是正解，延伸詞彙是原形）
 * @property partOfSpeech 詞性縮寫，例如 `n.`
 * @property meaning 繁體中文釋義
 * @property lemma 去重鍵（入庫時用）
 * @property level CEFR 等級（決定要不要自動入庫，畫面上不顯示）
 */
data class ResultWord(
    val word: String,
    val partOfSpeech: String? = null,
    val meaning: String? = null,
    val lemma: String = word.lowercase(),
    val level: CefrLevel? = null,
    /** 已經被使用者手動加入字庫 */
    val addedToWordbank: Boolean = false,
)

/**
 * 要收進字庫的候選單字。
 *
 * @property lemma 去重鍵。目前等於正規化後的答案；
 *   P1.5 接上 ECDICT 的 `exchange` 欄位後會改用真正的詞形還原。
 */
data class VocabCandidate(
    val lemma: String,
    val surfaceForm: String,
    /** 出現的完整歌詞原句——字庫複習時當題幹用 */
    val contextLine: String,
    val songId: Long? = null,
)
