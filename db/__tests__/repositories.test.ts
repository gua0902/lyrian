/**
 * Repository 的真實 SQL 驗證。
 *
 * 全部跑在 node:sqlite 開出來的真資料庫上，因此驗到的是**實際的 SQL**
 * （欄位名、upsert 語意、累加邏輯、JOIN），不是假物件。
 */
import { migrate } from '../migrate';
import {
  addVocabFromError,
  bumpDailyStat,
  getAttemptStats,
  getDailyStat,
  getLyricLines,
  getStudyStreak,
  getTotalScore,
  getVocabSummary,
  listRecentAttempts,
  listVocab,
  recordAttempt,
  saveLyricLines,
  saveSong,
  withTransaction,
} from '../repositories';
import { findSongByLrclibId } from '../repositories/songs';
import { createNodeDatabase, type NodeSqlDatabase } from '../testing/createNodeDatabase';

const NOW = 1_760_000_000_000;

let db: NodeSqlDatabase;

beforeEach(async () => {
  db = createNodeDatabase();
  await migrate(db);
});

afterEach(() => {
  db.close();
});

async function seedSong(lrclibId = 197097): Promise<number> {
  return saveSong(db, {
    lrclibId,
    title: 'Hello',
    artist: 'Adele',
    album: '25',
    durationMs: 296_000,
    coverUrl: 'https://example.com/600x600bb.jpg',
    genre: '流行',
    difficulty: null,
    now: NOW,
  });
}

describe('songs', () => {
  it('新增後可用 lrclib_id 查回', async () => {
    const songId = await seedSong();
    const row = await findSongByLrclibId(db, 197097);

    expect(row?.id).toBe(songId);
    expect(row?.title).toBe('Hello');
    expect(row?.genre).toBe('流行');
  });

  it('同一首 lrclib_id 重複儲存時是更新而非新增（回傳同一個 id）', async () => {
    const first = await seedSong();
    const second = await saveSong(db, {
      lrclibId: 197097,
      title: 'Hello (Remastered)',
      artist: 'Adele',
      album: '25',
      durationMs: 295_000,
      coverUrl: null,
      genre: '流行',
      difficulty: 'beginner',
      now: NOW + 1,
    });

    expect(second).toBe(first);

    const rows = await db.getAllAsync('SELECT * FROM song');
    expect(rows).toHaveLength(1);

    const row = await findSongByLrclibId(db, 197097);
    expect(row?.title).toBe('Hello (Remastered)');
    expect(row?.duration_ms).toBe(295_000);
    expect(row?.difficulty).toBe('beginner');
  });

  it('沒有封面時存 null', async () => {
    await saveSong(db, {
      lrclibId: 1,
      title: 'T',
      artist: 'A',
      album: null,
      durationMs: null,
      coverUrl: null,
      genre: null,
      difficulty: null,
      now: NOW,
    });

    const row = await findSongByLrclibId(db, 1);
    expect(row?.cover_url).toBeNull();
    expect(row?.duration_ms).toBeNull();
  });

  it('儲存歌詞行後可依序取回', async () => {
    const songId = await seedSong();
    await saveLyricLines(db, songId, [
      { index: 0, text: 'Hello, it is me', startMs: 6_220, endMs: 11_840 },
      { index: 1, text: 'I was wondering', startMs: 11_840, endMs: null },
    ]);

    const lines = await getLyricLines(db, songId);
    expect(lines).toEqual([
      { index: 0, text: 'Hello, it is me', startMs: 6_220, endMs: 11_840 },
      { index: 1, text: 'I was wondering', startMs: 11_840, endMs: null },
    ]);
  });

  it('重新儲存歌詞會覆寫，不會留下過時的舊行', async () => {
    const songId = await seedSong();

    await saveLyricLines(db, songId, [
      { index: 0, text: 'old 0', startMs: 0, endMs: null },
      { index: 1, text: 'old 1', startMs: 1, endMs: null },
      { index: 2, text: 'old 2', startMs: 2, endMs: null },
    ]);
    await saveLyricLines(db, songId, [{ index: 0, text: 'new 0', startMs: 0, endMs: null }]);

    const lines = await getLyricLines(db, songId);
    expect(lines).toEqual([{ index: 0, text: 'new 0', startMs: 0, endMs: null }]);
  });
});

