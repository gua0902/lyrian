package com.antigravity.lyrian.ui.screens.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.data.local.AttemptDao
import com.antigravity.lyrian.data.local.AttemptEntity
import com.antigravity.lyrian.data.local.DailyStatDao
import com.antigravity.lyrian.data.local.LyricLineEntity
import com.antigravity.lyrian.data.local.LyricLineDao
import com.antigravity.lyrian.data.local.SongDao
import com.antigravity.lyrian.data.local.SongEntity
import com.antigravity.lyrian.data.local.VocabDao
import com.antigravity.lyrian.data.active.PracticeSessionStore
import com.antigravity.lyrian.data.audio.YouTubeCandidateScorer
import com.antigravity.lyrian.data.audio.YouTubePlayerController
import com.antigravity.lyrian.data.audio.YouTubeWebSearch
import com.antigravity.lyrian.data.dictionary.DictionaryRepository
import com.antigravity.lyrian.data.dictionary.DictionaryResult
import com.antigravity.lyrian.data.lexicon.LexiconSource
import com.antigravity.lyrian.data.lyrics.LyricsParser
import com.antigravity.lyrian.data.settings.PlaybackOffsets
import com.antigravity.lyrian.data.settings.PlaybackSettings
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.ChoiceRecord
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine
import com.antigravity.lyrian.domain.model.PracticeModeId
import com.antigravity.lyrian.domain.model.ResultWord
import com.antigravity.lyrian.domain.practice.BlankPicker
import com.antigravity.lyrian.domain.practice.FillChoiceBuilder
import com.antigravity.lyrian.domain.practice.LineBoundary
import com.antigravity.lyrian.ui.navigation.Routes
import com.antigravity.lyrian.util.toDayKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 練習頁的 UI 狀態。
 *
 * 用單一 data class 讓狀態空間封閉——「已答完但還在作答中」這類矛盾狀態不存在。
 */
data class PracticeUiState(
    val songTitle: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    val lines: List<LyricLine> = emptyList(),
    val questions: List<FillChoiceQuestion> = emptyList(),
    val activeBlankIndex: Int = 0,
    /** blankIndex → 作答結果 */
    val selections: Map<Int, ChoiceRecord> = emptyMap(),
    /** 當前題的 4 個選項 */
    val options: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    // ---------- 音訊 ----------

    /** 正在解析音源（搜尋 YouTube Music → 取串流 URL）。 */
    val isAudioLoading: Boolean = false,
    /** 音源已就緒，可以播放。 */
    val isAudioReady: Boolean = false,
    /** 播放器是否正在播放。 */
    val isPlaying: Boolean = false,
    /** 正在緩衝。 */
    val isBuffering: Boolean = false,
    /** 播放位置（毫秒）。 */
    val playbackPositionMs: Long = 0L,
    /** 曲目總長（毫秒）；未知時為 null。 */
    val playbackDurationMs: Long? = null,
    /**
     * 目前**唱到**的歌詞行索引；沒有時間戳時為 null。
     *
     * 這與 [activeBlankIndex]（作答進度）是**兩條獨立的軸**：
     * 前者來自音訊時間，後者來自使用者答到哪。
     */
    val singingLineIndex: Int? = null,
    /**
     * 目前是否由**歌曲時間軸**驅動節奏。
     *
     * true 表示歌詞與題目跟著播放時間走（擁有者指定的新模式）；
     * false 表示沒有可用音源，退回「依作答進度推進」。
     */
    val isSongDriven: Boolean = false,
    /**
     * 因為「上一句還沒作答」而自動暫停時，正在等哪一個空格。
     *
     * null 表示沒有在等。作答完成後會清空並淡入恢復播放。
     */
    val waitingForBlankIndex: Int? = null,
    /** 音源相關的提示（例如「找不到對應的錄音版本」）。 */
    val audioNotice: String? = null,    /**
     * 播放器本身回報的錯誤。
     *
     * 與 [audioNotice] **分開存放**：播放器是單例，它的錯誤會一直留在狀態裡，
     * 若混進 [audioNotice] 就會讓「上一首歌的播放錯誤」黏到下一首歌上。
     * 這個欄位每次載入新歌時都會清空。
     */
    val playbackError: String? = null,

    /**
     * 三點動畫：**只代替「目前還沒唱到的那一行」**，不是整頁載入狀態。
     *
     * ```
     * true  音訊還沒進到第一句（或還沒開始）→ 那一行顯示三點
     * false 已經進到該行的時間範圍（或沒有時間軸／音訊失敗）→ 正常顯示歌詞
     * ```
     *
     * 擁有者明確要求：三點**不得**控制整個練習頁的生命週期——
     * 歌詞、題目、選項、音訊、時間軸在進入頁面時就全部就緒，
     * 三點只是那一行的視覺替身。
     */
    val awaitingFirstLine: Boolean = false,

    /** 歌曲是否已經播完（**結果頁的唯一觸發條件**）。 */
    val songFinished: Boolean = false,

    /** 使用者為這首歌設定的歌詞偏移（毫秒）。正值＝歌詞延後。 */
    val lyricOffsetMs: Long = 0L,
    /** 目前播放的是候選清單中的第幾個版本（0-based）。 */
    val playerVersionIndex: Int = 0,
    /** 候選版本總數；<= 1 時不顯示「換版本」。 */
    val playerVersionCount: Int = 0,
    /** 影片長寬比（寬/高）；用來把播放器塞進封面框。 */
    val videoAspectRatio: Float? = null,

    // ---------- 結果頁（模組 2）----------

    /** 這一輪開始的時間（第一次組完題）。用來算「用時」。 */
    val sessionStartedAtMs: Long = 0L,
    /** 用時（毫秒）；尚未完成時為 null。 */
    val elapsedMs: Long? = null,
    /**
     * 本首歌的 B1+ 較難單字（**只顯示，不會自動寫入字庫**）。
     *
     * 資料來源是本次選詞的候選池：所有通過 B1 門檻、去重、非專名的字，
     * 不論有沒有被挖空。要收藏得由使用者按「加入」。
     */
    val difficultWords: List<ResultWord> = emptyList(),
    /** 候選池的原始資料（結果頁組字表用，不落庫）。 */
    val pool: List<BlankPicker.Candidate> = emptyList(),
    /**
     * 距離 20 個挖空還差幾個（0 = 達標）。
     *
     * 依規格：不足時如實回報，**不補 A2、不補停用詞、不補專名**。
     */
    val shortfall: Int = 0,
    /**
     * 歌詞是否有時間軸。
     *
     * 有的話**歌詞行只能由 `currentTime` 推進**，作答不能讓行號前進
     * （擁有者指定的模組 B）。沒有時間軸時才退回「答完自動下一題」。
     */
    val hasTimeline: Boolean = false,
    /**
     * AI 是否已啟用。
     *
     * 本輪 AI 一律是「未啟用」（模組 6 只做設定儲存），因此結果頁的
     * 「值得學的表達」區塊整塊隱藏——擁有者指定 AI 未啟用時不顯示空殼。
     */
    val aiConfigured: Boolean = false,
) {
    val currentQuestion: FillChoiceQuestion?
        get() = questions.getOrNull(activeBlankIndex)

    val currentRecord: ChoiceRecord?
        get() = currentQuestion?.let { selections[it.blankIndex] }

    /**
     * 全部答完**而且歌曲播完**才進結果頁。
     *
     * ⚠️ 判斷依據是**答了幾題**，不是 [activeBlankIndex]。
     * 歌曲驅動時題目軸由播放時間決定，最後一句唱完之後 index 不會再增加——
     * 用 index 判斷的話，跟著歌唱歌永遠不會「完成」，也就永遠看不到結果頁。
     *
     * ⚠️ 擁有者修正：答完最後一題**不可以**立刻跳頁，要繼續播到歌曲結束
     * （[songFinished]）。只有在完全沒有音源時（無法得知歌曲結束）才退回
     * 「答完即完成」。
     */
    val isComplete: Boolean
        get() = questions.isNotEmpty() &&
            selections.size >= questions.size &&
            (songFinished || !isAudioReady)

    val correctCount: Int get() = selections.values.count { it.isCorrect }

    /**
     * 還沒作答的題目，依歌詞順序。
     *
     * 這是模組 B 的單一真相來源：暫停判斷、追回判斷、進度條刻度全都看它。
     * 已作答的題目**永遠不會**從這裡消失（換行、換版本、暫停都不會清 `selections`）。
     */
    val unansweredQuestions: List<FillChoiceQuestion>
        get() = questions.filterNot { selections.containsKey(it.blankIndex) }

    /** 最早一個尚未作答的題目；全部答完時為 null。 */
    val earliestUnanswered: FillChoiceQuestion?
        get() = unansweredQuestions.minByOrNull { it.lineIndex }

    /**
     * 已作答的**最後一行**索引；尚未作答任何一題時為 -1。
     *
     * 給歌詞捲動判斷「這題答完了沒」（見 `LyricFollowController`）：
     * 使用者還在思考時不能搶畫面，所以要比較「想捲過去的行」與這個值。
     */
    val lastAnsweredLineIndex: Int
        get() = questions
            .filter { selections.containsKey(it.blankIndex) }
            .maxOfOrNull { it.lineIndex }
            ?: -1

    val total: Int get() = questions.size

    val percent: Int get() = if (total > 0) Math.round(correctCount * 100f / total) else 0
}

