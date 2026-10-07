import {
  BLANK_MIN_WORD_LENGTH,
  DEFAULT_MAX_BLANKS,
  OPTION_COUNT,
  buildFillChoiceDataset,
  buildOptions,
  computeProgress,
  countBlankableLines,
  createSeededRng,
  findBlankCandidates,
  gradeChoice,
  indexQuestionsByLine,
  normalizeAnswer,
  pickEvenlySpaced,
  shuffle,
} from '../fillChoice';
import type { ChoiceRecord, FillChoiceQuestion, LyricLine } from '../types';

/** Park-Miller 線性同餘產生器：讓隨機行為在測試中可重現。 */
function seededRng(seed = 1): () => number {
  let state = seed % 2_147_483_647;
  if (state <= 0) state += 2_147_483_646;
  return () => {
    state = (state * 16_807) % 2_147_483_647;
    return (state - 1) / 2_147_483_646;
  };
}

function line(index: number, text: string, startMs = index * 1_000, endMs: number | null = null): LyricLine {
  return { index, text, startMs, endMs };
}

function record(isCorrect: boolean): ChoiceRecord {
  return { choice: isCorrect ? 'hope' : 'love', isCorrect };
}

describe('findBlankCandidates', () => {
  it('預設找出長度 >= 4 的純字母單字（依參考圖）', () => {
    expect(findBlankCandidates('A little love is ours to hold').map((c) => c.word)).toEqual([
      'little',
      'love',
      'ours',
      'hold',
    ]);
  });

  it('回報正確的起訖位置', () => {
    const text = 'I keep a little hope inside';
    const [candidate] = findBlankCandidates(text, 6);
    expect(candidate.word).toBe('little');
    expect(text.slice(candidate.start, candidate.end)).toBe('little');
  });

  it('minWordLength 可調高以限制候選', () => {
    expect(findBlankCandidates('A little love is ours to hold', 6).map((c) => c.word)).toEqual([
      'little',
    ]);
  });

  it('忽略縮寫造成的短片段', () => {
    expect(findBlankCandidates("it's a don't")).toEqual([]);
  });

  it('數字與標點不算單字', () => {
    expect(findBlankCandidates('12345 --- 6789 !!')).toEqual([]);
  });

  it('沒有合格單字時回傳空陣列', () => {
    expect(findBlankCandidates('a b c')).toEqual([]);
    expect(findBlankCandidates('')).toEqual([]);
  });

  it('可重複呼叫而不受 lastIndex 狀態影響', () => {
    const text = 'little inside morning';
    expect(findBlankCandidates(text)).toHaveLength(3);
    expect(findBlankCandidates(text)).toHaveLength(3);
  });
});

describe('shuffle', () => {
  it('不改動輸入陣列', () => {
    const input = [1, 2, 3, 4];
    shuffle(input, seededRng(1));
    expect(input).toEqual([1, 2, 3, 4]);
  });

  it('保留所有元素', () => {
    const result = shuffle([1, 2, 3, 4, 5], seededRng(7));
    expect([...result].sort()).toEqual([1, 2, 3, 4, 5]);
  });

  it('相同種子產生相同結果（可重現）', () => {
    expect(shuffle([1, 2, 3, 4, 5], seededRng(42))).toEqual(shuffle([1, 2, 3, 4, 5], seededRng(42)));
  });
});

describe('createSeededRng', () => {
  it('相同種子產生相同序列（讓畫面重繪時選項不跳動）', () => {
    const a = createSeededRng(7);
    const b = createSeededRng(7);
    expect([a(), a(), a()]).toEqual([b(), b(), b()]);
  });

  it('不同種子產生不同序列', () => {
    expect(createSeededRng(1)()).not.toBe(createSeededRng(2)());
  });

  it('值域落在 [0, 1)', () => {
    const rng = createSeededRng(123);
    for (let i = 0; i < 200; i += 1) {
      const value = rng();
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThan(1);
    }
  });

  it('能讓同一題的選項穩定重現', () => {
    const question: FillChoiceQuestion = {
      blankIndex: 2,
      lineIndex: 3,
      prefix: 'I keep a little ',
      answer: 'hope',
      suffix: ' inside',
      startMs: 0,
      endMs: null,
    };

    const first = buildOptions(question, ['love', 'heart'], { rng: createSeededRng(3) });
    const second = buildOptions(question, ['love', 'heart'], { rng: createSeededRng(3) });
    expect(first).toEqual(second);
    expect(first).toHaveLength(OPTION_COUNT);
  });
});

