package com.antigravity.lyrian.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.settings.PlaybackOffsets
import com.antigravity.lyrian.data.settings.PlaybackSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放設定。
 *
 * 三個都是**行為開關**，不是裝飾：
 *  - 自動播放：關掉後進頁只載入、不發聲（三點也不顯示，因為不會有第一句）
 *  - 淡入淡出：關掉後播放／暫停是直接切換（適合接耳機或錄音時）
 *  - 預設偏移：新歌的歌詞偏移起點；**每首歌調過就用自己的值**
 */
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    viewModel: PlaybackSettingsViewModel = hiltViewModel(),
) {
    val autoPlay by viewModel.autoPlay.collectAsStateWithLifecycle()
    val fadeAudio by viewModel.fadeAudio.collectAsStateWithLifecycle()
    val defaultOffsetMs by viewModel.defaultOffsetMs.collectAsStateWithLifecycle()
    val customOffsetCount by viewModel.customOffsetCount.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    val resetMessage = stringResource(R.string.playback_offsets_reset)

    SettingsScaffold(
        title = stringResource(R.string.settings_playback),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        SettingsList(modifier = Modifier.padding(padding)) {
            item {
                SettingsSection(title = stringResource(R.string.playback_behaviour_section)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.playback_autoplay),
                        subtitle = stringResource(R.string.playback_autoplay_hint),
                        checked = autoPlay,
                        onCheckedChange = viewModel::setAutoPlay,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.playback_fade),
                        subtitle = stringResource(R.string.playback_fade_hint),
                        checked = fadeAudio,
                        onCheckedChange = viewModel::setFadeAudio,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.playback_sync_section)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.playback_default_offset),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.playback_default_offset_value,
                                formatOffset(defaultOffsetMs),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        Slider(
                            value = defaultOffsetMs.toFloat(),
                            onValueChange = { raw ->
                                // 吸附到 0.5 秒：使用者不需要 1 毫秒的精度
                                val snapped = (raw / PlaybackOffsets.STEP_MS)
                                    .roundToLong() * PlaybackOffsets.STEP_MS
                                viewModel.setDefaultOffsetMs(snapped)
                            },
                            valueRange = -PlaybackSettings.MAX_DEFAULT_OFFSET_MS.toFloat()..
                                PlaybackSettings.MAX_DEFAULT_OFFSET_MS.toFloat(),
                            steps = OFFSET_STEPS,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )

                        Text(
                            text = stringResource(R.string.playback_default_offset_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsSection {
                    SettingsActionRow(
                        title = stringResource(R.string.playback_reset_offsets),
                        subtitle = if (customOffsetCount > 0) {
                            stringResource(R.string.playback_reset_offsets_count, customOffsetCount)
                        } else {
                            stringResource(R.string.playback_reset_offsets_none)
                        },
                        icon = Icons.Filled.RestartAlt,
                        enabled = customOffsetCount > 0,
                        onClick = { confirmReset = true },
                    )
                }
            }

            item {
                SettingsHint(stringResource(R.string.playback_offset_explainer))
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.playback_reset_offsets),
            message = stringResource(R.string.playback_reset_offsets_message),
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = {
                confirmReset = false
                viewModel.resetCustomOffsets()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = resetMessage,
                        withDismissAction = true,
                    )
                }
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** slider 的刻度數：±2 秒、每 0.5 秒一格 → 8 段、7 個中間刻度。 */
private const val OFFSET_STEPS = 7

/** 毫秒 → `+0.5 秒` / `0.0 秒`，帶正負號。 */
private fun formatOffset(ms: Long): String {
    val sign = if (ms > 0) "+" else ""
    return "$sign%.1f".format(ms / 1000.0)
}

/** 這一頁只有三個設定值。 */
@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor(
    private val settings: PlaybackSettings,
) : ViewModel() {

    val autoPlay: StateFlow<Boolean> = settings.autoPlay

    val fadeAudio: StateFlow<Boolean> = settings.fadeAudio

    val defaultOffsetMs: StateFlow<Long> = settings.defaultOffsetMs

    private val _customOffsetCount = MutableStateFlow(settings.customOffsetCount())

    val customOffsetCount: StateFlow<Int> = _customOffsetCount.asStateFlow()

    fun setAutoPlay(enabled: Boolean) = settings.setAutoPlay(enabled)

    fun setFadeAudio(enabled: Boolean) = settings.setFadeAudio(enabled)

    fun setDefaultOffsetMs(value: Long) = settings.setDefaultOffsetMs(value)

    fun resetCustomOffsets() {
        settings.clearCustomOffsets()
        _customOffsetCount.value = settings.customOffsetCount()
    }
}
