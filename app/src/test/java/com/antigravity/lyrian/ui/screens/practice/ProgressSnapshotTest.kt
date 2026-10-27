package com.antigravity.lyrian.ui.screens.practice

import androidx.lifecycle.SavedStateHandle
import com.antigravity.lyrian.domain.model.ChoiceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作答進度的存與讀。
 *
 * 這是「程序被回收後，已答過的題目不會被重答」的基礎。重答的代價不是多花時間，
 * 而是 `attempt` 與每日統計被**重複累加**——那是使用者看得到的統計錯誤。
 */
class ProgressSnapshotTest {

    private fun record(choice: String, correct: Boolean = true) = ChoiceRecord(choice, correct)

    @Test
    fun `存進去的進度可以原樣讀回`() {
        val handle = SavedStateHandle()
        val selections = mapOf(
            0 to record("deed"),
            3 to record("trim", correct = false),
            7 to record("platinum"),
        )

        ProgressSnapshot.save(
            handle = handle,
            songId = 42L,
            selections = selections,
            activeBlankIndex = 8,
            startedAtMs = 1_700_000_000_000L,
        )

        val restored = ProgressSnapshot.read(handle)

        assertEquals(42L, restored?.songId)
        assertEquals(selections, restored?.selections)
        assertEquals(8, restored?.activeBlankIndex)
        assertEquals(1_700_000_000_000L, restored?.startedAtMs)
    }

    @Test
    fun `答錯的紀錄也要保留`() {
        // 若只存「答對」，答錯的題目回來會變回未答，使用者會被迫重答
        val handle = SavedStateHandle()
        ProgressSnapshot.save(
            handle = handle,
            songId = 1L,
            selections = mapOf(2 to record("wrong", correct = false)),
            activeBlankIndex = 3,
            startedAtMs = 5L,
        )

        val restored = ProgressSnapshot.read(handle)
        assertEquals(false, restored?.selections?.get(2)?.isCorrect)
        assertEquals("wrong", restored?.selections?.get(2)?.choice)
    }

    @Test
    fun `沒有存過就回傳 null`() {
        assertNull(ProgressSnapshot.read(SavedStateHandle()))
    }

    @Test
    fun `清掉之後讀不到`() {
        val handle = SavedStateHandle()
        ProgressSnapshot.save(
            handle = handle,
            songId = 9L,
            selections = mapOf(0 to record("x")),
            activeBlankIndex = 1,
            startedAtMs = 1L,
        )
        ProgressSnapshot.clear(handle)

        assertNull(ProgressSnapshot.read(handle))
    }

    @Test
    fun `空進度也能存讀`() {
        val handle = SavedStateHandle()
        ProgressSnapshot.save(handle, songId = 3L, selections = emptyMap(), activeBlankIndex = 0, startedAtMs = 7L)

        val restored = ProgressSnapshot.read(handle)
        assertTrue(restored?.selections?.isEmpty() == true)
        assertEquals(3L, restored?.songId)
    }

    @Test
    fun `三個陣列長度不一致時整份放棄`() {
        // 模擬「存到一半被打斷」：只有 blankIndex 陣列有內容
        val handle = SavedStateHandle()
        handle["practice.progress.songId"] = 1L
        handle["practice.progress.blankIndexes"] = intArrayOf(0, 1, 2)
        handle["practice.progress.choices"] = arrayOf("a")
        handle["practice.progress.correct"] = booleanArrayOf(true, false, true)

        assertNull(ProgressSnapshot.read(handle))
    }
}
