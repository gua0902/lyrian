package com.antigravity.lyrian.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.settings.UserSettings
import com.antigravity.lyrian.domain.practice.BlankDifficulty
import com.antigravity.lyrian.domain.practice.BlankSelector
import com.antigravity.lyrian.ui.theme.LyrianMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * 學習設定：**挖空難度** 與 **題數上限**。
 *
 * 擁有者 2026-10 核定的語意：
 *  - 難度只決定「哪些字值得挖」，**不影響題數**
 *  - 題數由 `BlankCountPolicy` 決定：最少 20 題、句子多時按 0.6 倍增加
 *  - 這裡的滑桿是**上限**（20–40），不是目標值
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningSettingsScreen(
    onBack: () -> Unit,
    viewModel: LearningSettingsViewModel = hiltViewModel(),
) {
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    val maxBlanks by viewModel.maxBlanks.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_blank_difficulty)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_blank_difficulty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BlankDifficulty.entries.forEach { option ->
                    DifficultyOption(
                        difficulty = option,
                        selected = option == difficulty,
                        onSelect = { viewModel.setDifficulty(option) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_blank_difficulty_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- 題數上限（滑桿）----
            Text(
                text = stringResource(R.string.settings_blank_count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.settings_blank_count_value, maxBlanks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = maxBlanks.toFloat(),
                onValueChange = { viewModel.setMaxBlanks(it.toInt()) },
                valueRange = UserSettings.MIN_BLANKS.toFloat()..BlankSelector.MAX_BLANKS.toFloat(),
                steps = BlankSelector.MAX_BLANKS - UserSettings.MIN_BLANKS - 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.settings_blank_count_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DifficultyOption(
    difficulty: BlankDifficulty,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    // 選取狀態是「淡入另一個顏色」，不是瞬間換底
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = LyrianMotion.emphasized(),
        label = "difficultyContainer",
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = LyrianMotion.emphasized(),
        label = "difficultyContent",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // onClick = null：整張卡片已經是點擊目標，讀屏才不會重複宣告
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = difficulty.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    text = difficulty.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 這一頁只有兩個設定值，用最薄的 ViewModel 把 [UserSettings] 接出來。 */
@HiltViewModel
class LearningSettingsViewModel @Inject constructor(
    private val settings: UserSettings,
) : ViewModel() {

    val difficulty: StateFlow<BlankDifficulty> = settings.difficulty

    val maxBlanks: StateFlow<Int> = settings.maxBlanks

    fun setDifficulty(value: BlankDifficulty) = settings.setDifficulty(value)

    fun setMaxBlanks(value: Int) = settings.setMaxBlanks(value)
}
