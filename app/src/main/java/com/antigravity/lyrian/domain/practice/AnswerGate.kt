package com.antigravity.lyrian.domain.practice

/**
 * 一次作答點擊可不可以被接受。
 *
 * ## 為什麼需要「守門」而不是只防重複點擊
 *
 * 舊版 `onSelectOption(choice)` 只收到「使用者點了哪個字」，沒有收到
 * 「他點的是哪一題」。作答之後 `activeBlankIndex` 會**立刻**換到下一空
 * （同一句的第二個空、或全曲下一個未答的空），而選項格是 2×2 的固定版面：
 *
 * ```
 * 使用者：trim │ convertible        使用者手指停在「trim」的位置
 *         deed │ yacht
 *            ↓ 第一下：答了 blank 5（deed）
 *         platinum │ convertible     ← 同一格換成下一題的選項
 *         trim     │ yacht
 *            ↓ 第二下（150ms 後）：打到「platinum」→ 記成 blank 6 的錯答
 * ```
 *
 * 所以真正的問題有兩個來源，必須一起擋：
 *
 *  1. **這組選項已經不是現在的題目**（`AnimatedContent` 交叉淡入期間，
 *     舊格仍然可點）→ 用 `target != active` 擋。
 *  2. **換題後極短時間內的點擊**是上一個動作的殘點，不是新的作答意圖
 *     → 用 [DEBOUNCE_MS] 擋。
 *
 * 純函式、不碰 Android，所以可以直接在 JVM 上測（見 `AnswerGateTest`）。
 */
object AnswerGate {

    /**
     * 換題後多久內的點擊視為「打在新格上的殘點」。
     *
     * 300ms 略長於 `AnimatedContent` 的交叉淡入（`LyrianMotion.enter()` 約 400ms
     * 的淡入尾巴、但版面在 300ms 內已經穩定），也遠短於人「看清題目再點」的時間，
     * 不會誤傷正常作答。
     */
    const val DEBOUNCE_MS = 300L

    /** 為什麼接受／不接受。帶回原因才能寫出可診斷的 log。 */
    enum class Decision {
        /** 可以作答。 */
        Accept,

        /** 這一題已經答過（真正的重複點擊）。 */
        AlreadyAnswered,

        /** 使用者點的是畫面上殘留的上一組選項。 */
        StaleGrid,

        /** 換題後的殘點（時間太近）。 */
        TooSoon,
    }

    /**
     * @param target 這次點擊所屬的 blankIndex（由 UI 帶著，不是由 VM 猜）
     * @param active 目前畫面上正在作答的 blankIndex
     * @param answered 已經作答過的 blankIndex 集合
     * @param nowMs 單調時鐘（`SystemClock.elapsedRealtime()`）
     * @param lastAnswerAtMs 上一次**被接受**的作答時間；0 表示還沒有作答過
     */
    fun evaluate(
        target: Int,
        active: Int,
        answered: Set<Int>,
        nowMs: Long,
        lastAnswerAtMs: Long,
    ): Decision {
        // 順序刻意：先判斷「同一題重複點」，那是最常見、也最不需要 log 的情況。
        if (target in answered) return Decision.AlreadyAnswered
        if (target != active) return Decision.StaleGrid
        if (lastAnswerAtMs > 0L && nowMs - lastAnswerAtMs < DEBOUNCE_MS) return Decision.TooSoon
        return Decision.Accept
    }
}