describe('normalizeAnswer', () => {
  it('忽略大小寫與前後空白', () => {
    expect(normalizeAnswer('  Hope ')).toBe('hope');
  });
});

describe('buildFillChoiceDataset', () => {
  const lines = [
    line(0, 'The morning paints the sky in gold'),
    line(1, 'A little love is ours to hold'),
    line(2, 'I keep a little hope inside my heart'),
    line(3, 'A brand-new day, a brand-new start'),
  ];

  it('每行最多挖一個空', () => {
    const { questions } = buildFillChoiceDataset(lines, { rng: seededRng(1) });
    const perLine = new Map<number, number>();
    for (const question of questions) {
      perLine.set(question.lineIndex, (perLine.get(question.lineIndex) ?? 0) + 1);
    }
    expect([...perLine.values()].every((count) => count === 1)).toBe(true);
  });

  it('blankIndex 從 0 連續遞增（對應參考圖「填空 03」）', () => {
    const { questions } = buildFillChoiceDataset(lines, { rng: seededRng(3) });
    expect(questions.map((q) => q.blankIndex)).toEqual(
      questions.map((_, position) => position)
    );
  });

  it('prefix + answer + suffix 可還原原句（不變式）', () => {
    for (let seed = 1; seed <= 20; seed += 1) {
      const { questions } = buildFillChoiceDataset(lines, { rng: seededRng(seed) });
      for (const question of questions) {
        const original = lines[question.lineIndex].text;
        expect(`${question.prefix}${question.answer}${question.suffix}`).toBe(original);
      }
    }
  });

  it('答案長度符合門檻', () => {
    const { questions } = buildFillChoiceDataset(lines, { rng: seededRng(5) });
    expect(questions.every((q) => q.answer.length >= BLANK_MIN_WORD_LENGTH)).toBe(true);
  });

  it('沒有合格單字的行不產生題目，但仍在 lines 中', () => {
    const { lines: keptLines, questions } = buildFillChoiceDataset(
      [line(0, 'a b c'), line(1, 'wonderful')],
      { rng: seededRng(1) }
    );

    expect(keptLines).toHaveLength(2);
    expect(questions).toHaveLength(1);
    expect(questions[0].lineIndex).toBe(1);
  });

  it('保留該行的時間軸', () => {
    const { questions } = buildFillChoiceDataset([line(0, 'wonderful night', 5_000, 9_000)], {
      rng: seededRng(1),
    });
    expect(questions[0].startMs).toBe(5_000);
    expect(questions[0].endMs).toBe(9_000);
  });

  it('預設門檻 4 可挖到 love / hold', () => {
    const { questions } = buildFillChoiceDataset([line(0, 'A love to hold')], {
      rng: seededRng(1),
    });
    expect(questions).toHaveLength(1);
    expect(['love', 'hold']).toContain(questions[0].answer);
  });

  it('minWordLength 可調高以排除短字', () => {
    const { questions } = buildFillChoiceDataset([line(0, 'A love to hold')], {
      rng: seededRng(1),
      minWordLength: 5,
    });
    expect(questions).toHaveLength(0);
  });
});

