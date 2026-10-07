/**
 * `logPracticeAnswer` 的整合測試：一次作答應該同時寫入
 * 作答紀錄、字庫（答錯時）與每日統計。
 *
 * 跑在真實 SQLite 上。
 */
import { migrate } from '../migrate';
import { logPracticeAnswer, type LogPracticeAnswerInput } from '../repositories/practiceLog';
import { getAttemptStats } from '../repositories/attempts';
import { getDailyStat } from '../repositories/stats';
import { findSongByLrclibId, getLyricLines } from '../repositories/songs';
import { getVocabSummary, listVocab } from '../repositories/vocab';
import { createNodeDatabase, type NodeSqlDatabase } from '../testing/createNodeDatabase';

const NOW = 1_760_000_000_000;
const DAY = '2026-10-07';

let db: NodeSqlDatabase;

beforeEach(async () => {
  db = createNodeDatabase();
  await migrate(db);
});

afterEach(() => {
  db.close();
});

function baseInput(overrides: Partial<LogPracticeAnswerInput> = {}): LogPracticeAnswerInput {
  return {
    modeId: 'fill-choice',
    lineIndex: 2,
    prompt: 'I keep a little ____ inside my heart',
    userAnswer: 'love',
    correctAnswer: 'hope',
    score: 0,
    isCorrect: false,
    durationMs: 1_500,
    vocabCandidate: {
      lemma: 'hope',
      surfaceForm: 'hope',
      contextLine: 'I keep a little hope inside my heart',
      songId: null,
    },
    song: null,
    lyrics: null,
    day: DAY,
    now: NOW,
    ...overrides,
  };
}

describe('logPracticeAnswer', () => {
  it('答錯：寫入作答、把字庫標為新字、每日統計計為新單字', async () => {
    const result = await logPracticeAnswer(db, baseInput());

    expect(result.attemptId).toBeGreaterThan(0);
    expect(result.vocabId).not.toBeNull();
    expect(await getAttemptStats(db)).toEqual({ total: 1, correct: 0 });

    const items = await listVocab(db);
    expect(items).toHaveLength(1);
    expect(items[0].lemma).toBe('hope');
    expect(items[0].context_line).toBe('I keep a little hope inside my heart');

    expect(await getDailyStat(db, DAY)).toEqual({
      day: DAY,
      score: 0,
      attempts: 1,
      correct: 0,
      new_words: 1,
    });
  });

  it('答對：寫入作答、不動字庫、統計加一分', async () => {
    const result = await logPracticeAnswer(
      db,
      baseInput({
        userAnswer: 'hope',
        score: 1,
        isCorrect: true,
        vocabCandidate: null,
      })
    );

    expect(result.vocabId).toBeNull();
    expect(await getAttemptStats(db)).toEqual({ total: 1, correct: 1 });
    expect(await getVocabSummary(db)).toEqual({
      total: 0,
      learning: 0,
      reviewing: 0,
      mastered: 0,
    });

    expect(await getDailyStat(db, DAY)).toEqual({
      day: DAY,
      score: 1,
      attempts: 1,
      correct: 1,
      new_words: 0,
    });
  });

  it('同一個字第二次答錯時，不再累計為新單字', async () => {
    await logPracticeAnswer(db, baseInput());
    await logPracticeAnswer(db, baseInput({ now: NOW + 1_000 }));

    const items = await listVocab(db);
    expect(items).toHaveLength(1);
    expect(items[0].error_count).toBe(2);

    const stat = await getDailyStat(db, DAY);
    expect(stat?.new_words).toBe(1); // 只算一次
    expect(stat?.attempts).toBe(2);
  });

  it('帶入歌曲時會寫入快取，並把作答與字庫連到該首歌', async () => {
    const result = await logPracticeAnswer(
      db,
      baseInput({
        song: {
          lrclibId: 197097,
          title: 'Hello',
          artist: 'Adele',
          album: '25',
          durationMs: 296_000,
          coverUrl: null,
          genre: '流行',
          difficulty: null,
          now: NOW,
        },
        lyrics: [{ index: 0, text: 'Hello, it is me', startMs: 6_220, endMs: 11_840 }],
      })
    );

    expect(result.songId).not.toBeNull();

    const song = await findSongByLrclibId(db, 197097);
    expect(song?.title).toBe('Hello');
    expect(await getLyricLines(db, result.songId!)).toHaveLength(1);

    const items = await listVocab(db);
    expect(items[0].first_song_id).toBe(result.songId);
    expect(items[0].song_title).toBe('Hello');

    const attempt = await db.getFirstAsync<{ song_id: number | null; vocab_id: number | null }>(
      'SELECT song_id, vocab_id FROM attempt'
    );
    expect(attempt?.song_id).toBe(result.songId);
    expect(attempt?.vocab_id).toBe(result.vocabId);
  });

  it('作答紀錄保存完整的作答內容', async () => {
    await logPracticeAnswer(db, baseInput());

    const attempt = await db.getFirstAsync<{
      mode_id: string;
      line_index: number;
      prompt: string;
      user_answer: string;
      correct_answer: string;
      is_correct: number;
      duration_ms: number;
      created_at: number;
    }>(
      'SELECT mode_id, line_index, prompt, user_answer, correct_answer, is_correct, duration_ms, created_at FROM attempt'
    );

    expect(attempt).toEqual({
      mode_id: 'fill-choice',
      line_index: 2,
      prompt: 'I keep a little ____ inside my heart',
      user_answer: 'love',
      correct_answer: 'hope',
      is_correct: 0,
      duration_ms: 1_500,
      created_at: NOW,
    });
  });

  it('沒有提供歌曲時，作答與字庫的 song 參照為 null', async () => {
    const result = await logPracticeAnswer(db, baseInput());

    expect(result.songId).toBeNull();
    expect((await listVocab(db))[0].first_song_id).toBeNull();
  });

  it('可以連續記錄多次作答', async () => {
    for (let i = 0; i < 5; i += 1) {
      await logPracticeAnswer(
        db,
        baseInput({
          isCorrect: i % 2 === 0,
          score: i % 2 === 0 ? 1 : 0,
          vocabCandidate:
            i % 2 === 0
              ? null
              : {
                  lemma: `word${i}`,
                  surfaceForm: `Word${i}`,
                  contextLine: `context ${i}`,
                  songId: null,
                },
          now: NOW + i,
        })
      );
    }

    expect(await getAttemptStats(db)).toEqual({ total: 5, correct: 3 });
    expect((await getDailyStat(db, DAY))?.attempts).toBe(5);
    expect((await getVocabSummary(db)).total).toBe(2);
  });
});
