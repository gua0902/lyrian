package com.antigravity.lyrian.ui.screens.my

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.lyrian.R
import com.antigravity.lyrian.ui.components.LyrianTopAppBar
import com.antigravity.lyrian.ui.theme.LyrianMotion

/**
 * 「我的」頁。
 *
 * ## 這一頁的定位：儀表板，不是設定頁
 *
 * 只回答三個問題（擁有者指定的 PRD v2）：**我連續練了幾天、學了幾個字、
 * 今天還有多少要複習**。加上一個「今天的練習量」與一個「開始練習」按鈕——
 * 這是使用者進來最可能想做的事。
 *
 * ## 舊版被拿掉的东西
 *
 * - 「每日學習提醒」「學習紀錄」兩列——兩個都只是寫著「即將推出」的死入口。
 *   一個按了沒反應的列，比沒有那一列更糟：使用者會以為 App 壞了。
 *   真的要做時再連同通知權限、歷史頁一起加回來。
 * - 一組「保留給未來圖示」的 `unusedIcons`（那是為了消 unused import 警告而存在的
 *   假資料，現在不需要）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(
    onOpenSettings: () -> Unit,
    onStartPractice: () -> Unit,
    viewModel: MyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            // 這一頁是儀表板（固定卡片、不需要長捲動），用小型 Top App Bar；
            // 首頁與字庫是清單，才用「大標題會收合」的版本。
            LyrianTopAppBar(title = stringResource(R.string.tab_my))
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StreakCard(streakDays = state.streakDays, week = state.weekActivity)

            // 2×2 統計。原本「今天待複習」單獨佔一整列，長得比旁邊兩張卡片大，
            // 看起來像重點——但它只是四個數字裡的一個。
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    value = state.totalScore,
                    label = stringResource(R.string.my_total_score),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = state.masteredWords,
                    label = stringResource(R.string.my_mastered_words),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    value = state.dueForReview,
                    label = stringResource(R.string.my_due_today),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = state.todayAttempts,
                    label = stringResource(R.string.my_today_practice),
                    modifier = Modifier.weight(1f),
                )
            }

            FilledTonalButton(
                onClick = onStartPractice,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.my_action_practice))
            }

            SettingsRow(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                onClick = onOpenSettings,
            )

            Text(
                text = stringResource(R.string.my_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** 連續學習天數 + 本週週曆。 */
@Composable
private fun StreakCard(streakDays: Int, week: List<Boolean>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.my_streak, streakDays),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val labels = listOf("一", "二", "三", "四", "五", "六", "日")
                week.forEachIndexed { index, active ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (active) {
                                    Icon(
                                        imageVector = Icons.Filled.Bookmarks,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = labels[index],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: Int, label: String, modifier: Modifier = Modifier) {
    // 數字變動時用滑動的（例如練習完回到這一頁），不是直接跳到新值
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = LyrianMotion.emphasized(),
        label = "statValue",
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = animated.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
