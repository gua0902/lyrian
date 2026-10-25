package com.antigravity.lyrian.data.ai

/**
 * AI 功能的**唯一入口**。
 *
 * ## 為什麼要有這一層
 *
 * 擁有者的要求是「先建好接口與設定入口，之後接入任何 AI 都不必改動呼叫端」。
 * 因此所有 AI 能力都收斂在這個介面，呼叫端（結果頁、字庫出題）
 * **只認得它**，不認得 OpenAI／Gemini／DeepSeek 任何一家。
 *
 * 翻譯批改已於 2026-10 取消，介面一併移除。
 *
 * ## 本輪的行為
 *
 * **全部回傳 [AiResult.NotEnabled]**——連線層還沒實作，App 不會發出任何請求。
 * 這是刻意的：先讓設定與介面存在，之後只要換掉 [AiService] 的實作，
 * 呼叫端一行都不用改。
 */
interface AiService {

    /**
     * 解釋一個片語／俚語。
     *
     * @param phrase 片語本體，例如 `break a leg`
     * @param context 它出現的那句歌詞（AI 需要上下文才不會解釋錯）
     */
    suspend fun explainPhrase(phrase: String, context: String): AiResult<PhraseExplanation>

    /**
     * 分析整首歌的詞彙，供結果頁的「值得學的表達」區塊使用。
     *
     * @param lyrics 整首歌詞（純文字，一行一句）
     * @param minLevel 使用者設定的等級下限，用來濾掉太簡單的表達
     */
    suspend fun analyzeSongVocabulary(
        lyrics: String,
        minLevel: String,
    ): AiResult<SongVocabularyAnalysis>

    /**
     * 用字庫的單字出題。
     *
     * @param words 要入題的單字（通常是字庫裡還沒掌握的字）
     * @param count 想要幾題
     */
    suspend fun generateQuestions(
        words: List<String>,
        count: Int,
    ): AiResult<List<GeneratedQuestion>>
}

/**
 * AI 呼叫的結果。
 *
 * 刻意分成三種而不是「回傳 null」：
 *  - [NotEnabled]：使用者沒開 AI（或本輪還沒實作）→ UI **整塊隱藏**，不是顯示錯誤
 *  - [Ok]：成功
 *  - [Failed]：真的失敗了 → UI 可以顯示原因
 */
sealed interface AiResult<out T> {
    /** AI 未啟用。**這不是錯誤**，呼叫端應該安靜地不顯示相關區塊。 */
    data object NotEnabled : AiResult<Nothing>

    data class Ok<T>(val value: T) : AiResult<T>

    data class Failed(val message: String) : AiResult<Nothing>

    /** 是否為「未啟用」——呼叫端最常問的一題。 */
    val isNotEnabled: Boolean get() = this is NotEnabled
}

/** [AiService.explainPhrase] 的結果。 */
data class PhraseExplanation(
    val phrase: String,
    val meaning: String,
    /** 為什麼在這個語境下是這個意思 */
    val note: String? = null,
)

/** [AiService.analyzeSongVocabulary] 的結果。 */
data class SongVocabularyAnalysis(
    /** 值得學的片語／諺語 */
    val phrases: List<VocabularyInsight> = emptyList(),
)

/** 一個值得學的表達。 */
data class VocabularyInsight(
    val phrase: String,
    val meaning: String,
    /** `idiom` / `phrase` / `collocation` */
    val kind: String = "phrase",
)

/** [AiService.generateQuestions] 的一題。 */
data class GeneratedQuestion(
    val prompt: String,
    val answer: String,
    val options: List<String> = emptyList(),
)

/**
 * 目前的實作：**一律回傳「未啟用」**。
 *
 * 沒有任何 HTTP 客戶端注入進來，所以在結構上就**不可能**發出請求——
 * 這正是擁有者要的驗收條件（專案內無任何 AI 請求）。
 */
@javax.inject.Singleton
class DisabledAiService @javax.inject.Inject constructor() : AiService {

    override suspend fun explainPhrase(
        phrase: String,
        context: String,
    ): AiResult<PhraseExplanation> = AiResult.NotEnabled

    override suspend fun analyzeSongVocabulary(
        lyrics: String,
        minLevel: String,
    ): AiResult<SongVocabularyAnalysis> = AiResult.NotEnabled

    override suspend fun generateQuestions(
        words: List<String>,
        count: Int,
    ): AiResult<List<GeneratedQuestion>> = AiResult.NotEnabled
}
