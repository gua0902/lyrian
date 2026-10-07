/**
 * 四選一填空（`fill-choice`）的純邏輯。
 *
 * 完全離線、零 AI 依賴 —— 這是 App 的保底體驗（見 docs/ARCHITECTURE.md §6.2）。
 * 本模組不 import 任何 React 或 React Native，因此可完整單元測試。
 *
 * 所有隨機行為都接受可注入的 `rng`，讓測試能固定結果。
 */
import type {
  Attempt,
  ChoiceRecord,
  FillChoiceDataset,
  FillChoiceQuestion,
  LyricLine,
  Progress,
} from './types';

/**
 * 可挖空單字的最小長度（4）。
 *
 * **以 `UI references/Lyrian · 歌詞練習.png` 為準**——該參考圖的答案是 Hope 與 Love，皆為 4 字母。
 * 這與 `docs/REQUIREMENTS.md` Spec 2 的「長度 > 4」不同，已於 2026-10-06 由擁有者裁決：
 * **以參考圖為準**，因為 love / hope / hold / heart 這類學習者最需要的常用字才挖得到。
 *
 * 需要更嚴格的門檻時，傳 `minWordLength` 給 `findBlankCandidates` 或
 * `buildFillChoiceDataset` 即可覆寫。
 */
export const BLANK_MIN_WORD_LENGTH = 4;

/** 每題固定 4 個選項（依 UI references 的 2x2 選項格）。 */
export const OPTION_COUNT = 4;

export type Rng = () => number;

const defaultRng: Rng = Math.random;

/**
 * 當同一首歌湊不出 3 個相異干擾項時的備援字庫。
 * 沒有這個的話，短歌會出現少於 4 個選項（原 `search.tsx` 的缺陷）。
 */
const FALLBACK_DISTRACTORS = [
  'heart', 'light', 'dream', 'world', 'music', 'night', 'story', 'voice',
  'water', 'happy', 'young', 'place', 'sound', 'peace', 'smile', 'truth',
  'grace', 'shine', 'river', 'cloud', 'color', 'dance', 'quiet', 'brave',
];

export interface BlankCandidate {
  word: string;
  start: number;
  end: number;
}

/**
 * 找出可挖空的候選單字，附帶在原文中的位置。
 *
 * @param minWordLength 最短可挖空的字長。預設 5（需求書），可傳 4 以符合參考圖。
 */
export function findBlankCandidates(
  text: string,
  minWordLength: number = BLANK_MIN_WORD_LENGTH
): BlankCandidate[] {
  // 每次建立新的 RegExp，避免共用 lastIndex 造成的狀態污染
  const pattern = new RegExp(`\\b[A-Za-z]{${minWordLength},}\\b`, 'g');
  const candidates: BlankCandidate[] = [];

  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    candidates.push({
      word: match[0],
      start: match.index,
      end: match.index + match[0].length,
    });
  }

  return candidates;
}

