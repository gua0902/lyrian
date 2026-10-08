package com.antigravity.lyrian.ui.screens.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.data.local.AttemptDao
import com.antigravity.lyrian.data.local.DailyStatDao
import com.antigravity.lyrian.data.local.VocabDao
import com.antigravity.lyrian.data.local.VocabEntity
import com.antigravity.lyrian.util.toDayKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MyUiState(
    val totalScore: Int = 0,
    val masteredWords: Int = 0,
    val dueForReview: Int = 0,
    val streakDays: Int = 0,
    /** 本週的學習狀態，長度 7（週一到週日） */
    val weekActivity: List<Boolean> = List(7) { false },
    val todayAttempts: Int = 0,
)

@HiltViewModel
class MyViewModel @Inject constructor(
    private val dailyStatDao: DailyStatDao,
    private val vocabDao: VocabDao,
    private val attemptDao: AttemptDao,
) : ViewModel() {

    val uiState: StateFlow<MyUiState> = combine(
        dailyStatDao.observeAll(),
        dailyStatDao.observeTotalScore(),
        vocabDao.observeTotal(),
        vocabDao.observeCountByStatus(VocabEntity.STATUS_MASTERED),
        attemptDao.observeTotal(),
    ) { stats, totalScore, vocabTotal, mastered, _ ->
        val today = toDayKey()
        val todayStat = stats.firstOrNull { it.day == today }

        MyUiState(
            totalScore = totalScore,
            masteredWords = mastered,
            dueForReview = vocabTotal - mastered,
            streakDays = calculateStreak(stats.map { it.day }, today),
            weekActivity = calculateWeekActivity(stats.map { it.day }),
            todayAttempts = todayStat?.attempts ?: 0,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MyUiState(),
    )
}

/**
 * 連續學習天數。
 *
 * 從今天（或最近有紀錄的那天）往回數，直到遇到中斷的一天。
 * **今天還沒練時從昨天起算**，避免「今天還沒開始」就把連續天數歸零——
 * 那對使用者是錯誤的懲罰。
 */
internal fun calculateStreak(recordedDays: List<String>, today: String): Int {
    if (recordedDays.isEmpty()) return 0

    val days = recordedDays.toSet()
    val cursor = Calendar.getInstance().apply {
        val parts = today.split("-")
        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    }

    fun key(cal: Calendar): String =
        "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )

    // 今天沒有紀錄時，允許從昨天開始算
    if (key(cursor) !in days) {
        cursor.add(Calendar.DAY_OF_MONTH, -1)
    }

    var streak = 0
    while (key(cursor) in days) {
        streak++
        cursor.add(Calendar.DAY_OF_MONTH, -1)
    }
    return streak
}

/**
 * 本週（週一到週日）哪幾天有學習紀錄。
 *
 * 用來畫「我的」頁的週曆。
 */
internal fun calculateWeekActivity(recordedDays: List<String>): List<Boolean> {
    val days = recordedDays.toSet()
    val calendar = Calendar.getInstance()

    // 移動到本週的週一（Calendar.MONDAY = 2）
    while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
        calendar.add(Calendar.DAY_OF_MONTH, -1)
    }

    return (0 until 7).map { offset ->
        val day = calendar.clone() as Calendar
        day.add(Calendar.DAY_OF_MONTH, offset)
        val key = "%04d-%02d-%02d".format(
            day.get(Calendar.YEAR),
            day.get(Calendar.MONTH) + 1,
            day.get(Calendar.DAY_OF_MONTH),
        )
        key in days
    }
}
