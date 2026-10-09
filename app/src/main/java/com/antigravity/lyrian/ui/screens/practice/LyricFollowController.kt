package com.antigravity.lyrian.ui.screens.practice

/**
 * 歌詞捲動的主控權。
 *
 * 這是「歌詞跟時間跑」與「歌詞跟使用者跑」之間那個矛盾的解法。
 * 擁有者指定的規則：
 *
 * > 錯過了三秒鐘就暫停，等答完再繼續
 *
 * ## 為什麼不能單純讓其中一方贏
 *
 *  - 完全跟時間軸：使用者還在看上一句，畫面就跳走了
 *  - 完全跟使用者：歌詞與音樂對不上，跟唱沒有意義
 *
 * 所以預設**跟隨時間軸**（[AUTO]），但使用者一旦自己動手，就讓位給人（[MANUAL]）——
 * 並且在「閒置 3 秒**且**當前題已作答」時才收回主控權。
 * 「已作答」這個條件是關鍵：使用者正在思考時把畫面搶走，是最糟的體驗。
 *
 * ## 這個類別刻意不依賴 Android／Compose
 *
 * 時間由外部傳入（[nowMs]），因此閒置邏輯可以在 JVM 上完整單元測試，
 * 不必真的等 3 秒。Compose 端只負責把事件餵進來。
 */
class LyricFollowController(
    /** 使用者停手後，多久才考慮收回主控權。 */
    val idleDelayMs: Long = DEFAULT_IDLE_DELAY_MS,
) {

    /** 捲動主控權在誰手上。 */
    enum class Mode {
        /** 跟隨當前進度，自動捲動。 */
        AUTO,

        /** 使用者剛動過手，暫停自動捲動。 */
        MANUAL,
    }

    var mode: Mode = Mode.AUTO
        private set

    /**
     * 播放器是否暫停。
     *
     * **這是「播放鍵同時控制播放器與歌詞滾動」的實作點**：
     * 暫停時歌詞也停止跟隨——否則畫面會自己跑，跟沒有播放鍵一樣。
     *
     * 預設為 `false`（未暫停），讓這個類別單獨使用時的行為單純。
     * 實際的暫停狀態由 UI 依播放器同步進來。
     */
    var isPlaybackPaused: Boolean = false
        private set

    /** 使用者最後一次手動捲動的時間；null 表示他還沒動過手。 */
    private var lastManualScrollAtMs: Long? = null

    /** 播放／暫停。由播放鍵呼叫。 */
    fun onPlaybackChanged(isPlaying: Boolean) {
        isPlaybackPaused = !isPlaying
    }

    /** 使用者手動捲動。 */
    fun onManualScroll(nowMs: Long) {
        mode = Mode.MANUAL
        lastManualScrollAtMs = nowMs
    }

    /**
     * 是否可以自動捲到 [lineIndex]。
     *
     * @param nowMs 現在時間
     * @param lineIndex 想捲過去的行
     * @param answeredLine 已作答的行索引；尚未作答的行不搶畫面
     */
    fun shouldAutoScroll(nowMs: Long, lineIndex: Int, answeredLine: Int): Boolean {
        // 暫停時不跟隨——歌詞靜止在使用者看的地方
        if (isPlaybackPaused) return false

        // 跟隨模式：一律可以捲
        if (mode == Mode.AUTO) return true

        // 尚未作答 → 絕不搶畫面，等使用者答完再說。
        // **這個判斷要在計時之前**，否則閒置期間會被誤判為「可以收回主控權」。
        if (lineIndex > answeredLine) return false

        // 手動模式必然有計時起點（onManualScroll 一定會設）。null 代表狀態被外力弄壞，
        // 保守地當成「剛動手」——下一個 tick 就會拿到真的時間。
        val lastAt = lastManualScrollAtMs ?: nowMs

        // 停手還不到 3 秒 → 讓使用者看完
        if (nowMs - lastAt < idleDelayMs) return false

        // 條件都滿足：收回主控權
        mode = Mode.AUTO
        return true
    }

    /**
     * 使用者答完一題後呼叫。
     *
     * **重設閒置計時**，讓「答完之後再等 3 秒」才回歸——
     * 而不是「他答完了但我早就等超過 3 秒，所以立刻跳走」。
     */
    fun onAnswered(nowMs: Long) {
        if (mode == Mode.MANUAL) {
            lastManualScrollAtMs = nowMs
        }
    }

    /** 回到初始狀態（重新挑戰時使用）。 */
    fun reset() {
        mode = Mode.AUTO
        lastManualScrollAtMs = null
        isPlaybackPaused = false
    }

    companion object {
        /**
         * 閒置多久才收回主控權。
         *
         * 3 秒是擁有者指定的值：短於此，使用者會覺得畫面在跟他搶；
         * 長於此，就得一直手動捲，失去跟唱的感覺。
         */
        const val DEFAULT_IDLE_DELAY_MS = 3_000L
    }
}
