package com.antigravity.lyrian.ui.screens.wordbank

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 字庫分頁的判斷。
 *
 * 舊版把 `limit = 200` 寫死在 DAO，字庫超過 200 個字之後舊字會**整個從畫面消失**，
 * 但橫幅仍寫著「已收藏 350 個」。這個測試把「什麼時候還該顯示載入更多」固定下來。
 */
class WordbankPagingTest {

    @Test
    fun `還沒填滿視窗時沒有下一頁`() {
        assertFalse(
            WordbankPaging.hasMore(fetched = 12, limit = 200, total = 12),
        )
    }

    @Test
    fun `視窗填滿且總數更大時還有下一頁`() {
        assertTrue(
            WordbankPaging.hasMore(fetched = 200, limit = 200, total = 350),
        )
    }

    @Test
    fun `視窗剛好等於總數時沒有下一頁`() {
        // 200 個字剛好一頁：不該出現一個按了也沒東西的「載入更多」
        assertFalse(
            WordbankPaging.hasMore(fetched = 200, limit = 200, total = 200),
        )
    }

    @Test
    fun `載入第二頁後就結束`() {
        assertTrue(WordbankPaging.hasMore(fetched = 350, limit = 400, total = 350).not())
        assertFalse(WordbankPaging.hasMore(fetched = 350, limit = 400, total = 350))
    }

    @Test
    fun `空字庫不會顯示載入更多`() {
        assertFalse(WordbankPaging.hasMore(fetched = 0, limit = 200, total = 0))
    }
}