/**
 * 三點動畫的顯示門檻。
 *
 * 擁有者指定：若第一句歌詞**距離影片開頭不到 5 秒**，等一下就唱到了，
 * 不需要倒數動畫——直接正常顯示歌詞（三點透明度拉到最低＝等於不顯示）。
 */
private const val DOTS_MIN_WAIT_MS = 5_000L

/** 三點最多顯示多久；超過就放棄等待、正常顯示歌詞（不得永遠停在三點）。 */
private const val DOTS_MAX_MS = 20_000L

/** 找不到練習資料。使用者唯一的出路是返回。 */
private const val ERROR_NO_SESSION = "找不到這首歌的練習資料，請重新搜尋。"

/** 有歌詞，但沒有任何一行可以挖空（純演奏曲、或全是很短的虛詞）。 */
private const val ERROR_NO_BLANKS = "這首歌沒有可挖空的歌詞。"

/** 載入過程中發生未預期的錯誤（例外已被攔下，不讓它殺掉 App）。 */
private const val ERROR_LOAD_FAILED = "載入這首歌時發生問題，請按返回重新選擇。"

/** 在 YouTube Music 上搜尋不到可配對的版本。 */
private const val NO_AUDIO_MATCH = "找不到這首歌對應的錄音版本，只能純歌詞練習。"

/** 找到了，但時長與歌詞時間戳差距過大，可能不是同一個錄音。 */
private const val AUDIO_MAY_NOT_ALIGN =
    "找到的版本長度與歌詞不符，歌詞可能會有些對不上。"

/**
 * 播放位置的取樣間隔。
 *
 * Media3 沒有「位置變化」的 callback（位置是連續值，每帧回報太吵），
 * 因此以固定間隔取樣。50ms 足以讓歌詞高亮跟得上歌聲，又不會浪費 CPU。
 */
private const val POSITION_POLL_MS = 50L

/**
 * 「上一句沒作答就暫停」的寬限時間。
 *
 * 擁有者指定 0.5 秒：畫面捲到下一句後 0.5 秒內補上就不算錯過。
 * 短於此會來不及反應，長於此就失去「跟上歌曲」的壓力。
 */

/** 音量淡出／淡入的時間。擁有者指定 0.5 秒（先漸出完才 pause，避免突兀截斷）。 */
private const val FADE_OUT_MS = 500L

/**
 * 交界暫停後，位置與「該行結束時間」的容許誤差。
 *
 * 淡出的 0.5 秒會讓播放多走一段，超過這個量才把時間拉回交界；
 * 太小會頻繁 seek（播放器會抖），太大會讓下一句真的被播掉一小段。
 */
private const val BOUNDARY_SNAP_TOLERANCE_MS = 250L

/** 因為等作答而暫停時顯示的提示。 */
private const val WAITING_FOR_ANSWER = "已暫停，補上這句的填空就會繼續播放。"

/** 判定「歌曲播完」的容差：`currentTime >= duration - 1 秒`（擁有者指定）。 */
private const val SONG_END_TOLERANCE_MS = 1_000L

/** 拖曳放手時，往前找未答挖空的容許誤差（避免差幾十毫秒就跳過該題）。 */
private const val SNAP_TOLERANCE_MS = 1_500L

/** 所有候選版本都不允許嵌入時顯示的提示（模組 A.5）。 */
private const val EMBED_BLOCKED = "這些版本都不允許嵌入播放，換一首歌試試。"

/**
 * 自動入庫的門檻。
 *
 * ```
 * 等級 >= B1   或   學習價值分數 >= 60
 * ```
 *
 * 加入分數條件是因為 2026-10 的「保量」政策：題數固定 20 以上，
 * 嚴格池不夠時會補進較簡單的字。若只看等級，`desperate`（B1，分數 67）
 * 進得去、但補題進來的 A2 實詞（分數 70，例如 `beg`）反而進不去——
 * 那與「字庫＝值得學的清單」的定義相反。
 */
private val WORDBANK_MIN_LEVEL = CefrLevel.B1

private const val WORDBANK_MIN_SCORE = 60

private const val TAG = "LyrianPractice"

/** 歌曲的基本資訊。與歌詞來源分開，因為兩層退路取到的方式不同。 */
private data class SongHead(
    val songId: Long,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    /**
     * LRCLIB 的時長（毫秒）。
     *
     * **音源配對的關鍵**：用來在 YouTube Music 的眾多版本
     * （錄音室／現場／MV／remaster）中挑出同一個錄音。見 `AudioTrackMatcher`。
     */
    val durationMs: Long? = null,
)

/** 一份完整的練習資料來源：歌曲資訊 + 歌詞行。 */
private data class SongSource(
    val head: SongHead,
    val lines: List<LyricLine>,
)

/**
 * 練習頁的 ViewModel。
 *
 * 資料來源是 [PracticeSessionStore]（搜尋頁選歌時寫入），
 * 因此**不需要重新打 API**——歌詞在搜尋時就已取得。
 *
 * **載入有兩層退路**，見 [load]：記憶體 session → Room 快取。
 */
