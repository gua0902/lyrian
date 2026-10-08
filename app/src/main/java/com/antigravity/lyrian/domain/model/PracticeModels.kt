package com.antigravity.lyrian.domain.model

/**
 * 一行歌詞。時間軸單位一律是**毫秒**。
 *
 * 來源優先序見 [com.antigravity.lyrian.data.lyrics.LyricsParser]：
 * LRCLIB 的 `lyricsfile`（有 endMs）→ `syncedLyrics`（endMs 為 null）→ `plainLyrics`（推算）。
 *
 * @property index 在歌曲中的行序（0-based，解析後重新編號）
 * @property endMs 只有 `lyricsfile` 來源才有；其他來源為 null
 */
data class LyricLine(
    val index: Int,
    val text: String,
    val startMs: Long,
    val endMs: Long? = null,
)

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