/** Fisher-Yates 洗牌，不改動輸入陣列。 */
export function shuffle<T>(items: readonly T[], rng: Rng = defaultRng): T[] {
  const result = [...items];
  for (let i = result.length - 1; i > 0; i -= 1) {
    const j = Math.min(i, Math.floor(rng() * (i + 1)));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

/** 比較答案時統一大小寫與前後空白。 */
export function normalizeAnswer(value: string): string {
  return value.trim().toLowerCase();
}

function createQuestion(
  line: LyricLine,
  blankIndex: number,
  candidate: BlankCandidate
): FillChoiceQuestion {
  return {
    blankIndex,
    lineIndex: line.index,
    prefix: line.text.slice(0, candidate.start),
    answer: candidate.word,
    suffix: line.text.slice(candidate.end),
    startMs: line.startMs,
    endMs: line.endMs,
  };
}

/**
 * 把整首歌詞轉成題目集。
 *
 * 每一行**最多挖一個空**（需求書 Spec 2：「每句隨機挖 1 個單字」）。
 * 沒有合格單字的行不會產生題目，但仍保留在 `lines` 中供歌詞流顯示。
 */
export function buildFillChoiceDataset(
  lines: readonly LyricLine[],
  options: { rng?: Rng; minWordLength?: number } = {}
): FillChoiceDataset {
  const rng = options.rng ?? defaultRng;
  const minWordLength = options.minWordLength ?? BLANK_MIN_WORD_LENGTH;
  const questions: FillChoiceQuestion[] = [];

  for (const line of lines) {
    const candidates = findBlankCandidates(line.text, minWordLength);
    if (candidates.length === 0) continue;

    const pick = Math.min(candidates.length - 1, Math.floor(rng() * candidates.length));
    questions.push(createQuestion(line, questions.length, candidates[pick]));
  }

  return { lines: [...lines], questions };
}

/**
 * 只計算「可挖空的行數」，不產生題目。
 *
 * 搜尋結果清單要顯示「N 個填空」（對應參考圖的「10 個填空」）時使用，
 * 避免為 20 筆結果各建立一份完整題目集。
 */
export function countBlankableLines(
  lines: readonly LyricLine[],
  minWordLength: number = BLANK_MIN_WORD_LENGTH
): number {
  let count = 0;
  for (const line of lines) {
    if (findBlankCandidates(line.text, minWordLength).length > 0) count += 1;
  }
  return count;
}

/**
 * 產生一題的 4 個選項：正解 + 3 個干擾項。
 *
 * 干擾項優先取自**同一首歌的其他答案**（與參考圖一致），不足時才由備援字庫補齊，
 * 確保永遠是 4 個選項。回傳前會洗牌，正解位置不固定。
 */
export function buildOptions(
  question: FillChoiceQuestion,
  allAnswers: readonly string[],
  options: { rng?: Rng } = {}
): string[] {
  const rng = options.rng ?? defaultRng;
  const needed = OPTION_COUNT - 1;

  const seen = new Set([normalizeAnswer(question.answer)]);
  const distractors: string[] = [];

  const takeFrom = (pool: readonly string[]) => {
    for (const word of shuffle(pool, rng)) {
      if (distractors.length >= needed) return;
      const key = normalizeAnswer(word);
      if (key.length === 0 || seen.has(key)) continue;
      seen.add(key);
      distractors.push(word);
    }
  };

  takeFrom(allAnswers);
  takeFrom(FALLBACK_DISTRACTORS);

  return shuffle([question.answer, ...distractors], rng);
}

/** 把一次作答轉成 `Attempt`（所有模式的唯一收斂點）。 */
export function gradeChoice(question: FillChoiceQuestion, selected: string): Attempt {
  const isCorrect = normalizeAnswer(selected) === normalizeAnswer(question.answer);

  return {
    modeId: 'fill-choice',
    lineIndex: question.lineIndex,
    blankIndex: question.blankIndex,
    prompt: `${question.prefix}____${question.suffix}`,
    userAnswer: selected,
    correctAnswer: question.answer,
    isCorrect,
    score: isCorrect ? 1 : 0,
  };
}

/**
 * 計算作答進度。分母是**全部空格數**，與參考圖「2 / 10 正確（20%）」一致。
 */
export function computeProgress(
  totalQuestions: number,
  records: Iterable<ChoiceRecord>
): Progress {
  let correct = 0;
  for (const record of records) {
    if (record.isCorrect) correct += 1;
  }

  return {
    correct,
    total: totalQuestions,
    percent: totalQuestions > 0 ? Math.round((correct / totalQuestions) * 100) : 0,
  };
}

/**
 * 建立「行序 → 題目」的索引。
 *
 * UI 需要它才能在歌詞流中判斷某一行是否有空格、以及是否已作答
 * （參考圖中已答對的空格會在原行以彩色文字呈現）。
 */
export function indexQuestionsByLine(
  questions: readonly FillChoiceQuestion[]
): Map<number, FillChoiceQuestion> {
  const map = new Map<number, FillChoiceQuestion>();
  for (const question of questions) {
    map.set(question.lineIndex, question);
  }
  return map;
}