describe('attempts', () => {
  it('寫入並取回最近的作答紀錄（新到舊）', async () => {
    await recordAttempt(db, {
      modeId: 'fill-choice',
      songId: null,
      lineIndex: 0,
      vocabId: null,
      prompt: 'I keep a little ____',
      userAnswer: 'love',
      correctAnswer: 'hope',
      score: 0,
      isCorrect: false,
      durationMs: 1_200,
      now: NOW,
    });
    await recordAttempt(db, {
      modeId: 'fill-choice',
      songId: null,
      lineIndex: 1,
      vocabId: null,
      prompt: 'a ____ b',
      userAnswer: 'x',
      correctAnswer: 'x',
      score: 1,
      isCorrect: true,
      durationMs: null,
      now: NOW + 10,
    });

    const attempts = await listRecentAttempts(db);
    expect(attempts).toHaveLength(2);
    expect(attempts[0].is_correct).toBe(1); // 較新的在前
    expect(attempts[1].user_answer).toBe('love');
  });

  it('is_correct 以 0/1 儲存', async () => {
    await recordAttempt(db, {
      modeId: 'fill-choice',
      songId: null,
      lineIndex: 0,
      vocabId: null,
      prompt: 'p',
      userAnswer: 'a',
      correctAnswer: 'b',
      score: 0,
      isCorrect: false,
      durationMs: null,
      now: NOW,
    });

    const row = await db.getFirstAsync<{ is_correct: number }>('SELECT is_correct FROM attempt');
    expect(row?.is_correct).toBe(0);
  });

  it('統計總數與答對數', async () => {
    for (const correct of [true, true, false]) {
      await recordAttempt(db, {
        modeId: 'fill-choice',
        songId: null,
        lineIndex: 0,
        vocabId: null,
        prompt: 'p',
        userAnswer: 'a',
        correctAnswer: 'b',
        score: correct ? 1 : 0,
        isCorrect: correct,
        durationMs: null,
        now: NOW,
      });
    }

    expect(await getAttemptStats(db)).toEqual({ total: 3, correct: 2 });
  });

  it('沒有任何作答時統計為 0（不會是 null）', async () => {
    expect(await getAttemptStats(db)).toEqual({ total: 0, correct: 0 });
  });
});

