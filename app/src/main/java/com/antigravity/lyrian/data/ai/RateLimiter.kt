package com.antigravity.lyrian.data.ai

import java.util.ArrayDeque
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 滑動視窗限流器。
 *
 * **實作參考 [Operit](https://github.com/AAswordman/Operit) 的 `SlidingWindowRateLimiter`**。
 *
 * 為什麼需要它：免費層的 AI API（如 NVIDIA NIM）幾乎都有「每分鐘 N 次」的限制。
 * 硬打會拿到 429，而 429 之後的退避如果沒有節奏，會讓體驗變得一頓一頓的。
 * 這個限流器讓我們**主動**控制送出速率，而不是被動等 429。
 *
 * 做法：用佇列記錄每次請求的時間戳；超過視窗就移除；
 * 佇列滿了就回傳「還要等幾毫秒」，由呼叫端決定要等還是換金鑰。
 */
class SlidingWindowRateLimiter(
    private val maxRequestsPerMinute: Int,
    private val windowMs: Long = 60_000L,
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    /**
     * 嘗試取得一次請求額度。
     *
     * @return 0 表示可以立刻送出；大於 0 表示還要等幾毫秒
     */
    suspend fun tryAcquire(nowMs: Long = System.currentTimeMillis()): Long {
        if (maxRequestsPerMinute <= 0) return 0L

        return mutex.withLock {
            // 清掉已滑出視窗的時間戳
            while (timestamps.isNotEmpty() && nowMs - timestamps.first() >= windowMs) {
                timestamps.removeFirst()
            }

            if (timestamps.size >= maxRequestsPerMinute) {
                val oldest = timestamps.first()
                (windowMs - (nowMs - oldest)).coerceAtLeast(1L)
            } else {
                timestamps.addLast(nowMs)
                0L
            }
        }
    }

    /** 等到有額度為止（會阻塞）。 */
    suspend fun acquire() {
        while (true) {
            val retryAfterMs = tryAcquire()
            if (retryAfterMs <= 0L) return
            delay(retryAfterMs)
        }
    }

    /** 目前的視窗內已用次數（供 UI 顯示）。 */
    suspend fun usedInWindow(nowMs: Long = System.currentTimeMillis()): Int = mutex.withLock {
        while (timestamps.isNotEmpty() && nowMs - timestamps.first() >= windowMs) {
            timestamps.removeFirst()
        }
        timestamps.size
    }
}

/**
 * 每個 provider 各自一個限流器。
 *
 * 不同 provider 的額度是獨立的，不能共用一個限流器。
 */
class RateLimiterRegistry {
    private val limiters = mutableMapOf<Long, SlidingWindowRateLimiter>()

    /** 取得（或建立）某個 provider 的限流器。 */
    fun forProvider(providerId: Long, maxRequestsPerMinute: Int): SlidingWindowRateLimiter =
        limiters.getOrPut(providerId) { SlidingWindowRateLimiter(maxRequestsPerMinute) }
}
