package com.antigravity.lyrian.domain.model

/**
 * 一行歌詞裡的一個挖空。
 *
 * 同一行最多兩個（[com.antigravity.lyrian.domain.practice.BlankPicker.MAX_BLANKS_PER_LINE]）。
 *
 * @property blankIndex 全曲的序號（0-based），作答、選項、紀錄都用它對應
 * @property start 在原始歌詞中的起始索引
 * @property end 結束索引（不含）
 * @property surface 原始表面形式（`Café`），只用於顯示與除錯
 * @property answer **正規化後**的答案（`cafe`）——作答比對一律用這個
 */
data class LyricBlank(
    val blankIndex: Int,
    val start: Int,
    val end: Int,
    val surface: String,
    val answer: String,
    val lemma: String,
)

/**
 * 一行歌詞。
 *
 * 依擁有者指定：整首歌詞在練習開始時就**全部挖空**，不是輪到那一句才挖。
 *
 * 時間軸單位一律是毫秒。來源優先序見
 * [com.antigravity.lyrian.data.lyrics.LyricsParser]。
 */
data class LyricLine(
    val index: Int,
    val text: String,
    val startMs: Long,
    val endMs: Long? = null,
    /** 這一行所有的挖空，依位置排序。 */
    val blanks: List<LyricBlank> = emptyList(),
) {
    /** 這行是否有挖空。 */
    val hasBlank: Boolean get() = blanks.isNotEmpty()

    /**
     * 把挖空換成答案（或底線）之後的整行文字。
     *
     * @param filled blankIndex → 使用者填的答案；沒填的用 [mask]
     */
    fun masked(
        filled: Map<Int, String> = emptyMap(),
        mask: String = "____",
    ): String {
        if (blanks.isEmpty()) return text
        val builder = StringBuilder(text.length + blanks.size * mask.length)
        var cursor = 0
        blanks.forEach { blank ->
            if (blank.start < cursor) return@forEach
            builder.append(text, cursor, blank.start)
            builder.append(filled[blank.blankIndex] ?: mask)
            cursor = blank.end
        }
        builder.append(text, cursor, text.length)
        return builder.toString()
    }
}

/**
 * 一個挖空題目。
 *
 * @property blankIndex 全曲序號（UI 顯示時 +1）
 * @property prompt 整句，**其他挖空以 `____` 表示**（不會洩漏別的答案）
 * @property answer 正規化答案
 * @property surface 原始表面形式
 */
data class FillChoiceQuestion(
    val blankIndex: Int,
    val lineIndex: Int,
    val prompt: String,
    val answer: String,
    val surface: String,
    val startMs: Long,
    val endMs: Long? = null,
)

/** 一首歌的完整四選一資料集。 */
data class FillChoiceDataset(
    /** 全部歌詞行（含沒有挖空的行），供歌詞流渲染 */
    val lines: List<LyricLine>,
    /** 全部題目，依歌詞順序排列 */
    val questions: List<FillChoiceQuestion>,
)

/** 作答進度。 */
data class Progress(
    val correct: Int,
    val total: Int,
) {
    val percent: Int get() = if (total > 0) Math.round(correct * 100f / total) else 0
}

/** 練習模式。目前只有 [FILL_CHOICE]。 */
enum class PracticeModeId {
    /** 四選一填空。完全離線，零 AI 依賴。 */
    FILL_CHOICE,
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
    /** 0f..1f。選擇題為 0 或 1。 */
    val score: Float,
)

/** 使用者對某個空格的作答結果（供 UI 渲染對錯樣式）。 */
data class ChoiceRecord(
    val choice: String,
    val isCorrect: Boolean,
)

/**
 * 結果頁「較難單字」的一列。
 *
 * 這份清單**只顯示**，不會自動寫入字庫；要不要收藏由使用者按「加入」決定。
 *
 * @property word 顯示的形（原形）
 * @property partOfSpeech 詞性縮寫
 * @property meaning 繁體中文釋義
 * @property lemma 去重鍵／入庫鍵
 * @property level CEFR 等級（畫面上不顯示）
 * @property addedToWordbank 使用者是否已手動加入
 */
data class ResultWord(
    val word: String,
    val partOfSpeech: String? = null,
    val meaning: String? = null,
    val lemma: String = word.lowercase(),
    val level: CefrLevel? = null,
    val addedToWordbank: Boolean = false,
)
