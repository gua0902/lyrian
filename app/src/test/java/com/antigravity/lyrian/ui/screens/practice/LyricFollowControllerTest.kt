package com.antigravity.lyrian.ui.screens.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LyricFollowController] 的單元測試。
 *
 * 這個類別不依賴 Android／Compose（時間由外部傳入），所以閒置邏輯
 * 可以在 JVM 上**完整驗證，不必真的等 3 秒**。
 *
 * 驗證的核心規則（擁有者指定）：
 *  1. 預設跟隨
 *  2. 使用者一動手就讓位
 *  3. 閒置 3 秒**且已作答**才收回主控權
 *  4. **尚未作答時絕不搶畫面** ← 最重要的一條
 */
class LyricFollowControllerTest {

    private val controller = LyricFollowController()

    // ---------- 預設行為 ----------

    @Test
    fun `預設是跟隨模式`() {
        assertEquals(LyricFollowController.Mode.AUTO, controller.mode)
    }

    @Test
    fun `跟隨模式下任何行都可以自動捲動`() {
        assertTrue(controller.shouldAutoScroll(nowMs = 0, lineIndex = 5, answeredLine = 0))
    }

    // ---------- 使用者動手 → 讓位 ----------

    @Test
    fun `使用者捲動後進入手動模式`() {
        controller.onManualScroll(nowMs = 0)
        assertEquals(LyricFollowController.Mode.MANUAL, controller.mode)
    }

    @Test
    fun `手動模式且尚未作答時不搶畫面`() {
        controller.onManualScroll(nowMs = 0)

        // 想捲到第 4 行，但使用者只答到第 1 行 → 不准動
        assertFalse(controller.shouldAutoScroll(nowMs = 10_000, lineIndex = 4, answeredLine = 1))
    }

    @Test
    fun `手動模式且未作答時即使過很久也不搶畫面`() {
        controller.onManualScroll(nowMs = 0)

        // 過了一分鐘，但使用者還沒答完 → 仍然不准動
        assertFalse(controller.shouldAutoScroll(nowMs = 60_000, lineIndex = 9, answeredLine = 2))
    }

    // ---------- 閒置 3 秒且已作答 → 收回主控權 ----------

    @Test
    fun `已作答且停手滿三秒就回到跟隨`() {
        controller.onManualScroll(nowMs = 0)

        // 想捲到第 2 行，使用者已答到第 2 行（= 這題答完了）
        assertTrue(controller.shouldAutoScroll(nowMs = 3_000, lineIndex = 2, answeredLine = 2))
        assertEquals(LyricFollowController.Mode.AUTO, controller.mode)
    }

    @Test
    fun `已作答但停手未滿三秒仍不動作`() {
        controller.onManualScroll(nowMs = 0)

        assertFalse(controller.shouldAutoScroll(nowMs = 2_999, lineIndex = 2, answeredLine = 2))
        assertEquals(LyricFollowController.Mode.MANUAL, controller.mode)
    }

    @Test
    fun `停手滿三秒但尚未作答仍不動作`() {
        controller.onManualScroll(nowMs = 0)

        // 時間夠了，但 lineIndex 超過已作答的位置
        assertFalse(controller.shouldAutoScroll(nowMs = 5_000, lineIndex = 3, answeredLine = 1))
    }

    // ---------- 作答會重設計時 ----------

    @Test
    fun `作答後重新計算三秒`() {
        controller.onManualScroll(nowMs = 0)

        // 使用者過了 10 秒才答完
        controller.onAnswered(nowMs = 10_000)

        // 答完後只過 500ms → 還在緩衝，不搶畫面
        assertFalse(controller.shouldAutoScroll(nowMs = 10_500, lineIndex = 2, answeredLine = 2))

        // 答完後滿 3 秒 → 可以回歸
        assertTrue(controller.shouldAutoScroll(nowMs = 13_000, lineIndex = 2, answeredLine = 2))
    }

    @Test
    fun `跟隨模式下作答不影響模式`() {
        controller.onAnswered(nowMs = 1_000)
        assertEquals(LyricFollowController.Mode.AUTO, controller.mode)
        assertTrue(controller.shouldAutoScroll(nowMs = 1_000, lineIndex = 8, answeredLine = 0))
    }

    // ---------- 邊界 ----------

    @Test
    fun `在同一個 tick 動手不會立刻被收回`() {
        controller.onManualScroll(nowMs = 1_000)
        assertFalse(controller.shouldAutoScroll(nowMs = 1_000, lineIndex = 0, answeredLine = 0))
    }

    @Test
    fun `可以在設定變更後重用同一個 controller 狀態`() {
        // 模擬「已停手且答完」→ 下一刻應恢復跟隨
        controller.onManualScroll(nowMs = 0)
        controller.onAnswered(nowMs = 0)

        assertTrue(controller.shouldAutoScroll(nowMs = 3_000, lineIndex = 1, answeredLine = 1))
        assertEquals(LyricFollowController.Mode.AUTO, controller.mode)
    }

    @Test
    fun `reset 回到初始狀態`() {
        controller.onManualScroll(nowMs = 0)
        controller.reset()

        assertEquals(LyricFollowController.Mode.AUTO, controller.mode)
        assertTrue(controller.shouldAutoScroll(nowMs = 0, lineIndex = 99, answeredLine = 0))
    }

    @Test
    fun `自訂閒置時間`() {
        val quick = LyricFollowController(idleDelayMs = 500)
        quick.onManualScroll(nowMs = 0)

        assertFalse(quick.shouldAutoScroll(nowMs = 499, lineIndex = 0, answeredLine = 0))
        assertTrue(quick.shouldAutoScroll(nowMs = 500, lineIndex = 0, answeredLine = 0))
    }

    @Test
    fun `預設閒置時間是三秒`() {
        assertEquals(3_000L, LyricFollowController.DEFAULT_IDLE_DELAY_MS)
        assertEquals(3_000L, controller.idleDelayMs)
    }
}
