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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.lyrian.R
import com.antigravity.lyrian.ui.theme.LyrianMotion

/**
 * 「我的」頁。
 *
 * 依 PRD v2：**只留連勝、學會幾個字、今天待複習**，右上角齒輪進設定。
 * 刻意不塞其他資訊。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(
    onOpenSettings: () -> Unit,
    viewModel: MyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tab_my)) })
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

            StatCard(
                value = state.dueForReview,
                label = stringResource(R.string.my_due_today),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.my_section_learning),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            SettingsRow(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                onClick = onOpenSettings,
            )
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_daily_reminder),
                subtitle = stringResource(R.string.settings_coming_soon),
                onClick = {},
                enabled = false,
            )
            SettingsRow(
                icon = Icons.Filled.History,
                title = stringResource(R.string.settings_history),
                subtitle = stringResource(R.string.settings_coming_soon),
                onClick = {},
                enabled = false,
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
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
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
            .then(
                if (enabled) Modifier.clickableRow(onClick) else Modifier,
            ),
    )
}

/** ListItem 本身不吃 onClick，這裡補上。 */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/** 保留給尚未使用的圖示，避免 unused import 警告。 */
@Suppress("unused")
private val unusedIcons = listOf(Icons.Filled.Person, Icons.Filled.Bookmarks)
