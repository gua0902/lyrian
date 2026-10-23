package com.antigravity.lyrian.ui.screens.settings

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
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.practice.BlankCoverage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * 學習設定：**挖空的最低 CEFR 等級**。
 *
 * 擁有者指定預設 B1，選項只給 A1–B2（CEFR-J 的範圍）。C1/C2 的資料其實存在
 * （Octanove Vocabulary Profile），但依規格先不開放——選到 C2 之後幾乎挖不出字，
 * 那不是使用者想要的結果。
 *
 * 改完後**下一次組題**就套用（進任何一首歌的練習頁時重新計算）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningSettingsScreen(
    onBack: () -> Unit,
    viewModel: LearningSettingsViewModel = hiltViewModel(),
) {
    val current by viewModel.minLevel.collectAsStateWithLifecycle()
    val coverage by viewModel.coverage.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_learning_level)) },
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
                .padding(16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_learning_level_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CefrLevel.SELECTABLE.forEach { level ->
                LevelOption(
                    level = level,
                    selected = level == current,
                    onSelect = { viewModel.setMinLevel(level) },
                )
            }

            Text(
                text = stringResource(R.string.settings_blank_level_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            // ---- 每首要挖幾句（5 / 10 / 全部）----
            Text(
                text = stringResource(R.string.settings_blank_coverage),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.settings_blank_coverage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BlankCoverage.entries.forEach { option ->
                CoverageOption(
                    label = option.label,
                    selected = option == coverage,
                    onSelect = { viewModel.setCoverage(option) },
                )
            }
        }
    }
}

@Composable
private fun CoverageOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun LevelOption(level: CefrLevel, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
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
                Text(text = level.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = level.description,
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

    val minLevel: StateFlow<CefrLevel> = settings.minLevel

    val coverage: StateFlow<BlankCoverage> = settings.coverage

    fun setMinLevel(level: CefrLevel) = settings.setMinLevel(level)

    fun setCoverage(value: BlankCoverage) = settings.setCoverage(value)
}
