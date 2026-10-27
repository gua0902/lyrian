package com.antigravity.lyrian.ui.screens.practice

import androidx.lifecycle.SavedStateHandle
import com.antigravity.lyrian.domain.model.ChoiceRecord

/**
 * 作答進度在 `SavedStateHandle` 的保存與還原。
 *
 * ## 為什麼需要它
 *
 * 原本的進度只存在 `PracticeUiState`（記憶體）。程序被系統回收、或使用者
 * 在背景被殺掉之後，`PracticeViewModel` 會走 `restoreFromCache` 重建歌詞與挖空
 * （亂數種子固定，所以**題目是一致的**），但 `selections` 是空的：
 *
 * ```
 * 死亡前：已答 12 題（attempt 也寫了 12 筆）
 * 回來後：0 題 → 使用者重答一次 → attempt 變 24 筆、每日統計重複累加
 * ```
 *
 * `SavedStateHandle` 會跟著導覽的 `NavBackStackEntry` 一起被系統存進
 * saved instance state，所以「程序被回收後重建」這條路徑救得回來，
 * 而且**不需要動 Room schema**。
 *
 * ## 存的是什麼
 *
 * 三個平行陣列 + 幾個純量。不用 `Bundle` 或 `Parcelable`：
 * `ChoiceRecord` 是自己的 domain 型別，把它拆成基本型別最不容易出錯，
 * 也讓這一層可以在 JVM 上直接測。
 *
 * ## 什麼情況會丟棄
 *
 * - `songId` 不同（同一個 ViewModel 被下一首歌重用）→ 整份丟棄
 * - `blankIndex` 超出目前題數（選詞規則改變、歌詞快取更新）→ 只保留仍在範圍內的
 *
 * 寧可重來，也不要把上一首歌的作答套到這一首。
 */
internal object ProgressSnapshot {

    private const val KEY_SONG_ID = "practice.progress.songId"
    private const val KEY_BLANKS = "practice.progress.blankIndexes"
    private const val KEY_CHOICES = "practice.progress.choices"
    private const val KEY_CORRECT = "practice.progress.correct"
    private const val KEY_ACTIVE = "practice.progress.activeBlank"
    private const val KEY_STARTED_AT = "practice.progress.startedAt"

    /** 還原回來的進度。 */
    data class Snapshot(
        val songId: Long,
        val selections: Map<Int, ChoiceRecord>,
        val activeBlankIndex: Int,
        val startedAtMs: Long,
    )

    /**
     * 寫入目前進度。每次作答後呼叫即可（資料量是題數等級，成本可忽略）。
     */
    fun save(
        handle: SavedStateHandle,
        songId: Long,
        selections: Map<Int, ChoiceRecord>,
        activeBlankIndex: Int,
        startedAtMs: Long,
    ) {
        val ordered = selections.entries.sortedBy { it.key }
        handle[KEY_SONG_ID] = songId
        handle[KEY_BLANKS] = ordered.map { it.key }.toIntArray()
        handle[KEY_CHOICES] = ordered.map { it.value.choice }.toTypedArray()
        handle[KEY_CORRECT] = ordered.map { it.value.isCorrect }.toBooleanArray()
        handle[KEY_ACTIVE] = activeBlankIndex
        handle[KEY_STARTED_AT] = startedAtMs
    }

    /** 讀回進度；沒有任何記錄時回傳 null。 */
    fun read(handle: SavedStateHandle): Snapshot? {
        val songId = handle.get<Long>(KEY_SONG_ID) ?: return null
        val blanks = handle.get<IntArray>(KEY_BLANKS) ?: intArrayOf()
        val choices = handle.get<Array<String>>(KEY_CHOICES) ?: emptyArray()
        val correct = handle.get<BooleanArray>(KEY_CORRECT) ?: booleanArrayOf()

        // 三個陣列必須等長；長度不符代表資料被截斷（型別轉換失敗等），整份放棄。
        if (blanks.size != choices.size || blanks.size != correct.size) return null

        val selections = blanks.indices.associate { index ->
            blanks[index] to ChoiceRecord(choice = choices[index], isCorrect = correct[index])
        }

        return Snapshot(
            songId = songId,
            selections = selections,
            activeBlankIndex = handle.get<Int>(KEY_ACTIVE) ?: 0,
            startedAtMs = handle.get<Long>(KEY_STARTED_AT) ?: 0L,
        )
    }

    /** 清掉（重玩、換歌時用）。 */
    fun clear(handle: SavedStateHandle) {
        handle.remove<Long>(KEY_SONG_ID)
        handle.remove<IntArray>(KEY_BLANKS)
        handle.remove<Array<String>>(KEY_CHOICES)
        handle.remove<BooleanArray>(KEY_CORRECT)
        handle.remove<Int>(KEY_ACTIVE)
        handle.remove<Long>(KEY_STARTED_AT)
    }
}
