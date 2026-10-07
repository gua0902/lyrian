/**
 * 一次作答的完整落庫。
 *
 * 把「快取歌曲 → 寫入作答 → 答錯收進字庫 → 累加每日統計」包在**同一個交易**裡，
 * 避免只寫了一半（例如 attempt 進去了但字庫沒進去）。
 */
import type { LyricLine, PracticeModeId, VocabCandidate } from '@/features/practice';

import { withTransaction } from '../transaction';
import type { SqlDatabase } from '../types';
import { recordAttempt } from './attempts';
import { saveLyricLines, saveSong, type SaveSongInput } from './songs';
import { bumpDailyStat } from './stats';
import { addVocabFromError } from './vocab';

export interface LogPracticeAnswerInput {
  modeId: PracticeModeId;
  lineIndex: number;
  prompt: string;
  userAnswer: string;
  correctAnswer: string;
  score: number;
  isCorrect: boolean;
  durationMs: number | null;
  /** 答錯時的收藏目標；答對請傳 null */
  vocabCandidate: VocabCandidate | null;
  /** 需要把歌曲寫入快取時提供（第一次練習該首歌） */
  song: SaveSongInput | null;
  /** 搭配 `song` 一起寫入的歌詞行 */
  lyrics: readonly LyricLine[] | null;
  /** `YYYY-MM-DD`，見 `utils/time.ts` 的 `toDayKey` */
  day: string;
  now: number;
}

export interface LogPracticeAnswerResult {
  attemptId: number;
  songId: number | null;
  vocabId: number | null;
}

/** 記錄一次作答（含快取、字庫與每日統計）。 */
export async function logPracticeAnswer(
  database: SqlDatabase,
  input: LogPracticeAnswerInput
): Promise<LogPracticeAnswerResult> {
  return withTransaction(database, async () => {
    let songId: number | null = input.vocabCandidate?.songId ?? null;

    if (input.song) {
      songId = await saveSong(database, input.song);
      if (input.lyrics && input.lyrics.length > 0) {
        await saveLyricLines(database, songId, input.lyrics);
      }
    }

    let vocabId: number | null = null;
    let isNewWord = false;

    if (input.vocabCandidate) {
      const added = await addVocabFromError(database, {
        lemma: input.vocabCandidate.lemma,
        surfaceForm: input.vocabCandidate.surfaceForm,
        translation: null,
        contextLine: input.vocabCandidate.contextLine,
        songId,
        now: input.now,
      });
      vocabId = added.id;
      isNewWord = added.isNew;
    }

    const attemptId = await recordAttempt(database, {
      modeId: input.modeId,
      songId,
      lineIndex: input.lineIndex,
      vocabId,
      prompt: input.prompt,
      userAnswer: input.userAnswer,
      correctAnswer: input.correctAnswer,
      score: input.score,
      isCorrect: input.isCorrect,
      durationMs: input.durationMs,
      now: input.now,
    });

    await bumpDailyStat(database, {
      day: input.day,
      score: input.isCorrect ? 1 : 0,
      attempts: 1,
      correct: input.isCorrect ? 1 : 0,
      newWords: isNewWord ? 1 : 0,
    });

    return { attemptId, songId, vocabId };
  });
}