describe('vocab', () => {
  it('第一次加入時 error_count 為 1、標記為新字，並建立 FSRS 排程', async () => {
    const added = await addVocabFromError(db, {
      lemma: 'hope',
      surfaceForm: 'Hope',
      translation: null,
      contextLine: 'I keep a little hope inside my heart',
      songId: null,
      now: NOW,
    });

    expect(added.isNew).toBe(true);

    const row = await db.getFirstAsync<{ error_count: number; status: string }>(
      'SELECT error_count, status FROM vocab WHERE id = ?',
      added.id
    );
    expect(row).toEqual({ error_count: 1, status: 'learning' });

    const schedule = await db.getAllAsync(
      'SELECT * FROM vocab_schedule WHERE vocab_id = ?',
      added.id
    );
    expect(schedule).toHaveLength(1);
  });

  it('同一個 lemma 再答錯時累加 error_count 而不新增一列', async () => {
    const first = await addVocabFromError(db, {
      lemma: 'hope',
      surfaceForm: 'hope',
      translation: null,
      contextLine: 'first context',
      songId: null,
      now: NOW,
    });
    const second = await addVocabFromError(db, {
      lemma: 'hope',
      surfaceForm: 'HOPE',
      translation: null,
      contextLine: 'second context',
      songId: null,
      now: NOW + 100,
    });

    expect(second.id).toBe(first.id);
    expect(first.isNew).toBe(true);
    expect(second.isNew).toBe(false); // 第二次是累加，不是新字
    expect(await db.getAllAsync('SELECT * FROM vocab')).toHaveLength(1);

    const row = await db.getFirstAsync<{
      error_count: number;
      surface_form: string;
      context_line: string;
      created_at: number;
      updated_at: number;
    }>('SELECT error_count, surface_form, context_line, created_at, updated_at FROM vocab');

    expect(row?.error_count).toBe(2);
    expect(row?.context_line).toBe('second context'); // 語境更新為最新
    expect(row?.surface_form).toBe('HOPE');
    expect(row?.created_at).toBe(NOW); // 首次時間保留
    expect(row?.updated_at).toBe(NOW + 100);
  });

  it('已掌握的單字再答錯時退回 learning', async () => {
    const { id: vocabId } = await addVocabFromError(db, {
      lemma: 'hope',
      surfaceForm: null,
      translation: null,
      contextLine: null,
      songId: null,
      now: NOW,
    });
    await db.runAsync("UPDATE vocab SET status = 'mastered' WHERE id = ?", vocabId);

    await addVocabFromError(db, {
      lemma: 'hope',
      surfaceForm: null,
      translation: null,
      contextLine: null,
      songId: null,
      now: NOW + 1,
    });

    const row = await db.getFirstAsync<{ status: string }>('SELECT status FROM vocab');
    expect(row?.status).toBe('learning');
  });

  it('保留第一個來源歌曲', async () => {
    const songId = await seedSong();
    const { id: vocabId } = await addVocabFromError(db, {
      lemma: 'hope',
      surfaceForm: null,
      translation: null,
      contextLine: null,
      songId,
      now: NOW,
    });

    await addVocabFromError(db, {
      lemma: 'hope',
      surfaceForm: null,
      translation: null,
      contextLine: null,
      songId: null,
      now: NOW + 1,
    });

    const row = await db.getFirstAsync<{ first_song_id: number | null }>(
      'SELECT first_song_id FROM vocab WHERE id = ?',
      vocabId
    );
    expect(row?.first_song_id).toBe(songId);
  });

  it('清單帶出來源歌曲名稱', async () => {
    const songId = await seedSong();
    await addVocabFromError(db, {
      lemma: 'hope',
      surfaceForm: 'Hope',
      translation: null,
      contextLine: 'I keep a little hope',
      songId,
      now: NOW,
    });

    const items = await listVocab(db);
    expect(items).toHaveLength(1);
    expect(items[0].song_title).toBe('Hello');
    expect(items[0].song_artist).toBe('Adele');
  });

  it('可依狀態篩選', async () => {
    const now = NOW;
    await addVocabFromError(db, { lemma: 'a', surfaceForm: null, translation: null, contextLine: null, songId: null, now });
    await addVocabFromError(db, { lemma: 'b', surfaceForm: null, translation: null, contextLine: null, songId: null, now });
    await db.runAsync("UPDATE vocab SET status = 'mastered' WHERE lemma = 'b'");

    expect(await listVocab(db)).toHaveLength(2);
    expect((await listVocab(db, { status: 'mastered' })).map((i) => i.lemma)).toEqual(['b']);
    expect((await listVocab(db, { status: 'learning' })).map((i) => i.lemma)).toEqual(['a']);
  });

  it('統計各狀態數量', async () => {
    const now = NOW;
    await addVocabFromError(db, { lemma: 'a', surfaceForm: null, translation: null, contextLine: null, songId: null, now });
    await addVocabFromError(db, { lemma: 'b', surfaceForm: null, translation: null, contextLine: null, songId: null, now });
    await addVocabFromError(db, { lemma: 'c', surfaceForm: null, translation: null, contextLine: null, songId: null, now });
    await db.runAsync("UPDATE vocab SET status = 'mastered' WHERE lemma = 'b'");
    await db.runAsync("UPDATE vocab SET status = 'reviewing' WHERE lemma = 'c'");

    expect(await getVocabSummary(db)).toEqual({ total: 3, learning: 1, reviewing: 1, mastered: 1 });
  });

  it('空字庫的統計全為 0', async () => {
    expect(await getVocabSummary(db)).toEqual({ total: 0, learning: 0, reviewing: 0, mastered: 0 });
  });
});