describe('pickEvenlySpaced', () => {
  it('取樣數量大於等於長度時回傳全部', () => {
    expect(pickEvenlySpaced([1, 2, 3], 5)).toEqual([1, 2, 3]);
    expect(pickEvenlySpaced([1, 2, 3], 3)).toEqual([1, 2, 3]);
  });

  it('取樣 0 或空清單回傳空陣列', () => {
    expect(pickEvenlySpaced([1, 2, 3], 0)).toEqual([]);
    expect(pickEvenlySpaced([], 3)).toEqual([]);
  });

  it('取樣 1 個時取第一個', () => {
    expect(pickEvenlySpaced([1, 2, 3, 4], 1)).toEqual([1]);
  });

  it('涵蓋頭尾且數量正確', () => {
    const picked = pickEvenlySpaced([0, 1, 2, 3, 4, 5, 6, 7, 8, 9], 5);
    expect(picked).toHaveLength(5);
    expect(picked[0]).toBe(0);
    expect(picked[4]).toBe(9);
  });

  it('結果遞增且不重複', () => {
    const picked = pickEvenlySpaced(
      Array.from({ length: 48 }, (_, i) => i),
      10
    );
    expect(picked).toEqual([...picked].sort((a, b) => a - b));
    expect(new Set(picked).size).toBe(picked.length);
  });
});

describe('每首歌的挖空上限', () => {
  // 實測 Adele《Hello》在門檻 4 之下有 48 行可挖
  const manyLines = Array.from({ length: 48 }, (_, i) => line(i, `wonderful morning number ${i}`));

  it('預設上限為 10（依參考圖的「共 10 個」）', () => {
    expect(DEFAULT_MAX_BLANKS).toBe(10);
    expect(buildFillChoiceDataset(manyLines, { rng: seededRng(1) }).questions).toHaveLength(10);
  });

  it('maxBlanks 可覆寫', () => {
    const { questions } = buildFillChoiceDataset(manyLines, { rng: seededRng(1), maxBlanks: 3 });
    expect(questions).toHaveLength(3);
  });

  it('可挖空的行少於上限時全部挖空', () => {
    const few = [line(0, 'wonderful morning'), line(1, 'another lovely day')];
    expect(buildFillChoiceDataset(few, { rng: seededRng(1) }).questions).toHaveLength(2);
  });

  it('取樣涵蓋整首歌，而不是只挖前半段', () => {
    const { questions } = buildFillChoiceDataset(manyLines, { rng: seededRng(1) });
    expect(questions[0].lineIndex).toBe(0);
    expect(questions[questions.length - 1].lineIndex).toBe(47);
  });
});

describe('countBlankableLines', () => {
  const manyLines = Array.from({ length: 48 }, (_, i) => line(i, `wonderful morning number ${i}`));

  it('受上限限制，與 buildFillChoiceDataset 一致', () => {
    expect(countBlankableLines(manyLines)).toBe(DEFAULT_MAX_BLANKS);
    expect(countBlankableLines(manyLines, BLANK_MIN_WORD_LENGTH, 3)).toBe(3);
  });

  it('沒有可挖空的行時為 0', () => {
    expect(countBlankableLines([line(0, 'a b c')])).toBe(0);
  });
});

