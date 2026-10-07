/**
 * 練習模式的領域型別。
 *
 * 依 docs/ARCHITECTURE.md §5 / §6：
 * - `Attempt` 是所有練習模式的**唯一收斂點**，字庫、分數、統計只認它。
 * - `PracticeModeId` 目前只有兩個模式。
 */

export type PracticeModeId = 'fill-choice' | 'zh-translate';

/** 一行歌詞。時間軸單位一律是毫秒。 */
export interface LyricLine {
  /** 在歌曲中的行序（0-based，解析後重新編號） */
  index: number;
  text: string;
  startMs: number;
  /** 只有 lyricsfile 來源才有；其他來源為 null */
  endMs: number | null;
}

/**
 * 一個挖空題目。
 *
 * `prefix` / `suffix` 是答案前後的原文字串，UI 負責在中間渲染空格，
 * 例如 prefix="I keep a little " + answer="hope" + suffix=" inside my heart"。
 */
export interface FillChoiceQuestion {
  /** 在所有空格中的序號（0-based）。UI 顯示時 +1，對應參考圖的「填空 03」 */
  blankIndex: number;
  /** 對應的 `LyricLine.index` */
  lineIndex: number;
  prefix: string;
  answer: string;
  suffix: string;
  startMs: number;
  endMs: number | null;
}

/** 一首歌的完整四選一資料集。 */
export interface FillChoiceDataset {
  /** 全部歌詞行（含沒有挖空的行），供歌詞流渲染 */
  lines: LyricLine[];
  /** 全部題目，依歌詞順序排列 */
  questions: FillChoiceQuestion[];
}

/** 作答進度。對應參考圖的「作答進度：2 / 10 正確（20%）」 */
export interface Progress {
  correct: number;
  total: number;
  percent: number;
}

/** 單次作答紀錄。 */
export interface Attempt {
  modeId: PracticeModeId;
  lineIndex: number;
  blankIndex: number;
  prompt: string;
  userAnswer: string;
  correctAnswer: string;
  isCorrect: boolean;
  /** 0..1。選擇題為 0 或 1；翻譯題為相似度分數 */
  score: number;
}

/** 使用者對某個空格的作答結果（供 UI 渲染對錯樣式）。 */
export interface ChoiceRecord {
  choice: string;
  isCorrect: boolean;
}
