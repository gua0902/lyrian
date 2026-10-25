package com.antigravity.lyrian.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.local.DataCounts
import com.antigravity.lyrian.data.local.UserDataRepository
import com.antigravity.lyrian.util.Sizes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 資料與儲存。
 *
 * ## 為什麼每一個清除都要確認
 *
 * 這個 App **沒有帳號、沒有雲端備份**——刪掉的東西真的就沒了。
 * 所以：
 *  - 每一項都寫清楚「會刪什麼、不會刪什麼」（副標題）
 *  - 破壞性按鈕用 error 色
 *  - 一律經過對話框
 *  - 清完立刻更新數字與檔案大小（使用者要看到「真的變少了」）
 *
 * 字庫與練習紀錄**分開**：只想重練的人不該被迫放棄累積的字庫。
 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    viewModel: DataSettingsViewModel = hiltViewModel(),
) {
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val bytes by viewModel.databaseBytes.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pending by remember { mutableStateOf<DataAction?>(null) }

    val messages = DataMessages(
        history = stringResource(R.string.data_cleared_history),
        wordbank = stringResource(R.string.data_cleared_wordbank),
        songs = stringResource(R.string.data_cleared_songs),
        all = stringResource(R.string.data_cleared_all),
        failed = stringResource(R.string.data_clear_failed),
    )

    SettingsScaffold(
        title = stringResource(R.string.settings_data),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        SettingsList(modifier = Modifier.padding(padding)) {
            item {
                SettingsSection(title = stringResource(R.string.data_storage_section)) {
                    SettingsValueRow(
                        title = stringResource(R.string.data_songs),
                        value = counts.songs.toString(),
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        title = stringResource(R.string.data_words),
                        value = counts.words.toString(),
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        title = stringResource(R.string.data_attempts),
                        value = counts.attempts.toString(),
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        title = stringResource(R.string.data_database_size),
                        value = Sizes.formatBytes(bytes),
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.data_clear_section)) {
                    SettingsActionRow(
                        title = stringResource(R.string.data_clear_history),
                        subtitle = stringResource(R.string.data_clear_history_hint),
                        icon = Icons.Filled.History,
                        destructive = true,
                        enabled = counts.attempts > 0,
                        onClick = { pending = DataAction.CLEAR_HISTORY },
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = stringResource(R.string.data_clear_wordbank),
                        subtitle = stringResource(R.string.data_clear_wordbank_hint),
                        icon = Icons.Filled.DeleteOutline,
                        destructive = true,
                        enabled = counts.words > 0,
                        onClick = { pending = DataAction.CLEAR_WORDBANK },
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = stringResource(R.string.data_clear_songs),
                        subtitle = stringResource(R.string.data_clear_songs_hint),
                        icon = Icons.Filled.LibraryMusic,
                        enabled = counts.songs > 0,
                        onClick = { pending = DataAction.CLEAR_SONGS },
                    )
                }
            }

            item {
                SettingsHint(stringResource(R.string.data_local_only_hint))
            }

            item {
                SettingsSection(title = stringResource(R.string.data_danger_section)) {
                    SettingsActionRow(
                        title = stringResource(R.string.data_clear_all),
                        subtitle = stringResource(R.string.data_clear_all_hint),
                        icon = Icons.Filled.DeleteForever,
                        destructive = true,
                        enabled = counts.songs > 0 || counts.words > 0 || counts.attempts > 0,
                        onClick = { pending = DataAction.CLEAR_ALL },
                    )
                }
            }

            item {
                SettingsSection {
                    SettingsActionRow(
                        title = stringResource(R.string.data_ai_keys_note_title),
                        subtitle = stringResource(R.string.data_ai_keys_note),
                        icon = Icons.Filled.WarningAmber,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }

    pending?.let { action ->
        ConfirmDialog(
            title = stringResource(action.titleRes),
            message = stringResource(action.messageRes),
            confirmLabel = stringResource(R.string.data_clear_confirm),
            destructive = true,
            onConfirm = {
                pending = null
                scope.launch {
                    val ok = runCatching { viewModel.run(action) }.isSuccess
                    snackbarHostState.showSnackbar(
                        message = if (ok) messages.of(action) else messages.failed,
                        withDismissAction = true,
                    )
                }
            },
            onDismiss = { pending = null },
        )
    }
}

/** 這一頁能做的四種清除。用 enum 而不用四個 boolean，對話框才只需要一個狀態。 */
internal enum class DataAction {
    CLEAR_HISTORY,
    CLEAR_WORDBANK,
    CLEAR_SONGS,
    CLEAR_ALL,
    ;

    @get:StringRes
    val titleRes: Int
        get() = when (this) {
            CLEAR_HISTORY -> R.string.data_clear_history
            CLEAR_WORDBANK -> R.string.data_clear_wordbank
            CLEAR_SONGS -> R.string.data_clear_songs
            CLEAR_ALL -> R.string.data_clear_all
        }

    @get:StringRes
    val messageRes: Int
        get() = when (this) {
            CLEAR_HISTORY -> R.string.data_clear_history_message
            CLEAR_WORDBANK -> R.string.data_clear_wordbank_message
            CLEAR_SONGS -> R.string.data_clear_songs_message
            CLEAR_ALL -> R.string.data_clear_all_message
        }
}

/** 對話框的標題、訊息、確認鍵。 */
private data class ActionSpec(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    @StringRes val confirmRes: Int,
)

/** snackbar 的字串（先取好，協程裡才不用碰 `stringResource`）。 */
private data class DataMessages(
    val history: String,
    val wordbank: String,
    val songs: String,
    val all: String,
    val failed: String,
) {
    fun of(action: DataAction): String = when (action) {
        DataAction.CLEAR_HISTORY -> history
        DataAction.CLEAR_WORDBANK -> wordbank
        DataAction.CLEAR_SONGS -> songs
        DataAction.CLEAR_ALL -> all
    }
}

@HiltViewModel
class DataSettingsViewModel @Inject constructor(
    private val repository: UserDataRepository,
) : ViewModel() {

    /** 三個數量。離開畫面就停止收集，回來時重抓（設定頁不需要背景即時更新）。 */
    val counts: StateFlow<DataCounts> = repository.observeCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataCounts())

    private val _databaseBytes = MutableStateFlow(repository.databaseBytes())

    val databaseBytes: StateFlow<Long> = _databaseBytes.asStateFlow()

    /** 執行清除並刷新檔案大小。UI 負責顯示結果。 */
    internal suspend fun run(action: DataAction) {
        when (action) {
            DataAction.CLEAR_HISTORY -> repository.clearPracticeHistory()
            DataAction.CLEAR_WORDBANK -> repository.clearWordbank()
            DataAction.CLEAR_SONGS -> repository.clearSongCache()
            DataAction.CLEAR_ALL -> repository.clearEverything()
        }
        _databaseBytes.value = repository.databaseBytes()
    }
}
