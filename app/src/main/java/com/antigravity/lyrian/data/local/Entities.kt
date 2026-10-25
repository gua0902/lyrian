package com.antigravity.lyrian.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lyrian 的 Room 實體。
 *
 * **刪除語意（重要，與 RN 版一致）**：`SongEntity` 只是 LRCLIB 資料的快取，
 * 清掉它**不該弄丟使用者的資料**。因此：
 *  - `LyricLineEntity` 隨歌曲 CASCADE（純快取）
 *  - `VocabEntity` / `AttemptEntity` 只把參照設為 SET_NULL（使用者資料必須留下）
 *
 * 時間一律以 epoch millis 儲存（Long），避免時區問題。
 */

@Entity(
    tableName = "song",
    indices = [Index(value = ["lrclibId"], unique = true)],
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LRCLIB 的 id。為 null 表示是手動加入、非來自 LRCLIB 的歌 */
    val lrclibId: Long? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    /** 來自 iTunes；LRCLIB 沒有封面 */
    val coverUrl: String? = null,
    /** 來自 iTunes 的 primaryGenreName，已翻為繁中 */
    val genre: String? = null,
    /** 由 ECDICT 詞頻推算；尚未接上時為 null */
    val difficulty: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "lyric_line",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["songId", "lineIndex"], unique = true),
        Index(value = ["songId"]),
    ],
)
data class LyricLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val lineIndex: Int,
    val text: String,
    val startMs: Long,
    /** 只有 LRCLIB 的 lyricsfile 來源才有 */
    val endMs: Long? = null,
)

@Entity(
    tableName = "vocab",
    indices = [
        Index(value = ["lemma"], unique = true),
        Index(value = ["firstSongId"]),
    ],
)
data class VocabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 去重鍵。目前等於正規化後的答案；接上 ECDICT 後改用詞形還原 */
    val lemma: String,
    /** 原文實際出現的形（例如 `Hope`） */
    val surfaceForm: String? = null,
    /** 字義。目前來自 Wiktionary（英文）；接上 ECDICT 後改為中文 */
    val translation: String? = null,
    /** 國際音標，例如 `/həʊp/` */
    val phonetic: String? = null,
    /** 音標腔調：`英` 或 `美`。字典來源已擇一，不並列 */
    val phoneticAccent: String? = null,
    /** 詞性的**英文縮寫**，例如 `n.` / `v.` / `adj.` */
    val partOfSpeech: String? = null,
    /** CEFR 等級（A1..C2）；接上 ECDICT 後才會有值 */
    val cefr: String? = null,
    val firstSongId: Long? = null,
    /** 出現的完整歌詞原句——複習時當題幹用 */
    val contextLine: String? = null,
    /** 字典資料是否已查過。用來避免重複連外 */
    val dictionaryFetched: Boolean = false,
    val errorCount: Int = 0,
    val correctStreak: Int = 0,
    /** learning / reviewing / mastered */
    val status: String = STATUS_LEARNING,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val STATUS_LEARNING = "learning"
        const val STATUS_REVIEWING = "reviewing"
        const val STATUS_MASTERED = "mastered"
    }
}

@Entity(
    tableName = "vocab_schedule",
    foreignKeys = [
        ForeignKey(
            entity = VocabEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VocabScheduleEntity(
    @PrimaryKey val vocabId: Long,
    /** FSRS 參數。尚未實作演算法時為 null */
    val stability: Double? = null,
    val difficulty: Double? = null,
    val dueAt: Long? = null,
    val lastReviewAt: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    /** new / learning / review / relearning */
    val state: String = STATE_NEW,
) {
    companion object {
        const val STATE_NEW = "new"
    }
}

@Entity(
    tableName = "attempt",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = VocabEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["modeId"]),
        // 外鍵欄位必須有索引，否則父表變更時會全表掃描
        Index(value = ["songId"]),
        Index(value = ["vocabId"]),
    ],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 目前只有 `fill-choice`（`zh-translate` 已於 2026-10 取消） */
    val modeId: String,
    val songId: Long? = null,
    val lineIndex: Int? = null,
    val vocabId: Long? = null,
    val prompt: String? = null,
    val userAnswer: String? = null,
    val correctAnswer: String? = null,
    /** 0f..1f */
    val score: Float? = null,
    val isCorrect: Boolean,
    val durationMs: Long? = null,
    val createdAt: Long,
)

@Entity(tableName = "daily_stat")
data class DailyStatEntity(
    /** `YYYY-MM-DD`（本地時區） */
    @PrimaryKey val day: String,
    val score: Int = 0,
    val attempts: Int = 0,
    val correct: Int = 0,
    val newWords: Int = 0,
)

/**
 * BYOK provider 設定。
 *
 * **金鑰本體不存這裡**——只存 `keyAlias`，實際值在 Android Keystore 加密後的位置。
 */
@Entity(tableName = "provider_config")
data class ProviderConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** OpenAI 相容端點 */
    val baseUrl: String,
    val model: String,
    /** 指向加密存放的金鑰；null 表示尚未設定 */
    val keyAlias: String? = null,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val lastTestAt: Long? = null,
    val lastTestOk: Boolean? = null,
)