describe('stats', () => {
  it('同一天累加而不新增一列', async () => {
    await bumpDailyStat(db, { day: '2026-10-07', score: 10, attempts: 1, correct: 1, newWords: 1 });
    await bumpDailyStat(db, { day: '2026-10-07', score: 5, attempts: 2, correct: 1, newWords: 0 });

    const row = await getDailyStat(db, '2026-10-07');
    expect(row).toEqual({ day: '2026-10-07', score: 15, attempts: 3, correct: 2, new_words: 1 });
    expect(await db.getAllAsync('SELECT * FROM daily_stat')).toHaveLength(1);
  });

  it('未提供欄位時以 0 計', async () => {
    await bumpDailyStat(db, { day: '2026-10-07' });
    expect(await getDailyStat(db, '2026-10-07')).toEqual({
      day: '2026-10-07',
      score: 0,
      attempts: 0,
      correct: 0,
      new_words: 0,
    });
  });

  it('累積總分跨日加總', async () => {
    await bumpDailyStat(db, { day: '2026-10-06', score: 10 });
    await bumpDailyStat(db, { day: '2026-10-07', score: 32 });

    expect(await getTotalScore(db)).toBe(42);
  });

  it('沒有紀錄時總分為 0', async () => {
    expect(await getTotalScore(db)).toBe(0);
  });

  it('連續學習天數：連續三天', async () => {
    for (const day of ['2026-10-05', '2026-10-06', '2026-10-07']) {
      await bumpDailyStat(db, { day });
    }

    expect(await getStudyStreak(db, '2026-10-07')).toBe(3);
  });

  it('連續學習天數：中間中斷就停止計算', async () => {
    for (const day of ['2026-10-04', '2026-10-06', '2026-10-07']) {
      await bumpDailyStat(db, { day });
    }

    expect(await getStudyStreak(db, '2026-10-07')).toBe(2);
  });

  it('連續學習天數：今天還沒練時從昨天起算，不會歸零', async () => {
    for (const day of ['2026-10-05', '2026-10-06']) {
      await bumpDailyStat(db, { day });
    }

    expect(await getStudyStreak(db, '2026-10-07')).toBe(2);
  });

  it('連續學習天數：完全沒有紀錄時為 0', async () => {
    expect(await getStudyStreak(db, '2026-10-07')).toBe(0);
  });
});

describe('withTransaction', () => {
  it('成功時提交', async () => {
    await withTransaction(db, async () => {
      await bumpDailyStat(db, { day: '2026-10-07', score: 7 });
    });

    expect(await getTotalScore(db)).toBe(7);
  });

  it('失敗時回滾，不留下半截資料', async () => {
    await expect(
      withTransaction(db, async () => {
        await bumpDailyStat(db, { day: '2026-10-07', score: 7 });
        throw new Error('boom');
      })
    ).rejects.toThrow('boom');

    expect(await db.getAllAsync('SELECT * FROM daily_stat')).toHaveLength(0);
  });

  it('可巢狀呼叫（內層不會再開一次 BEGIN）', async () => {
    await withTransaction(db, async () => {
      await withTransaction(db, async () => {
        await bumpDailyStat(db, { day: '2026-10-07', score: 3 });
      });
      await bumpDailyStat(db, { day: '2026-10-07', score: 4 });
    });

    expect(await getTotalScore(db)).toBe(7);
  });

  it('巢狀時由最外層回滾，內層的寫入也一併撤銷', async () => {
    await expect(
      withTransaction(db, async () => {
        await withTransaction(db, async () => {
          await bumpDailyStat(db, { day: '2026-10-07', score: 3 });
        });
        throw new Error('outer fails');
      })
    ).rejects.toThrow('outer fails');

    expect(await db.getAllAsync('SELECT * FROM daily_stat')).toHaveLength(0);
  });

  it('內層失敗時整筆回滾，且交易狀態不會卡住', async () => {
    await expect(
      withTransaction(db, async () => {
        await withTransaction(db, async () => {
          await bumpDailyStat(db, { day: '2026-10-07', score: 3 });
          throw new Error('inner fails');
        });
      })
    ).rejects.toThrow('inner fails');

    expect(await db.getAllAsync('SELECT * FROM daily_stat')).toHaveLength(0);

    // 交易旗標必須已清除，後續寫入才能正常開啟新交易
    await withTransaction(db, async () => {
      await bumpDailyStat(db, { day: '2026-10-08', score: 1 });
    });
    expect(await getTotalScore(db)).toBe(1);
  });
});
