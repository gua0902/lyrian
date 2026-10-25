package com.antigravity.lyrian.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.settings.AppearanceSettings
import com.antigravity.lyrian.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * 外觀設定：**主題模式** 與 **動態配色**。
 *
 * ## 為什麼主題是三選一而不是一個開關
 *
 * 「深色模式」開關只有兩種狀態，但使用者的需求有三種：
 * 跟隨系統（白天淺、晚上深）、固定淺色（日照下）、固定深色（睡前）。
 * M3 把它做成 segmented button（相鄰選項一組、單選）而不是三張卡片，
 * 因為這裡的選項**互斥且短**，卡片會讓清單變得很長。
 */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: AppearanceSettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

    // Android 12 才有 Material You 的系統色票；舊裝置直接顯示不支援，
    // 不讓它看起來「有效但沒反應」。
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SettingsScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
        snackbarHostState = remember { SnackbarHostState() },
    ) { padding ->
        SettingsList(modifier = Modifier.padding(padding)) {
            item {
                SettingsSection(title = stringResource(R.string.appearance_theme_section)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.appearance_theme_title),
                            style = MaterialTheme.typography.titleMedium,
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = option == themeMode,
                                    onClick = { viewModel.setThemeMode(option) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size,
                                    ),
                                    // 選取中的那一格多一個打勾，圖示仍保留（幫助掃讀）
                                    icon = {
                                        SegmentedButtonDefaults.Icon(
                                            active = option == themeMode,
                                        ) {
                                            Icon(
                                                imageVector = option.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    label = { Text(stringResource(option.labelRes)) },
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.appearance_theme_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.appearance_color_section)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.appearance_dynamic_color),
                        subtitle = if (supportsDynamicColor) {
                            stringResource(R.string.appearance_dynamic_color_hint)
                        } else {
                            stringResource(R.string.appearance_dynamic_color_unsupported)
                        },
                        checked = dynamicColor && supportsDynamicColor,
                        enabled = supportsDynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) },
                    )
                }
            }

            item {
                SettingsHint(stringResource(R.string.appearance_cover_color_hint))
            }

            item {
                SettingsSection(title = stringResource(R.string.appearance_note_section)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.appearance_note_color),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.appearance_note_shape),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** segmented button 左側的圖示。 */
private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Filled.LightMode
        ThemeMode.DARK -> Icons.Filled.DarkMode
    }

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.appearance_theme_system
        ThemeMode.LIGHT -> R.string.appearance_theme_light
        ThemeMode.DARK -> R.string.appearance_theme_dark
    }

/** 這一頁只有兩個設定值，用最薄的 ViewModel 把 [AppearanceSettings] 接出來。 */
@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val settings: AppearanceSettings,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor

    fun setThemeMode(value: ThemeMode) = settings.setThemeMode(value)

    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
}