@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val sessionStore: PracticeSessionStore,
    private val songDao: SongDao,
    private val lyricLineDao: LyricLineDao,
    private val attemptDao: AttemptDao,
    private val vocabDao: VocabDao,
    private val dailyStatDao: DailyStatDao,
    private val youTubeSearch: YouTubeWebSearch,
    private val playbackOffsets: PlaybackOffsets,
    private val playbackSettings: PlaybackSettings,
    private val lexicon: LexiconSource,
    private val dictionary: DictionaryRepository,
    val player: YouTubePlayerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    /** 歌詞只在第一次作答時寫入快取，避免每次作答都 delete + insert。 */
    private var lyricsPersisted = false
    private var localSongId: Long? = null

    /**
     * 目前這首歌的基本資訊。
     *
     * 由 [applySource] 填入，供 [ensureSongPersisted] 使用。
     * **不從 `sessionStore` 讀**——程序死亡後回復時那裡是空的。
     */
    private var songHead: SongHead? = null

    /** 全部題目的選項**預先算好**，避免每次換題重算（也讓上下題的選項穩定）。 */
    private var optionsByBlank: Map<Int, List<String>> = emptyMap()

    // ---------- 音訊 ----------

    /** 是否已經搜尋過音源（避免每次按播放都重打 YouTube）。 */
    private var audioResolved = false

    /** 排序後的候選版本；「換版本」就是換這份清單的索引。 */
    private var candidates: List<YouTubeCandidateScorer.Scored> = emptyList()

    /**
     * 正在淡出／暫停的旗標。
     *
     * 暫停要花 0.5 秒淡出，這段時間輪詢還會繼續跑；沒有這個旗標就會重複觸發暫停。
     */
    private var pausing = false

    /**
     * 上一次輪詢時「正在唱」的行。
     *
     * 交界檢查靠它判斷「剛剛離開的是哪一行」：時間軸一跨過 **A 的結束時間**
     * （＝ B 的開始時間），這一輪的 `currentLine` 就變成 B，而 [lastSeenLine]
     * 還是 A——那一刻正是我們要檢查 A 的時機。
     */
    private var lastSeenLine: Int? = null

    /**
     * 已經檢查過交界的行。
     *
     * 擁有者要求「每行只檢查一次」：淡出後的位移校正、使用者手動播放、
     * 換版本的 `seekTo` 都會讓同一行的交界被反覆經過，沒有這個集合就會
     * 反覆暫停。
     */
    private val checkedLines = mutableSetOf<Int>()

    /** 三點動畫的保底計時器（20 秒後無論如何都要顯示歌詞）。 */
    private var dotsTimeoutJob: kotlinx.coroutines.Job? = null

    /**
     * 音量漸入的工作。
     *
     * 擁有者要求「0.5 秒內又觸發暫停 → 立即中止漸入」，所以必須握得住它。
     */
    private var fadeInJob: kotlinx.coroutines.Job? = null

    /** 進行中的「交界暫停」協程；補答或手動播放時要取消它（見 [pauseAtBoundary]）。 */
    private var pauseJob: kotlinx.coroutines.Job? = null

    /**
     * ⚠️ **`init` 必須在所有欄位初始器之後**。
     *
     * `viewModelScope` 用的是 `Dispatchers.Main.immediate`：只要建立 ViewModel 時
     * 已經在主執行緒，`launch` 會**同步**跑完。而 `load()` 在語言資料已快取時
     * 不會有任何掛起點（`languageData.get()` 立刻回傳），於是整條
     * `load → applySource` 會在欄位還沒初始化完時執行。
     *
     * 這是實機閃退的真因：`applySource` 碰到宣告在 `init` 之後的 `checkedLines`
     * 時它還是 null → `NullPointerException`。第一次進練習頁不會當（首次載入
     * 語言資料一定會掛起），**第二次以後必當**，所以看起來像「有時會閃退」。
     */
    init {
        val songId = savedStateHandle.get<String>(Routes.ARG_SONG_ID)?.toLongOrNull()
        viewModelScope.launch { load(songId) }
        observePlayback()
    }

    /**
     * 把播放器的狀態同步進 UI 狀態。
     *
     * 播放器是**單例**（旋轉螢幕不中斷音樂），所以它的狀態可能比這個
     * ViewModel 還早存在。因此除了收集 flow，也要立刻讀一次目前值。
     */
    private fun observePlayback() {
        viewModelScope.launch {
            player.state.collect { playback ->
                _uiState.update {
                    it.copy(
                        isPlaying = playback.isPlaying,
                        isBuffering = playback.isBuffering,
                        playbackPositionMs = playback.positionMs,
                        playbackDurationMs = playback.durationMs,
                        playbackError = playback.error,
                        videoAspectRatio = playback.videoAspectRatio,
                    )
                }

                // 版權方關掉嵌入（101/150 → playabilityStatus UNPLAYABLE）是最常見的失敗。
                // 自動換下一個候選；全部用完才告訴使用者「此版本不允許嵌入」。
                if (playback.embedBlocked) {
                    onSwitchVersion(auto = true)
                }
            }
        }

        // 播放中才輪詢位置。
        //
        // ⚠️ **暫停時不更新位置**——這正是「暫停期間歌詞時間軸同步凍結」的實作。
        // ⚠️ 位置要加上使用者微調的 offset 才拿去比對歌詞時間戳（調整立即生效）。
        //
        // ## 三條正交的因果（模組 B-1）
        //
        // ```
        // 換行 → 只由播放時間決定（這裡）
        // 暫停 → 只由「剛播完的那一行還沒作答」決定（onLeftLine）
        // 作答 → 只解除暫停，不改換行（onSelectOption）
        // ```
        //
        // ## 檢查點在**句子交界**，不是句子開始（擁有者修正）
        //
        // ```
        // A 播放中 → 顯示 A 的挖空，可邊聽邊填
        // A→B 交界 → 檢查 A：答了就直接播 B；沒答才漸出 0.5 秒後停在交界
        // ```
        // 舊版是「一進入含挖空的新句子就暫停」，使用者還沒聽到就被要求作答。
        // 差別只在檢查的對象：**離開的那一行**（= 剛播完的那句），不是進來的這一行。
        viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                // `waitingForBlankIndex != null` 也一起擋：暫停後播放器可能還有一兩次
                // 在途的 tick 會回報「還在播」，那段空窗若不擋，交界檢查剛設好的
                // 焦點（停在剛播完的那一行）會被下一行的資訊蓋掉。
                if (state.isPlaying && !pausing && state.waitingForBlankIndex == null) {
                    val position = player.currentPositionMs()
                    val lyricPosition = position + state.lyricOffsetMs
                    val currentLine = lineIndexAt(lyricPosition)

                    // ---- 三點動畫：只影響「那一行」的呈現 ----
                    // 音訊真的進到某一行的時間範圍（或根本沒有時間軸）就收掉三點。
                    // ⚠️ 這一段**不 continue**：歌詞同步、交界檢查、結束判定
                    // 全部照常跑，三點不得控制頁面生命週期。
                    if (state.awaitingFirstLine &&
                        (currentLine != null || !state.hasTimeline)
                    ) {
                        dotsTimeoutJob?.cancel()
                        _uiState.update { it.copy(awaitingFirstLine = false) }
                        android.util.Log.d(
                            TAG,
                            "lyrics revealed：音訊進到 line=$currentLine（${formatLogTime(position)}）",
                        )
                    }

                    if (currentLine == null) {
                        _uiState.update { it.copy(playbackPositionMs = position) }
                        delay(POSITION_POLL_MS)
                        continue
                    }

                    // ---- 修正 5：歌曲播完才結束 ----
                    // 不看「題目答完沒」，只看播放時間 → 答完最後一題也不會跳頁。
                    if (isSongEnded(position, state.playbackDurationMs)) {
                        onSongEnded()
                        delay(POSITION_POLL_MS)
                        continue
                    }

                    // ---- 規則 7：追回機制 ----
                    // 卡頓、廣告、拖動都可能讓我們**越過**某個未答的挖空行。
                    // 這時 seek 回那一行的結束時間（不是行首）並暫停，等補答。
                    //
                    // ⚠️ 只追「沒檢查過」的行。已經在交界停過、使用者又手動按播放的行
                    // 不可以再拉回去——那會變成「按播放立刻被彈回同一個點」的死鎖；
                    // 而且那句的交界檢查已經做過了，只是使用者選擇先不答。
                    val earliest = state.earliestUnanswered
                    if (earliest != null &&
                        earliest.lineIndex < currentLine &&
                        earliest.lineIndex !in checkedLines
                    ) {
                        android.util.Log.d(
                            TAG,
                            "追回：未答 line=${earliest.lineIndex} 已越過（目前 line=$currentLine）",
                        )
                        checkedLines += earliest.lineIndex
                        _uiState.update { it.copy(playbackPositionMs = position) }
                        pauseAtBoundary(earliest, reason = "追回")
                        delay(POSITION_POLL_MS)
                        continue
                    }

                    // ---- 交界檢查：只檢查剛剛離開的那一行，而且每行只檢查一次 ----
                    val previous = lastSeenLine
                    lastSeenLine = currentLine
                    if (previous != null && previous != currentLine) {
                        onLeftLine(previous)
                        // 剛剛停在交界：不要把焦點更新到下一行（擁有者要求
                        // 「歌詞焦點停在已播完的那一行」）。
                        if (pausing) {
                            delay(POSITION_POLL_MS)
                            continue
                        }
                    }

                    // 播放中就把「當前這一句」的題目與選項端上來，讓使用者邊聽邊填。
                    val question = LineBoundary.questionOf(currentLine, state.questions)

                    _uiState.update {
                        val focusBlank = question?.blankIndex
                        it.copy(
                            playbackPositionMs = position,
                            singingLineIndex = currentLine,
                            isSongDriven = true,
                            activeBlankIndex = focusBlank ?: it.activeBlankIndex,
                            options = focusBlank?.let { blank ->
                                optionsByBlank[blank].orEmpty()
                            } ?: it.options,
                        )
                    }
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    /**
     * 離開某一行的那一刻（＝該行結束時間到了）。
     *
     * **這裡是唯一會自動暫停的地方**。每行只檢查一次（[checkedLines]），
     * 所以淡出後的位移校正、或使用者手動播放，都不會讓同一行反覆觸發。
     */
    private fun onLeftLine(lineIndex: Int) {
        if (!checkedLines.add(lineIndex)) return

        val state = _uiState.value
        val question = LineBoundary.questionOf(lineIndex, state.questions)
        if (question == null) {
            android.util.Log.d(TAG, "check line=$lineIndex 沒有挖空 → 通過")
            return
        }

        if (LineBoundary.isAnswered(question, state.selections)) {
            android.util.Log.d(TAG, "check line=$lineIndex answered=true → 直接進入下一句")
            return
        }

        android.util.Log.d(
            TAG,
            "check line=$lineIndex answered=false → fadeOut 0.5s → pause",
        )
        pauseAtBoundary(question, reason = "交界")
    }

    /**
     * 停在某一句的結尾（＝下一句的開頭），等待補答。
     *
     * 流程（擁有者指定）：先漸出 0.5 秒，**漸出完成後**才 `pause()`，
     * 確保不會有突兀的截斷聲；暫停後只做「位移校正」回到交界，
     * **不回頭到句首**——回頭就等於要使用者先答再聽。
     *
     * ## ⚠️ 為什麼要握著這個協程（修正：填答案後意外暫停）
     *
     * 漸出要 0.5 秒。如果使用者在**漸出期間**就填了答案，`onSelectOption`
     * 會把音量歸零、`play()`、再漸入——但這個協程還活著，等 `fadeOut` 回來
     * 就呼叫 `player.pause()`，把剛恢復的播放又停掉。這就是「填完答案反而暫停」
     * 的真正原因（不是交界判定錯，是舊的暫停指令遲到）。
     *
     * 因此：協程存進 [pauseJob]，作答／手動播放時直接取消；並且在真正
     * `pause()` 之前再確認一次「還有人在等這一題」。
     *
     * @param reason 觸發原因（寫進 log，方便驗收時對照）
     */
    private fun pauseAtBoundary(question: FillChoiceQuestion, reason: String) {
        if (pausing) return
        pausing = true

        // 先把題目端上來：暫停的當下就要看得到這一句的選項。
        focusUnanswered(question, seekToStart = false)

        pauseJob?.cancel()
        pauseJob = viewModelScope.launch {
            try {
                // 淡入淡出可以在設定頁關掉：關掉時直接停，不要多等 0.5 秒。
                if (playbackSettings.fadeAudioValue) {
                    player.fadeOut(FADE_OUT_MS)
                }

                // 漸出這 0.5 秒內使用者可能已經補答（或按了播放）——
                // 那就不該再暫停，直接把播放交回去。
                if (_uiState.value.waitingForBlankIndex != question.blankIndex) {
                    android.util.Log.d(TAG, "中止交界暫停：line=${question.lineIndex} 已補答／已恢復")
                    return@launch
                }

                player.pause()

                val state = _uiState.value
                val boundary = LineBoundary.endMsOf(
                    lines = state.lines,
                    lineIndex = question.lineIndex,
                    durationMs = state.playbackDurationMs,
                )

                var snapMs = 0L
                if (boundary != null) {
                    // 淡出的那 0.5 秒會多播一點，這裡把位置拉回交界。
                    val target = (boundary - state.lyricOffsetMs).coerceAtLeast(0L)
                    snapMs = (player.currentPositionMs() - target).coerceAtLeast(0L)
                    if (snapMs > BOUNDARY_SNAP_TOLERANCE_MS) {
                        player.seekTo(target)
                    }
                }

                // 歌詞焦點留在**剛剛播完的那一行**，不要跳到下一行。
                // isPlaying 也一併壓成 false：播放器的下一次 tick 才會回報暫停，
                // 這段空窗若還留著 true，輪詢會多跑一輪並把焦點更新到下一行。
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        playbackPositionMs = player.currentPositionMs(),
                        singingLineIndex = question.lineIndex,
                    )
                }

                android.util.Log.d(
                    TAG,
                    "pause@${formatLogTime(player.currentPositionMs())}" +
                        " 行=${question.lineIndex} 交界=${boundary?.let(::formatLogTime) ?: "未知"}" +
                        " 校正=${snapMs}ms（$reason）",
                )
            } finally {
                pausing = false
            }
        }
    }

    /**
     * 取消「準備中的交界暫停」。
     *
     * 使用者在漸出期間補答、或手動按播放時呼叫：舊的暫停指令還沒執行到
     * `player.pause()`，取消就不會再停。
     */
    private fun cancelPauseAttempt() {
        if (pauseJob?.isActive == true) {
            android.util.Log.d(TAG, "取消交界暫停（使用者已補答／已按播放）")
        }
        pauseJob?.cancel()
        pauseJob = null
        pausing = false
    }

    /** 把某一行尚未作答的題目端到 UI 上（暫停等待補答時使用）。 */
    private fun focusUnanswered(question: FillChoiceQuestion, seekToStart: Boolean) {
        _uiState.update {
            it.copy(
                activeBlankIndex = question.blankIndex,
                options = optionsByBlank[question.blankIndex].orEmpty(),
                waitingForBlankIndex = question.blankIndex,
                singingLineIndex = question.lineIndex,
                audioNotice = WAITING_FOR_ANSWER,
            )
        }

        if (seekToStart) {
            val target = (question.startMs - _uiState.value.lyricOffsetMs).coerceAtLeast(0L)
            player.seekTo(target)
            _uiState.update { it.copy(playbackPositionMs = target) }
            android.util.Log.d(
                TAG,
                "focus line=${question.lineIndex} blank=${question.blankIndex} @${formatLogTime(target)}",
            )
        }
    }

    /**
     * 準備音源。第一次呼叫才會真的打 YouTube。
     *
     * ⚠️ **時長不符時不硬播**：見 `AudioTrackMatcher`。若挑到的版本
     * 與 LRCLIB 的時間戳明顯不是同一個錄音，硬播只會讓歌詞整首錯位。
     */
    /**
     * 找音源：用「歌手 + 歌名」搜 YouTube，排序後取最佳候選。
     *
     * 只做一次（[audioResolved]）；換版本會走 [onSwitchVersion]，不會重新搜尋。
     */
    fun prepareAudio() {
        if (audioResolved || _uiState.value.isAudioLoading) return
        val head = songHead ?: return

        audioResolved = true
        _uiState.update {
            it.copy(isAudioLoading = true, audioNotice = null, playbackError = null)
        }

        viewModelScope.launch {
            val query = "${head.artist} ${head.title}".trim()
            val videos = youTubeSearch.search(query)

            if (videos.isEmpty()) {
                failEntry(NO_AUDIO_MATCH)
                return@launch
            }

            val ranked = YouTubeCandidateScorer.rank(
                candidates = videos,
                expectedTitle = head.title,
                expectedArtist = head.artist,
                expectedDurationMs = head.durationMs,
            )

            if (ranked.isEmpty()) {
                failEntry(NO_AUDIO_MATCH)
                return@launch
            }

            candidates = ranked
            // 設定頁可以關掉自動播放：仍然載入音源（播放鍵才有效），但先不發聲。
            playCandidate(index = 0, autoPlay = playbackSettings.autoPlayValue)

            android.util.Log.d(
                TAG,
                "音源排序（${head.title}）：" + ranked.joinToString(" | ") {
                    "${it.video.videoId} score=${"%.1f".format(it.score)} " +
                        "delta=${it.durationDeltaMs}ms conf=${it.isConfident} " +
                        "penalty=${it.penaltyKeywords} ${it.video.channel}"
                },
            )
        }
    }

    /**
     * 播放候選清單中的第 [index] 個版本。
     *
     * @param resumeAtMs 要接續的播放位置（換版本時帶入，見 [onSwitchVersion]）
     * @param autoPlay 載入後是否自動播放
     */
    private fun playCandidate(index: Int, resumeAtMs: Long = 0L, autoPlay: Boolean = true) {
        val list = candidates
        val chosen = list.getOrNull(index)
        if (chosen == null) {
            failEntry(NO_AUDIO_MATCH)
            return
        }

        // `startAtMs` 會變成 embed URL 的 `&start=`；播放器另外會在拿到
        // 真實時長後再校正一次（新影片比記錄的時間短 → 停在時長前 1 秒）。
        player.load(videoId = chosen.video.videoId, startAtMs = resumeAtMs, autoPlay = autoPlay)

        // 從這裡開始重新檢查交界（換版本後仍要在同一個交界暫停）。
        checkedLines.clear()
        lastSeenLine = null

        _uiState.update {
            it.copy(
                isAudioLoading = false,
                isAudioReady = true,
                playerVersionIndex = index,
                playerVersionCount = list.size,
                audioNotice = if (chosen.isConfident) {
                    null
                } else {
                    AUDIO_MAY_NOT_ALIGN
                },
                playbackError = null,
            )
        }
    }

    /** 音源解析失敗：收掉三點、保留提示，其他功能完全不受影響。 */
    private fun failEntry(message: String) {
        hideDots()
        _uiState.update {
            it.copy(
                isAudioLoading = false,
                audioNotice = message,
            )
        }
    }

    /**
     * 換下一個候選版本。
     *
     * **保留播放位置**（擁有者指定）：播放到 1:30 換版本，新版本從 1:30 繼續，
     * 不是從頭開始。原本在等作答就維持暫停（不自動播），原本在播就繼續播。
     *
     * @param auto true 表示是「播放器回報不能嵌入」時自動換的
     */
    fun onSwitchVersion(auto: Boolean = false) {
        val list = candidates
        if (list.isEmpty()) return

        // 全部候選都試過了 → 明確告訴使用者，不要讓畫面一直空轉
        if (auto && _uiState.value.playerVersionIndex >= list.lastIndex) {
            if (_uiState.value.audioNotice == EMBED_BLOCKED) return
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    audioNotice = EMBED_BLOCKED,
                )
            }
            return
        }

        val state = _uiState.value

        // ---- 換源前記錄三件事（擁有者指定）----
        val resumeAtMs = state.playbackPositionMs.coerceAtLeast(0L)
        val wasWaiting = state.waitingForBlankIndex != null
        val keepPaused = wasWaiting

        val next = (state.playerVersionIndex + 1) % list.size
        playCandidate(next, resumeAtMs = resumeAtMs, autoPlay = !keepPaused)

        android.util.Log.d(
            TAG,
            "換版本 #${next + 1}：接續 ${formatLogTime(resumeAtMs)}" +
                "（行=${state.singingLineIndex}，${if (keepPaused) "保持暫停等作答" else "繼續播放"}）",
        )

        _uiState.update {
            it.copy(
                // 換版本不清作答、不跳結果頁
                audioNotice = if (keepPaused) WAITING_FOR_ANSWER else null,
            )
        }
    }

    /**
     * 微調歌詞偏移。**調整立即生效**：下一次位置輪詢就套用新的 offset。
     *
     * @param deltaMs 正數＝歌詞延後（歌聲比歌詞早）
     */
    fun onAdjustOffset(deltaMs: Long) {
        val head = songHead ?: return
        val next = (_uiState.value.lyricOffsetMs + deltaMs)
            .coerceIn(-PlaybackOffsets.MAX_OFFSET_MS, PlaybackOffsets.MAX_OFFSET_MS)

        playbackOffsets.setOffsetMs(head.songId, next)

        // 立即用新 offset 重算「正在唱哪一行」，不必等下一次輪詢
        val lyricPosition = player.currentPositionMs() + next
        _uiState.update {
            it.copy(
                lyricOffsetMs = next,
                singingLineIndex = lineIndexAt(lyricPosition) ?: it.singingLineIndex,
            )
        }
    }

    fun onResetOffset() {
        val head = songHead ?: return
        playbackOffsets.setOffsetMs(head.songId, 0L)
        _uiState.update { it.copy(lyricOffsetMs = 0L) }
    }

    /**
     * 播放鍵：**同時控制播放器與歌詞滾動**。
     *
     * 這是擁有者指定的核心行為——只有一個開關，不是兩個。
     * 使用者手動按下播放時，視同「我知道我欠一題，先讓我聽」：清掉等待狀態。
     * （但未答的題目**不會消失**，追回機制仍會在下一次 tick 把它拉回來。）
     */
    fun onTogglePlayPause() {
        val state = _uiState.value
        if (!state.isAudioReady) {
            // 上一次解析失敗（audioResolved 已 true）時，按播放＝重試。
            // 不然按鈕會完全沒反應，而三點已經收掉了。
            if (!state.isAudioLoading && audioResolved) {
                audioResolved = false
                _uiState.update { it.copy(audioNotice = null, playbackError = null) }
            }
            prepareAudio()
            return
        }

        // 使用者手動暫停 → 立即中止還在跑的漸入（擁有者指定）。
        if (state.isPlaying) {
            stopFadeIn()
        }

        // 正在等作答時按播放 ＝「先讓我聽」：取消還沒完成的交界暫停，
        // 並恢復播放。這時**不能**用 toggle——播放器在漸出期間仍算「播放中」，
        // toggle 會把它暫停，變成「按播放反而停住」。
        if (state.waitingForBlankIndex != null) {
            cancelPauseAttempt()
            _uiState.update {
                it.copy(waitingForBlankIndex = null, audioNotice = null)
            }
            viewModelScope.launch {
                player.play()
                if (playbackSettings.fadeAudioValue) {
                    player.fadeToFull(FADE_OUT_MS)
                } else {
                    // 淡出關掉時音量可能還停在 0，這裡一定要補回滿音量
                    player.setVolumePercent(100)
                }
            }
            return
        }

        player.togglePlayPause()
    }

    /**
     * 音量漸入（修正 2）。
     *
     * 擁有者指定的順序：**先把音量設成 0 → 再 play() → 才從 0 線性升到 1.0（0.5 秒）**。
     * 直接 `play()` 再用滿音量會有突兀的截斷感，所以這裡不能省任何一步。
     *
     * 新的漸入會取消舊的；暫停時由 [stopFadeIn] 立刻中止（不會有「漸入還在跑、
     * 但已經暫停」的狀態）。
     */
    private fun startFadeIn() {
        fadeInJob?.cancel()

        // 淡入淡出關掉：直接滿音量播放（播放器可能還停在漸出後的 0，要先補回來）
        if (!playbackSettings.fadeAudioValue) {
            fadeInJob = viewModelScope.launch {
                player.setVolumePercent(100)
                player.play()
            }
            return
        }

        fadeInJob = viewModelScope.launch {
            player.setVolumePercent(0)
            player.play()
            player.fadeIn(FADE_OUT_MS)
        }
    }

    /** 中止漸入（暫停、換版本、歌曲結束時呼叫）。 */
    private fun stopFadeIn() {
        if (fadeInJob?.isActive == true) {
            fadeInJob?.cancel()
            android.util.Log.d(TAG, "fadeIn 中止（使用者按下暫停）")
        }
    }

    /** 拖曳進度條過程中的即時 seek（不判斷未答，放手時才算）。 */
    fun onSeekPlayback(positionMs: Long) {
        player.seekTo(positionMs)
        _uiState.update { it.copy(playbackPositionMs = positionMs) }
    }

    /**
     * 拖曳結束（模組 B-7 / H-2）。
     *
     * 擁有者指定：「允許拖動，但到達後必須暫停在第一個未答挖空處，不得直接播過去」。
     * 因此放手時把位置對齊到「拖到的地方之後第一個未答的挖空」，並停在那裡。
     *
     * ⚠️ 這是**使用者手勢**，與交界檢查不同：手勢是「我要從這裡開始」，
     * 所以直接停、對齊到**句首**（讓使用者能聽這一句），不需要 0.5 秒漸出。
     * 交界檢查則是「這句已經唱完了」，所以停在**句尾**。
     */
    fun onTimelineSeekFinished() {
        val state = _uiState.value
        val next = state.unansweredQuestions
            .filter { it.startMs >= state.playbackPositionMs - SNAP_TOLERANCE_MS }
            .minByOrNull { it.startMs }
            ?: state.unansweredQuestions.minByOrNull { it.lineIndex }
            ?: return

        player.pause()
        // 從這一句重新開始：交界檢查要在它的句尾再觸發一次。
        checkedLines.remove(next.lineIndex)
        lastSeenLine = next.lineIndex
        focusUnanswered(next, seekToStart = true)
    }

    /**
     * 某個時間點**正在唱**哪一行。
     *
     * 取「開始時間 <= 位置」的最後一行；位置在所有行之前時回傳 null。
     * 沒有時間戳（純文字歌詞）時全部為 0，此時回傳 null 讓 UI 不要跟隨。
     */
    fun lineIndexAt(positionMs: Long): Int? {
        val lines = _uiState.value.lines
        if (lines.isEmpty()) return null
        // 純文字歌詞每行都有間隔（LyricsParser 會補），但仍要防禦全為 0 的情況
        if (lines.all { it.startMs <= 0L }) return null

        return lines.lastOrNull { it.startMs <= positionMs }?.index
    }

    /**
     * 歌曲是否已經播完（修正 5）。
     *
     * 兩個條件任一成立即可（擁有者指定）：
     * ```
     * currentTime >= duration - 1.0 秒
     * 播放器回報 ended
     * ```
     */
    private fun isSongEnded(positionMs: Long, durationMs: Long?): Boolean {
        if (player.state.value.ended) return true
        if (durationMs == null || durationMs <= 0L) return false
        return positionMs >= durationMs - SONG_END_TOLERANCE_MS
    }

    /**
     * 歌曲播完（**不是**答完最後一題）。
     *
     * 擁有者修正：答完最後一題後音樂要繼續播，播完才進結果頁。
     * 若結束時還有未答題目，**不強制跳頁**——回到第一個未答句等待補答。
     */
    private fun onSongEnded() {
        val state = _uiState.value
        if (state.songFinished) return

        player.pause()
        fadeInJob?.cancel()

        val unanswered = state.unansweredQuestions
        if (unanswered.isNotEmpty()) {
            val next = unanswered.minByOrNull { it.lineIndex } ?: return
            checkedLines.remove(next.lineIndex)
            lastSeenLine = next.lineIndex
            focusUnanswered(next, seekToStart = true)
            android.util.Log.d(
                TAG,
                "song end：還有 ${unanswered.size} 題未答 → 回到 line=${next.lineIndex} 等補答（不跳頁）",
            )
            return
        }

        _uiState.update { it.copy(songFinished = true, isPlaying = false) }
        android.util.Log.d(TAG, "song end：全部作答完成 → 進結果頁")
        finishIfComplete()
    }

    override fun onCleared() {
        super.onCleared()
        // 使用者真的離開練習頁（不是旋轉）→ 停掉播放。
        // WebView 本身由畫面的 DisposableEffect 解除綁定（見 PracticeScreen）。
        pauseJob?.cancel()
        fadeInJob?.cancel()
        dotsTimeoutJob?.cancel()
        player.stop()
    }

    private suspend fun load(songId: Long?) {
        if (songId == null) {
            failWith(ERROR_NO_SESSION)
            return
        }

        // 這一整段**不可以讓例外冒出去**：它在 `init` 啟動的協程裡執行，
        // 未捕捉的例外會直接殺掉 App（使用者看到的就是「點進去閃退」）。
        // 出錯時退到錯誤畫面，至少能按返回重來。
        try {
            loadInternal(songId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            android.util.Log.e(TAG, "載入練習資料失敗（songId=$songId）", error)
            failWith(ERROR_LOAD_FAILED)
        }
    }

    private suspend fun loadInternal(songId: Long) {
        // 第 1 層：記憶體中的 session（涵蓋設定變更／旋轉螢幕）。
        // 用 get() 而非已移除的 take()——見 PracticeSessionStore 的類別註解。
        sessionStore.get(songId)?.let { session ->
            applySource(
                source = SongSource(
                    head = SongHead(
                        songId = songId,
                        title = session.title,
                        artist = session.artist,
                        coverUrl = session.coverUrl,
                        durationMs = session.durationMs,
                    ),
                    lines = LyricsParser.parse(
                        lyricsFile = session.lyricsFile,
                        syncedLyrics = session.syncedLyrics,
                        plainLyrics = session.plainLyrics,
                    ),
                ),
            )
            return
        }

        // 第 2 層：程序被系統回收後，從 Room 快取取回。
        // 這條路徑需要第 1 次作答時寫入的 song / lyric_line（見 ensureSongPersisted）。
        val restored = restoreFromCache(songId)
        if (restored != null) {
            applySource(restored)
        } else {
            failWith(ERROR_NO_SESSION)
        }
    }

    /**
     * 從 Room 取回一首歌（程序死亡後的路徑）。
     *
     * ⚠️ **`lyric_line` 只存 `text`，不存挖空位置**，所以回復時要重跑
     * [FillChoiceBuilder.buildDataset]。因為亂數種子固定為 `songId`
     * （見 [randomFor]），挖出來的字與死亡前一致。
     *
     * @return 找不到歌曲或沒有歌詞行時回傳 null
     */
    private suspend fun restoreFromCache(songId: Long): SongSource? = runCatching {
        val song = songDao.findByLrclibId(songId) ?: return@runCatching null
        val storedLines = lyricLineDao.getForSong(song.id)
        if (storedLines.isEmpty()) return@runCatching null

        SongSource(
            head = SongHead(
                songId = songId,
                title = song.title,
                artist = song.artist,
                coverUrl = song.coverUrl,
                durationMs = song.durationMs,
            ),
            lines = storedLines.map { entity ->
                LyricLine(
                    index = entity.lineIndex,
                    text = entity.text,
                    startMs = entity.startMs,
                    endMs = entity.endMs,
                )
            },
        )
    }.onFailure {
        android.util.Log.w(TAG, "從快取回復練習進度失敗", it)
    }.getOrNull()

    /** 把一份歌詞來源轉成 UI 狀態。兩條來源路徑共用，確保行為一致。 */
    private suspend fun applySource(source: SongSource) {
        songHead = source.head

        // 播放器是單例，可能還留著上一首歌的狀態 → 每次都清乾淨
        player.stop()
        audioResolved = false
        candidates = emptyList()

        // 換歌 ＝ 全新的交界檢查狀態（同一個 ViewModel 可能被下一首歌重用）
        checkedLines.clear()
        lastSeenLine = null

        // 選詞：B1+、去重、排除專名與查無此詞，全域取 20 個、每句最多 2 個
        val pick = BlankPicker.pick(lines = source.lines, lexicon = lexicon)
        val dataset = FillChoiceBuilder.fromBlanks(source.lines, pick.blanks)

        logPick(pick, source.head.title)

        if (dataset.questions.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = ERROR_NO_BLANKS,
                    songTitle = source.head.title,
                    artist = source.head.artist,
                    coverUrl = source.head.coverUrl,
                    lines = dataset.lines,
                    hasTimeline = dataset.lines.any { line -> line.startMs > 0L },
                    pool = pick.pool,
                    shortfall = pick.shortfall,
                )
            }
            return
        }

        val allAnswers = dataset.questions.map { it.answer }

        // 一次把所有題目的選項算好並快取——換題時不必重算，選項也不會跳動
        val seed = source.head.songId
        optionsByBlank = dataset.questions.associate { question ->
            question.blankIndex to FillChoiceBuilder.buildOptions(
                question,
                allAnswers,
                randomFor(seed + question.blankIndex),
            )
        }

        val hasTimeline = dataset.lines.any { it.startMs > 0L }
        val firstLineStart = dataset.lines.firstOrNull()?.startMs ?: 0L
        // 三點只在「第一句離影片開頭夠遠」時才有意義；5 秒內就唱到的話
        // 直接正常顯示（擁有者指定），否則會變成無謂的倒數。
        //
        // 自動播放關掉時也**不顯示**三點：音樂不會開始，三點只會一直轉
        // 到 20 秒的安全上限才消失——那看起來像壞掉，不像等待。
        val awaitingFirstLine = playbackSettings.autoPlayValue &&
            hasTimeline && firstLineStart > DOTS_MIN_WAIT_MS

        _uiState.update {
            it.copy(
                isLoading = false,
                songTitle = source.head.title,
                artist = source.head.artist,
                coverUrl = source.head.coverUrl,
                lines = dataset.lines,
                questions = dataset.questions,
                // 選項**照常產生**，只是三點期間先不顯示（由 UI 依 awaitingFirstLine 控制）
                options = optionsByBlank[0].orEmpty(),
                // 換歌時把節奏狀態歸零，避免上一首的暫停／行號黏過來
                isSongDriven = false,
                singingLineIndex = null,
                waitingForBlankIndex = null,
                playbackPositionMs = 0L,
                // 每首歌有自己的歌詞偏移；沒調過就用設定頁的預設值
                lyricOffsetMs = playbackSettings.offsetFor(source.head.songId),
                playerVersionIndex = 0,
                playerVersionCount = 0,
                videoAspectRatio = null,
                isAudioReady = false,
                isAudioLoading = false,
                awaitingFirstLine = awaitingFirstLine,
                songFinished = false,
                // 結果頁的統計只存在這一次的 session，不落庫
                sessionStartedAtMs = System.currentTimeMillis(),
                elapsedMs = null,
                difficultWords = emptyList(),
                pool = pick.pool,
                shortfall = pick.shortfall,
                hasTimeline = hasTimeline,
            )
        }

        // 進入練習頁就開始解析音源（擁有者要求「進頁即載入」）。
        // 三點只是那一行的替身，任何失敗都會立刻收掉它（見 hideDots）。
        if (awaitingFirstLine) {
            android.util.Log.d(
                TAG,
                "awaiting first line：第一句在 ${formatLogTime(firstLineStart)}，顯示三點",
            )
            startEntry()
        } else {
            prepareAudio()
        }
    }

    /**
     * 開場：開始解析音源（擁有者要求「進頁即載入」）。
     *
     * ⚠️ 這裡**不再有頁面層級的閘門**：三點動畫只代替還沒唱到的那一行。
     * 解析失敗時收掉三點、正常顯示歌詞（提示文字仍然照舊顯示在 header）。
     */
    private fun startEntry() {
        dotsTimeoutJob?.cancel()
        dotsTimeoutJob = viewModelScope.launch {
            delay(DOTS_MAX_MS)
            if (_uiState.value.awaitingFirstLine) {
                android.util.Log.w(TAG, "三點超過 ${DOTS_MAX_MS}ms → 收掉，正常顯示歌詞")
                _uiState.update { it.copy(awaitingFirstLine = false) }
            }
        }
        prepareAudio()
    }

    /** 音源解析失敗：收掉三點，讓歌詞正常顯示（不影響任何其他功能）。 */
    private fun hideDots() {
        dotsTimeoutJob?.cancel()
        if (_uiState.value.awaitingFirstLine) {
            _uiState.update { it.copy(awaitingFirstLine = false) }
        }
    }


    private fun failWith(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    /**
     * 毫秒 → `mm:ss.S`（給暫停時機的 log 用）。
     *
     * 帶到 0.1 秒是因為交界誤差就是幾百毫秒——只印 `mm:ss` 看不出「差 300ms」。
     */
    private fun formatLogTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        return "%d:%02d.%d".format(safe / 60_000, (safe / 1_000) % 60, (safe % 1_000) / 100)
    }

    /**
     * 印出本次選詞結果（logcat 標籤 `LyrianPractice`），供驗收核對。
     *
     * ```
     * D/LyrianPractice: 選詞：48 句 → 20 題（差 0 題達標）；候選池 23 個 B1+
     * D/LyrianPractice: L3  silhouettes (C1, BNC 11637)
     * D/LyrianPractice: 排除: hello(A1), the(功能詞), Vegas(專名)
     * ```
     */
    private fun logPick(result: BlankPicker.Result, title: String) {
        android.util.Log.d(
            TAG,
            "選詞《$title》：${result.blanks.size} 題" +
                if (result.shortfall > 0) "（不足 ${result.shortfall} 題，候選池耗盡）" else "（達標）" +
                "；B1+ 候選 ${result.pool.size} 個；排除 ${result.rejected.size} 筆",
        )
        result.blanks.forEach { blank ->
            val candidate = result.pool.firstOrNull { it.lemma == blank.lemma }
            android.util.Log.d(
                TAG,
                "  L${blank.lineIndex} ${blank.surface} → ${blank.answer}" +
                    "（${candidate?.cefr?.label ?: "—"}，" +
                    "${candidate?.frequencyRank?.let { "BNC $it" } ?: "無詞頻"}）",
            )
        }
        result.rejected.take(20).forEach {
            android.util.Log.d(TAG, "  排除 ${it.word}：${it.reason}")
        }
    }

    /**
     * 挖空的亂數種子。
     *
     * **以 `songId` 為種子**，讓同一首歌永遠挖同一組字。這有兩個好處：
     *  1. 從 Room 回復時（第 2 層）挖出來的字與死亡前一致——否則使用者會發現
     *     「同一個空格換了字」
     *  2. 同一首歌重複進入的體驗穩定，不再每次不同
     */
    private fun randomFor(seed: Long): Random = Random(seed.hashCode())

    fun onSelectOption(choice: String) {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        if (state.currentRecord != null) return // 已作答，忽略重複點擊

        val attempt = FillChoiceBuilder.grade(question, choice)
        val wasWaitingForThis = state.waitingForBlankIndex == question.blankIndex

        // 同一行還有下一個未答的空？如果有，作答完要**立刻**把它端上來。
        val nextInLine = state.lines
            .firstOrNull { it.index == question.lineIndex }
            ?.blanks
            ?.firstOrNull { blank ->
                blank.blankIndex > question.blankIndex && state.selections[blank.blankIndex] == null
            }
            ?.blankIndex

        _uiState.update {
            it.copy(
                selections = it.selections + (question.blankIndex to ChoiceRecord(choice, attempt.isCorrect)),
                // 同句第二個空：立即切換，不等下一句
                activeBlankIndex = nextInLine ?: it.activeBlankIndex,
                options = nextInLine?.let { blank -> optionsByBlank[blank].orEmpty() } ?: it.options,
                // 停在同一個交界等第二個空：保持等待狀態，不恢復播放
                waitingForBlankIndex = when {
                    nextInLine == null -> if (wasWaitingForThis) null else it.waitingForBlankIndex
                    wasWaitingForThis -> nextInLine
                    else -> it.waitingForBlankIndex
                },
            )
        }

        // 作答**只寫入 attempt**。字庫一律由使用者手動加入（規格 §四）。
        persist(question, choice, attempt.isCorrect)

        if (nextInLine != null && wasWaitingForThis) {
            // 同一句還有空 → 不恢復播放，直接把下一個空端上來等作答
            android.util.Log.d(
                TAG,
                "same-line next blank=${nextInLine}（line=${question.lineIndex}）→ 保持暫停",
            )
            return
        }

        // ---- B-1 第三條因果 ----
        // 作答**只解除暫停**：淡入恢復播放。不碰 `singingLineIndex`、
        // 也不碰 `activeBlankIndex`——換行永遠只由播放時間決定。
        // 未答狀態本來就只增不減，所以「回到前面補答」永遠可行。
        //
        // 提前作答（該行還在播放中就填了）不會走到這裡：那時沒有在等待，
        // 交界檢查會直接通過，**不會有漸出、不會停**。
        if (wasWaitingForThis) {
            // ⚠️ 順序很重要：先取消「還沒執行到 pause()」的交界暫停協程，
            // 否則它會在漸出回來後把剛恢復的播放又停掉（擁有者回報的
            // 「填完答案反而暫停」就是這個）。
            cancelPauseAttempt()

            _uiState.update {
                it.copy(waitingForBlankIndex = null, audioNotice = null)
            }
            android.util.Log.d(
                TAG,
                "resume@${formatLogTime(player.currentPositionMs())} 補答 line=${question.lineIndex}" +
                    " blank=${question.blankIndex} → volume=0 → play → fadeIn 0.5s",
            )
            // 先歸零再播，然後才漸入（擁有者指定的順序）
            startFadeIn()
        } else {
            android.util.Log.d(
                TAG,
                "answer@${formatLogTime(player.currentPositionMs())} line=${question.lineIndex}" +
                    " blank=${question.blankIndex} 播放中作答（不暫停）",
            )
        }

        // 有音源時**不在這裡**進結果頁：要等歌曲播完（見 onSongEnded）。
        // 沒有音源時才退回「答完即完成」，否則永遠看不到結果頁。
        finishIfComplete()
    }

    /**
     * 全部答完時，算出結果頁要的統計與兩份字表。
     *
     * **有音源時要等歌曲播完**（[songFinished]）——擁有者修正：答完最後一題
     * 不可以立刻跳頁，音樂要繼續播。
     *
     * 「用時」只存在 session 狀態裡（擁有者指定不新增資料表）；
     * 字典查詢是離線資產，查 20~30 個字只是一連串雜湊查表。
     */
    private fun finishIfComplete() {
        val state = _uiState.value
        if (state.elapsedMs != null) return
        if (state.questions.isEmpty() || state.selections.size < state.questions.size) return
        // 有音源 → 必須等歌曲結束；沒有音源（無法得知結束）才用「答完即完成」
        if (state.isAudioReady && !state.songFinished) return

        val elapsed = (System.currentTimeMillis() - state.sessionStartedAtMs).coerceAtLeast(0L)
        _uiState.update { it.copy(elapsedMs = elapsed) }

        viewModelScope.launch {
            // 結果頁的「較難單字」＝ 本次候選池（B1+、已去重、非專名）。
            // 這是**顯示**用的清單，不會寫入字庫。
            val words = state.pool.map { candidate ->
                ResultWord(
                    word = candidate.lemma,
                    partOfSpeech = candidate.displayPos,
                    meaning = candidate.gloss,
                    lemma = candidate.lemma,
                    level = candidate.cefr,
                )
            }
            _uiState.update { it.copy(difficultWords = words) }
        }
    }

    /**
     * 前進到下一題（**只有「沒有時間軸」時才成立**）。
     *
     * 擁有者指定的模組 B：歌詞行只能由 `currentTime` 推進。
     * 因此只要歌詞有時間戳，作答就**不得**讓行號前進——就算音源還沒載入、
     * `isSongDriven` 還是 false 也一樣。用 [PracticeUiState.hasTimeline] 當守門員，
     * 而不是 `isSongDriven`（後者要等第一次輪詢才會變 true，中間那幾秒就會亂跳）。
     */
    fun onNext() {
        val state = _uiState.value
        if (state.hasTimeline) return
        if (state.currentRecord == null) return

        val nextIndex = state.activeBlankIndex + 1

        _uiState.update {
            it.copy(
                activeBlankIndex = nextIndex,
                options = optionsByBlank[nextIndex].orEmpty(),
            )
        }
    }

    fun onReplay() {
        val state = _uiState.value
        if (state.questions.isEmpty()) return

        // 重玩＝全新的交界檢查：把「檢查過哪幾行／上次唱到哪一行」清掉。
        checkedLines.clear()
        lastSeenLine = null

        _uiState.update {
            it.copy(
                activeBlankIndex = 0,
                selections = emptyMap(),
                options = optionsByBlank[0].orEmpty(),
                waitingForBlankIndex = null,
                singingLineIndex = null,
                // 結果頁的統計歸零重算（延伸詞彙不必重查，字是一樣的）
                sessionStartedAtMs = System.currentTimeMillis(),
                elapsedMs = null,
                difficultWords = emptyList(),
            )
        }

        if (state.isAudioReady) {
            onSeekPlayback(0L)
        }
    }

    /**
     * 把一次作答落庫：**只寫 attempt 與每日統計**。
     *
     * ## 字庫不在這裡寫
     *
     * 依規格 §四：答對、答錯、練習完成都**不得**自動寫入生詞本。
     * 唯一的寫入點是 [onAddToWordbank]（使用者按「加入」）。
     * 舊版的「B1 以上不論對錯一律入庫」與 `addFromError` 已完全移除。
     */
    private fun persist(question: FillChoiceQuestion, choice: String, isCorrect: Boolean) {
        viewModelScope.launch {
            runCatching {
                val songId = ensureSongPersisted()

                attemptDao.insert(
                    AttemptEntity(
                        modeId = PracticeModeId.FILL_CHOICE.name,
                        songId = songId,
                        lineIndex = question.lineIndex,
                        // 沒有字庫關聯：作答不再對應任何 vocab 列
                        vocabId = null,
                        prompt = question.prompt,
                        userAnswer = choice,
                        correctAnswer = question.answer,
                        score = if (isCorrect) 1f else 0f,
                        isCorrect = isCorrect,
                        createdAt = System.currentTimeMillis(),
                    ),
                )

                dailyStatDao.bumpOrCreate(
                    day = toDayKey(),
                    score = if (isCorrect) 1 else 0,
                    attempts = 1,
                    correct = if (isCorrect) 1 else 0,
                )
            }.onFailure { error ->
                android.util.Log.w(TAG, "記錄作答失敗（不影響練習）", error)
            }
        }
    }

    /**
     * 結果頁的「加入字庫」（模組 D.3）。
     *
     * **手動加入不受等級限制**——這是使用者明確表示「我要這個字」。
     * 同一個 lemma 已存在時不會重複新增（`ensureInWordbank` 是冪等的）。
     */
    fun onAddToWordbank(word: String, lemma: String, contextLine: String?) {
        viewModelScope.launch {
            runCatching {
                val songId = ensureSongPersisted()
                vocabDao.ensureInWordbank(
                    lemma = FillChoiceBuilder.normalizeAnswer(lemma),
                    surfaceForm = word,
                    contextLine = contextLine,
                    songId = songId,
                    cefr = null,
                    now = System.currentTimeMillis(),
                )
            }.onFailure {
                android.util.Log.w(TAG, "手動加入字庫失敗", it)
            }

            _uiState.update { state ->
                state.copy(
                    difficultWords = state.difficultWords.map {
                        if (it.word == word) it.copy(addedToWordbank = true) else it
                    },
                )
            }
        }
    }

    /**
     * 第一次作答時把歌曲與歌詞寫入快取；之後沿用同一個 id。
     *
     * ⚠️ **不能依賴 `sessionStore`**：程序死亡後回復的那條路徑
     * （見 [restoreFromCache]）根本沒有記憶體 session，但歌已經在 Room 裡了。
     * 因此這裡只依賴 [songHead] 與 [_uiState]。
     */
    private suspend fun ensureSongPersisted(): Long? {
        localSongId?.let { return it }

        val head = songHead ?: return null

        val songId = songDao.upsertByLrclibId(
            SongEntity(
                lrclibId = head.songId,
                title = head.title,
                artist = head.artist,
                coverUrl = head.coverUrl,
                createdAt = System.currentTimeMillis(),
            ),
        )

        if (!lyricsPersisted) {
            val lines = _uiState.value.lines.map { line ->
                LyricLineEntity(
                    songId = songId,
                    lineIndex = line.index,
                    text = line.text,
                    startMs = line.startMs,
                    endMs = line.endMs,
                )
            }
            lyricLineDao.replaceForSong(songId, lines)
            lyricsPersisted = true
        }

        localSongId = songId
        return songId
    }
}