describe('buildOptions', () => {
  const question: FillChoiceQuestion = {
    blankIndex: 0,
    lineIndex: 2,
    prefix: 'I keep a little ',
    answer: 'hope',
    suffix: ' inside my heart',
    startMs: 0,
    endMs: null,
  };

  it('永遠產生 4 個選項', () => {
    expect(buildOptions(question, [], { rng: seededRng(1) })).toHaveLength(OPTION_COUNT);
  });

  it('正解恰好出現一次', () => {
    const options = buildOptions(question, ['love', 'heart', 'hello'], { rng: seededRng(2) });
    expect(options.filter((option) => normalizeAnswer(option) === 'hope')).toHaveLength(1);
  });

  it('選項彼此相異（不分大小寫）', () => {
    const options = buildOptions(question, ['love', 'LOVE', 'heart', 'hello'], { rng: seededRng(4) });
    const normalized = options.map(normalizeAnswer);
    expect(new Set(normalized).size).toBe(normalized.length);
  });

  it('優先使用同一首歌的其他答案', () => {
    const options = buildOptions(question, ['love', 'heart', 'hello'], { rng: seededRng(6) });
    expect([...options].sort()).toEqual(['heart', 'hello', 'hope', 'love']);
  });

  it('同歌答案不足時由備援字庫補滿', () => {
    const options = buildOptions(question, ['love'], { rng: seededRng(8) });
    expect(options).toHaveLength(OPTION_COUNT);
    expect(options).toContain('love');
    expect(options).toContain('hope');
  });

  it('正解本身在備援字庫中時不會被當成干擾項', () => {
    const heartQuestion: FillChoiceQuestion = { ...question, answer: 'heart' };
    const options = buildOptions(heartQuestion, [], { rng: seededRng(9) });
    expect(options.filter((option) => normalizeAnswer(option) === 'heart')).toHaveLength(1);
  });

  it('相同種子產生相同選項', () => {
    expect(buildOptions(question, [], { rng: seededRng(11) })).toEqual(
      buildOptions(question, [], { rng: seededRng(11) })
    );
  });
});

describe('gradeChoice', () => {
  const question: FillChoiceQuestion = {
    blankIndex: 3,
    lineIndex: 7,
    prefix: 'I keep a little ',
    answer: 'hope',
    suffix: ' inside my heart',
    startMs: 1_000,
    endMs: 2_000,
  };

  it('答對時 score 為 1', () => {
    const attempt = gradeChoice(question, 'hope');
    expect(attempt.isCorrect).toBe(true);
    expect(attempt.score).toBe(1);
    expect(attempt.modeId).toBe('fill-choice');
  });

  it('比對時忽略大小寫與空白', () => {
    expect(gradeChoice(question, '  HOPE  ').isCorrect).toBe(true);
  });

  it('答錯時 score 為 0 並保留正確答案', () => {
    const attempt = gradeChoice(question, 'love');
    expect(attempt.isCorrect).toBe(false);
    expect(attempt.score).toBe(0);
    expect(attempt.correctAnswer).toBe('hope');
    expect(attempt.userAnswer).toBe('love');
  });

  it('帶出 blankIndex 與 lineIndex，供字庫與進度使用', () => {
    const attempt = gradeChoice(question, 'hope');
    expect(attempt.blankIndex).toBe(3);
    expect(attempt.lineIndex).toBe(7);
  });

  it('prompt 保留空格前後文', () => {
    expect(gradeChoice(question, 'hope').prompt).toBe('I keep a little ____ inside my heart');
  });
});

describe('computeProgress', () => {
  it('分母是全部空格數（對應參考圖 2 / 10 = 20%）', () => {
    expect(computeProgress(10, [record(true), record(true)])).toEqual({
      correct: 2,
      total: 10,
      percent: 20,
    });
  });

  it('全部答對為 100%', () => {
    expect(computeProgress(2, [record(true), record(true)]).percent).toBe(100);
  });

  it('沒有題目時不會除以零', () => {
    expect(computeProgress(0, [])).toEqual({ correct: 0, total: 0, percent: 0 });
  });

  it('接受 Map 的值', () => {
    const selections = new Map<number, ChoiceRecord>([
      [0, record(true)],
      [1, record(false)],
    ]);
    expect(computeProgress(4, selections.values()).correct).toBe(1);
  });
});

describe('indexQuestionsByLine', () => {
  it('以 lineIndex 建立索引', () => {
    const questions: FillChoiceQuestion[] = [
      { blankIndex: 0, lineIndex: 2, prefix: '', answer: 'alpha', suffix: '', startMs: 0, endMs: null },
      { blankIndex: 1, lineIndex: 5, prefix: '', answer: 'bravo', suffix: '', startMs: 0, endMs: null },
    ];

    const map = indexQuestionsByLine(questions);
    expect(map.get(2)?.answer).toBe('alpha');
    expect(map.get(5)?.answer).toBe('bravo');
    expect(map.get(0)).toBeUndefined();
  });
});
