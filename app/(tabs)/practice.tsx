import { useFocusEffect, useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { M3Button, M3ProgressBar, useTheme } from '@/components/m3';
import { format, strings } from '@/constants/strings';
import { radius, spacing, typography } from '@/constants/theme';
import { getDatabase } from '@/db/database';
import { logPracticeAnswer } from '@/db/repositories/practiceLog';
import {
  buildFillChoiceDataset,
  buildOptions,
  computeProgress,
  createSeededRng,
  gradeChoice,
  indexQuestionsByLine,
  normalizeAnswer,
  parseLyrics,
  vocabCandidateFromAttempt,
  type Attempt,
  type ChoiceRecord,
} from '@/features/practice';
import { clearPendingSession, peekPendingSession, type PracticeSession } from '@/features/session';
import { formatDuration, toDayKey } from '@/utils/time';

/** 播完前的環境音提示用的固定間隔（真正的音訊播放是 Phase 6）。 */
const TIMER_INTERVAL_MS = 1000;

/**
 * Tab 2：練習（四選一填空）。
 *
 * 對齊 `UI references/Lyrian · 歌詞練習.png`：
 * 標題列（返回＋歌名＋歌手·時間）→ 封面＋標語＋「第 N 個空格 / 共 M 個」→
 * 進度條 → 時間＋「跟著歌詞一起聽」→ 歌詞流（當前空格行抽成黃色大卡、已答過的行
 * 以彩色文字顯示）→ 答錯提示＋播放鍵 → 「填入空格」＋提示／下一題 →
 * 2×2 選項膠囊（答對綠、答錯紅）→ 作答進度。
 *
 * 音訊尚未實作（Phase 6），因此播放鍵目前只驅動畫面上的計時與進度條。
 */
export default function PracticeTabScreen() {
  const { colors } = useTheme();
  const router = useRouter();

  const [session, setSession] = useState<PracticeSession | null>(null);
  const [activeBlankIndex, setActiveBlankIndex] = useState(0);
  const [selections, setSelections] = useState<Record<number, ChoiceRecord>>({});
  const [currentSeconds, setCurrentSeconds] = useState(0);
  const [playRequested, setPlayRequested] = useState(true);

  /** 記住目前載入的是哪一首歌，避免每次取得焦點都重置作答狀態。 */
  const sessionKeyRef = useRef<string | null>(null);

  /** 歌詞行只在該首歌的第一次作答時寫入快取。 */
  const lyricsSavedRef = useRef(false);

  // 搜尋頁選好歌後會寫入 pending session；取得焦點時讀進來。
  useFocusEffect(
    useCallback(() => {
      const pending = peekPendingSession();
      if (!pending) return;

      const key = `${pending.song.lrclibId}:${pending.modeId}`;
      if (sessionKeyRef.current === key) return;

      sessionKeyRef.current = key;
      lyricsSavedRef.current = false;
      setSession(pending);
      setActiveBlankIndex(0);
      setSelections({});
      setCurrentSeconds(0);
      setPlayRequested(true);
    }, [])
  );

  const dataset = useMemo(
    () =>
      session
        ? buildFillChoiceDataset(parseLyrics(session.song.lyrics), { rng: createSeededRng(1) })
        : null,
    [session]
  );

  const lines = useMemo(() => dataset?.lines ?? [], [dataset]);
  const questions = useMemo(() => dataset?.questions ?? [], [dataset]);

  const questionByLine = useMemo(() => indexQuestionsByLine(questions), [questions]);
  const allAnswers = useMemo(() => questions.map((question) => question.answer), [questions]);

  const currentQuestion = questions[activeBlankIndex] ?? null;

  /** 以 blankIndex 為種子，選項在重繪時保持穩定。 */
  const options = useMemo(() => {
    if (!currentQuestion) return [];
    return buildOptions(currentQuestion, allAnswers, {
      rng: createSeededRng(activeBlankIndex + 1),
    });
  }, [activeBlankIndex, allAnswers, currentQuestion]);

  const progress = useMemo(
    () => computeProgress(questions.length, Object.values(selections)),
    [questions.length, selections]
  );

  const totalSeconds = useMemo(() => {
    if (!session) return 0;
    if (session.song.durationMs && session.song.durationMs > 0) {
      return Math.round(session.song.durationMs / 1000);
    }
    const last = lines[lines.length - 1];
    if (!last) return 0;
    return Math.round(((last.endMs ?? last.startMs) + 5_000) / 1000);
  }, [lines, session]);

  // 播完即停止：以推導取代在 effect 內 setState，避免連鎖重繪。
  const isFinished = totalSeconds > 0 && currentSeconds >= totalSeconds;
  const isPlaying = playRequested && !isFinished;

  useEffect(() => {
    if (!isPlaying) return;

    const interval = setInterval(() => {
      setCurrentSeconds((prev) => Math.min(prev + 1, totalSeconds));
    }, TIMER_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [isPlaying, totalSeconds]);

  const answeredRecord = currentQuestion ? selections[currentQuestion.blankIndex] : undefined;

  /**
   * 把一次作答落庫：寫入 attempt、答錯的字收進字庫、累加每日統計。
   *
   * 刻意**不 await**——寫入失敗不該卡住或中斷練習（見 ARCHITECTURE.md §3
   * 「AI 絕不在關鍵路徑上」的同一原則：外掛依賴不阻擋主流程）。
   */
  const persistAnswer = useCallback(
    async (attempt: Attempt, choice: string) => {
      if (!session || !currentQuestion) return;

      const now = Date.now();

      try {
        const database = await getDatabase();

        await logPracticeAnswer(database, {
          modeId: 'fill-choice',
          lineIndex: attempt.lineIndex,
          prompt: attempt.prompt,
          userAnswer: choice,
          correctAnswer: attempt.correctAnswer,
          score: attempt.score,
          isCorrect: attempt.isCorrect,
          durationMs: null,
          vocabCandidate: vocabCandidateFromAttempt(currentQuestion, attempt, null),
          song: {
            lrclibId: session.song.lrclibId,
            title: session.song.title,
            artist: session.song.artist,
            album: session.song.album,
            durationMs: session.song.durationMs,
            coverUrl: session.song.coverUrl,
            genre: session.song.genre,
            // 難度需要 ECDICT 詞頻，P1.5 才會填
            difficulty: null,
            now,
          },
          // 歌詞行只在第一次作答時寫入，之後不必重複 delete + insert
          lyrics: lyricsSavedRef.current ? null : lines,
          day: toDayKey(new Date(now)),
          now,
        });

        lyricsSavedRef.current = true;
      } catch (error) {
        // 落庫失敗只記錄，不打斷練習
        console.warn('[Lyrian] 記錄作答失敗：', error);
      }
    },
    [currentQuestion, lines, session]
  );

  const handleSelectOption = useCallback(
    (choice: string) => {
      if (!currentQuestion || answeredRecord) return;

      const attempt = gradeChoice(currentQuestion, choice);
      setSelections((prev) => ({
        ...prev,
        [currentQuestion.blankIndex]: { choice, isCorrect: attempt.isCorrect },
      }));

      void persistAnswer(attempt, choice);
    },
    [answeredRecord, currentQuestion, persistAnswer]
  );

  const handleNextBlank = useCallback(() => {
    setActiveBlankIndex((prev) => prev + 1);
  }, []);

  const handleReplay = useCallback(() => {
    setActiveBlankIndex(0);
    setSelections({});
    setCurrentSeconds(0);
    setPlayRequested(true);
  }, []);

  const handleChangeSong = useCallback(() => {
    clearPendingSession();
    sessionKeyRef.current = null;
    setSession(null);
    router.push('/');
  }, [router]);

  // ---- 尚未選歌 ----
  if (!session || !dataset || questions.length === 0) {
    return (
      <SafeAreaView style={[styles.safe, { backgroundColor: colors.surface }]} edges={['top']}>
        <View style={styles.emptyState}>
          <SymbolView
            name={{ ios: 'play.circle', android: 'play_circle', web: 'play_circle' }}
            tintColor={colors.onSurfaceVariant}
            size={48}
          />
          <Text style={[typography.titleMedium, styles.emptyTitle, { color: colors.onSurface }]}>
            {strings.practice.noSongTitle}
          </Text>
          <Text
            style={[typography.bodyMedium, styles.emptyHint, { color: colors.onSurfaceVariant }]}
          >
            {strings.practice.noSongHint}
          </Text>
          <M3Button label={strings.practice.goSearch} onPress={handleChangeSong} />
        </View>
      </SafeAreaView>
    );
  }

  const elapsedLabel = formatDuration(currentSeconds * 1000) ?? '00:00';
  const totalLabel = formatDuration(totalSeconds * 1000) ?? '--:--';

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.surface }]} edges={['top']}>
      {/* ---- 標題列 ---- */}
      <View style={styles.appBar}>
        <Pressable
          onPress={handleChangeSong}
          accessibilityRole="button"
          accessibilityLabel={strings.practice.changeSong}
          style={styles.appBarButton}
        >
          <SymbolView
            name={{ ios: 'arrow.left', android: 'arrow_back', web: 'arrow_back' }}
            tintColor={colors.onSurface}
            size={24}
          />
        </Pressable>

        <View style={styles.appBarMeta}>
          <Text style={[typography.titleMedium, { color: colors.onSurface }]} numberOfLines={1}>
            {session.song.title}
          </Text>
          <Text
            style={[typography.bodySmall, styles.appBarSubtitle, { color: colors.onSurfaceVariant }]}
            numberOfLines={1}
          >
            {`${session.song.artist}${strings.search.metaSeparator}${elapsedLabel} / ${totalLabel}`}
          </Text>
        </View>
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* ---- 封面 + 標語 + 空格進度 ---- */}
        <View style={styles.summaryRow}>
          {session.song.coverUrl ? (
            <Image source={{ uri: session.song.coverUrl }} style={styles.summaryCover} />
          ) : (
            <View
              style={[
                styles.summaryCover,
                styles.summaryCoverFallback,
                { backgroundColor: colors.surfaceContainerHigh },
              ]}
            >
              <SymbolView
                name={{ ios: 'music.note', android: 'music_note', web: 'music_note' }}
                tintColor={colors.onSurfaceVariant}
                size={26}
              />
            </View>
          )}

          <View style={styles.summaryMeta}>
            <Text style={[typography.headlineSmall, { color: colors.onSurface }]}>
              {strings.practice.title}
            </Text>
            <Text
              style={[typography.bodySmall, styles.summarySubtitle, { color: colors.onSurfaceVariant }]}
            >
              {format(strings.practice.blankOrdinalFormat, {
                ordinal: Math.min(activeBlankIndex + 1, questions.length),
                total: questions.length,
              })}
            </Text>
          </View>
        </View>

        <M3ProgressBar
          value={totalSeconds > 0 ? currentSeconds / totalSeconds : 0}
          style={styles.progressBar}
        />

        <View style={styles.timeRow}>
          <Text style={[typography.bodySmall, { color: colors.onSurfaceVariant }]}>
            {`${elapsedLabel} / ${totalLabel}`}
          </Text>
          <Text style={[typography.bodySmall, { color: colors.primary }]}>
            {strings.practice.listenAlong}
          </Text>
        </View>

        {/* ---- 歌詞流 ---- */}
        <View style={styles.lyrics}>
          {lines.map((line) => {
            const question = questionByLine.get(line.index);

            // 當前空格：抽成黃色大卡
            if (question && question.blankIndex === activeBlankIndex) {
              return (
                <View
                  key={line.index}
                  style={[
                    styles.highlightCard,
                    {
                      backgroundColor: colors.lyricHighlight,
                      borderColor: colors.onLyricHighlight,
                    },
                  ]}
                >
                  <Text
                    style={[typography.labelSmall, { color: colors.onLyricHighlight }]}
                  >
                    {`${strings.practice.nowPlaying}${strings.search.metaSeparator}${strings.practice.blankCardLabel} ${String(
                      question.blankIndex + 1
                    ).padStart(2, '0')}`}
                  </Text>
                  <Text
                    style={[
                      typography.headlineSmall,
                      styles.highlightText,
                      { color: colors.onLyricHighlight },
                    ]}
                  >
                    {question.prefix}
                    <Text style={styles.blankMark}>{'____'}</Text>
                    {question.suffix}
                  </Text>
                </View>
              );
            }

            // 已答過的行：原句以深色顯示，答案以對／錯顏色標出
            const record = question ? selections[question.blankIndex] : undefined;
            if (question && record) {
              return (
                <Text
                  key={line.index}
                  style={[typography.lyric, { color: colors.lyricCurrent }]}
                >
                  {question.prefix}
                  <Text
                    style={[
                      styles.answeredWord,
                      { color: record.isCorrect ? colors.success : colors.error },
                    ]}
                  >
                    {record.choice}
                  </Text>
                  {question.suffix}
                </Text>
              );
            }

            // 其餘（尚未輪到、或本身沒有空格）
            return (
              <Text
                key={line.index}
                style={[typography.lyric, { color: colors.lyricUpcoming }]}
              >
                {line.text}
              </Text>
            );
          })}
        </View>

        {/* ---- 提示 + 播放鍵 ---- */}
        <View style={styles.feedbackRow}>
          <View
            style={[
              styles.feedbackBox,
              {
                backgroundColor: answeredRecord
                  ? answeredRecord.isCorrect
                    ? colors.successContainer
                    : colors.errorContainer
                  : colors.surfaceContainerHigh,
              },
            ]}
          >
            <Text
              style={[
                typography.bodySmall,
                {
                  color: answeredRecord
                    ? answeredRecord.isCorrect
                      ? colors.onSuccessContainer
                      : colors.onErrorContainer
                    : colors.onSurfaceVariant,
                },
              ]}
            >
              {answeredRecord
                ? answeredRecord.isCorrect
                  ? strings.practice.feedbackCorrect
                  : format(strings.practice.feedbackWrongFormat, {
                      answer: currentQuestion?.answer ?? '',
                    })
                : strings.practice.listenAlong}
            </Text>
          </View>

          <Pressable
            onPress={() => (isFinished ? handleReplay() : setPlayRequested((prev) => !prev))}
            accessibilityRole="button"
            accessibilityLabel={isPlaying ? strings.practice.pause : strings.practice.startPlayback}
            style={({ pressed }) => [
              styles.playButton,
              { backgroundColor: colors.primary, opacity: pressed ? 0.85 : 1 },
            ]}
          >
            <SymbolView
              name={
                isFinished
                  ? { ios: 'arrow.clockwise', android: 'replay', web: 'replay' }
                  : isPlaying
                    ? { ios: 'pause.fill', android: 'pause', web: 'pause' }
                    : { ios: 'play.fill', android: 'play_arrow', web: 'play_arrow' }
              }
              tintColor={colors.onPrimary}
              size={24}
            />
          </Pressable>
        </View>

        {/* ---- 選項區 ---- */}
        {currentQuestion ? (
          <>
            <View style={styles.optionHeader}>
              <Text style={[typography.labelLarge, { color: colors.onSurface }]}>
                {strings.practice.fillBlankLabel}
              </Text>
              {answeredRecord ? (
                <Pressable
                  onPress={handleNextBlank}
                  accessibilityRole="button"
                  style={styles.nextButton}
                >
                  <Text style={[typography.bodySmall, styles.nextLink, { color: colors.primary }]}>
                    {`${strings.practice.nextBlank} ›`}
                  </Text>
                </Pressable>
              ) : (
                <Text style={[typography.bodySmall, { color: colors.onSurfaceVariant }]}>
                  {strings.practice.tryAgain}
                </Text>
              )}
            </View>

            <View style={styles.optionGrid}>
              {options.map((option) => {
                const isCorrectOption =
                  normalizeAnswer(option) === normalizeAnswer(currentQuestion.answer);
                const isChosen = answeredRecord?.choice === option;

                const state = !answeredRecord
                  ? 'idle'
                  : isCorrectOption
                    ? 'correct'
                    : isChosen
                      ? 'wrong'
                      : 'idle';

                const palette = {
                  idle: {
                    bg: colors.surface,
                    border: colors.outlineVariant,
                    text: colors.onSurface,
                  },
                  correct: {
                    bg: colors.successContainer,
                    border: colors.success,
                    text: colors.onSuccessContainer,
                  },
                  wrong: {
                    bg: colors.errorContainer,
                    border: colors.error,
                    text: colors.onErrorContainer,
                  },
                }[state];

                return (
                  <Pressable
                    key={option}
                    onPress={() => handleSelectOption(option)}
                    disabled={Boolean(answeredRecord)}
                    accessibilityRole="button"
                    accessibilityState={{ disabled: Boolean(answeredRecord) }}
                    style={({ pressed }) => [
                      styles.option,
                      {
                        backgroundColor: palette.bg,
                        borderColor: palette.border,
                        opacity: pressed && !answeredRecord ? 0.85 : 1,
                      },
                    ]}
                  >
                    {state !== 'idle' ? (
                      <SymbolView
                        name={
                          state === 'correct'
                            ? { ios: 'checkmark', android: 'check', web: 'check' }
                            : { ios: 'xmark', android: 'close', web: 'close' }
                        }
                        tintColor={palette.text}
                        size={16}
                        style={styles.optionCheck}
                      />
                    ) : null}
                    <Text
                      style={[typography.bodyLarge, styles.optionText, { color: palette.text }]}
                      numberOfLines={1}
                    >
                      {option}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </>
        ) : (
          <View style={styles.completeBlock}>
            <Text style={[typography.titleMedium, { color: colors.onSurface }]}>
              {strings.practice.replay}
            </Text>
            <View style={styles.completeActions}>
              <M3Button label={strings.practice.replay} onPress={handleReplay} />
              <M3Button
                label={strings.practice.changeSong}
                variant="outlined"
                onPress={handleChangeSong}
              />
            </View>
          </View>
        )}

        <Text
          style={[typography.bodySmall, styles.progressText, { color: colors.onSurfaceVariant }]}
        >
          {format(strings.practice.progressFormat, {
            correct: progress.correct,
            total: progress.total,
            percent: progress.percent,
          })}
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
  },
  appBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
    paddingBottom: spacing.md,
  },
  appBarMeta: {
    flex: 1,
    marginLeft: spacing.lg,
  },
  appBarSubtitle: {
    marginTop: 2,
  },
  /** 觸控目標至少 48dp（MD3 無障礙要求） */
  appBarButton: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: -spacing.sm,
  },
  scrollContent: {
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.xxl,
  },
  summaryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: spacing.sm,
  },
  summaryCover: {
    width: 64,
    height: 64,
    borderRadius: radius.md,
  },
  summaryCoverFallback: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  summaryMeta: {
    flex: 1,
    marginLeft: spacing.md,
  },
  summarySubtitle: {
    marginTop: spacing.xs,
  },
  progressBar: {
    marginTop: spacing.lg,
  },
  timeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.sm,
  },
  lyrics: {
    marginTop: spacing.lg,
    gap: spacing.md,
  },
  highlightCard: {
    borderRadius: radius.xl,
    borderWidth: 2,
    padding: spacing.lg,
  },
  highlightText: {
    marginTop: spacing.sm,
  },
  blankMark: {
    textDecorationLine: 'underline',
  },
  answeredWord: {
    fontWeight: '700',
  },
  feedbackRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    marginTop: spacing.xl,
  },
  feedbackBox: {
    flex: 1,
    borderRadius: radius.md,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
  },
  playButton: {
    width: 56,
    height: 56,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  optionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: spacing.lg,
    marginBottom: spacing.md,
  },
  nextLink: {
    fontWeight: '700',
  },
  /** 觸控目標至少 48dp（MD3 無障礙要求） */
  nextButton: {
    minHeight: 48,
    minWidth: 48,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  optionGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    width: '48%',
    minHeight: 48,
    borderRadius: radius.full,
    borderWidth: 2,
    paddingHorizontal: spacing.md,
  },
  optionCheck: {
    marginRight: spacing.xs,
  },
  optionText: {
    fontWeight: '600',
  },
  completeBlock: {
    marginTop: spacing.xl,
    gap: spacing.md,
  },
  completeActions: {
    flexDirection: 'row',
    gap: spacing.md,
  },
  progressText: {
    textAlign: 'center',
    marginTop: spacing.xl,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    gap: spacing.md,
  },
  emptyTitle: {
    marginTop: spacing.sm,
  },
  emptyHint: {
    textAlign: 'center',
    marginBottom: spacing.sm,
  },
});
