package com.antigravity.lyrian.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.antigravity.lyrian.R

/**
 * 三個頂層目的地（PRD v2：Home / Wordbank / My）。
 *
 * 用 enum 而非散落的字串常數——編譯期就能檢查，也不會打錯字。
 * 順序即底部導覽列由左到右的順序。
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(
        route = "home",
        labelRes = R.string.tab_home,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
    ),
    WORDBANK(
        route = "wordbank",
        labelRes = R.string.tab_wordbank,
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
    ),
    MY(
        route = "my",
        labelRes = R.string.tab_my,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
    ),
    ;

    companion object {
        val START = HOME

        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}

/**
 * 非頂層的路由。
 *
 * 這些畫面會覆蓋整個畫面（不顯示底部導覽列），例如練習頁與設定頁。
 */
object Routes {
    const val PRACTICE = "practice/{songId}"

    /** 對應 [PRACTICE]，但填入實際的 songId */
    fun practice(songId: Long) = "practice/$songId"

    const val VOCAB_DETAIL = "vocab/{vocabId}"

    /** 對應 [VOCAB_DETAIL]，但填入實際的 vocabId */
    fun vocabDetail(vocabId: Long) = "vocab/$vocabId"

    const val ARG_VOCAB_ID = "vocabId"

    const val SETTINGS = "settings"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_PLAYBACK = "settings/playback"
    const val SETTINGS_LEARNING = "settings/learning"
    const val SETTINGS_DATA = "settings/data"
    const val ABOUT = "about"
    const val ONBOARDING = "onboarding"

    const val ARG_SONG_ID = "songId"
}
