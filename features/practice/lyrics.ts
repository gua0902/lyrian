/**
 * 歌詞解析。
 *
 * 依 docs/ARCHITECTURE.md §4.1，來源優先序為：
 *   1. `lyricsfile`  —— LRCLIB 的結構化格式，**同時有 start_ms 與 end_ms**
 *   2. `syncedLyrics` —— 標準 LRC，只有開始時間
 *   3. `plainLyrics`  —— 純文字，無時間軸，以固定間隔推算
 *
 * 之所以把 `lyricsfile` 放在最前，是因為 `end_ms` 讓我們知道「這句唱完了沒」，
 * 這對歌詞高亮推進與題目節奏是必要的（見 ARCHITECTURE.md §4.1）。
 */
import type { LyricLine } from './types';

/** 純文字歌詞的每行間隔（毫秒）。 */
export const PLAIN_LYRICS_INTERVAL_MS = 4000;

/**
 * 解析 LRC 的 `[mm:ss.xx]` 為毫秒。
 * 支援 `mm:ss`、`mm:ss.xx`、`mm:ss.xxx`，以及 `mm:ss:xx` 這種以冒號分隔的變體。
 */
export function parseLrcTimestamp(value: string): number | null {
  const match = value.match(/^(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?$/);
  if (!match) return null;

  const minutes = Number(match[1]);
  const seconds = Number(match[2]);
  if (Number.isNaN(minutes) || Number.isNaN(seconds)) return null;

  // 小數位數不固定：.5 = 500ms、.50 = 500ms、.500 = 500ms
  const fraction = match[3] ? Number(`0.${match[3]}`) : 0;

  return Math.round((minutes * 60 + seconds + fraction) * 1000);
}

/** 去掉 YAML 純量外層的引號，並還原基本的跳脫。 */
function unquoteYamlScalar(raw: string): string {
  const value = raw.trim();
  if (value.length < 2) return value;

  const first = value[0];
  const last = value[value.length - 1];

  if (first === "'" && last === "'") {
    return value.slice(1, -1).replace(/''/g, "'");
  }
  if (first === '"' && last === '"') {
    return value.slice(1, -1).replace(/\\"/g, '"').replace(/\\\\/g, '\\');
  }
  return value;
}

/** 從 `key: 123` 這種字串取出整數。 */
function readIntScalar(raw: string): number | null {
  const match = raw.trim().match(/^-?\d+/);
  return match ? Number(match[0]) : null;
}

/** 解析後統一重新編號並依時間排序，同時濾掉空行。 */
function finalize(lines: Array<Omit<LyricLine, 'index'>>): LyricLine[] {
  return lines
    .filter((line) => line.text.trim().length > 0)
    .sort((a, b) => a.startMs - b.startMs)
    .map((line, index) => ({ ...line, index }));
}

/**
 * 解析 LRCLIB 的 `lyricsfile`（YAML-like）。
 *
 * 目標形狀：
 * ```yaml
 * lines:
 * - text: Hello, it's me
 *   start_ms: 6220
 *   end_ms: 11840
 * ```
 * 刻意不引入 YAML 套件：格式固定，且未知的鍵（例如逐字同步的 `words:`）會被忽略。
 */
export function parseLyricsFile(source: string): LyricLine[] {
  if (!source) return [];

  const collected: Array<Omit<LyricLine, 'index'>> = [];
  let current: Omit<LyricLine, 'index'> | null = null;
  /**
   * 頂層清單項目的縮排寬度。第一次遇到 `- text:` 時鎖定，
   * 之後更深縮排的 `- text:` 視為巢狀項目（例如逐字同步的 `words:`）並忽略。
   */
  let itemIndent: number | null = null;

  const push = () => {
    if (current) collected.push(current);
    current = null;
  };

  for (const rawLine of source.split(/\r?\n/)) {
    const textMatch = rawLine.match(/^([ \t]*)-[ \t]*text:[ \t]*(.*)$/);
    if (textMatch) {
      const indent = textMatch[1].length;
      if (itemIndent === null) itemIndent = indent;
      if (indent !== itemIndent) continue;

      push();
      current = { text: unquoteYamlScalar(textMatch[2]), startMs: 0, endMs: null };
      continue;
    }

    if (!current) continue;

    const startMatch = rawLine.match(/^[ \t]*start_ms:[ \t]*(.*)$/);
    if (startMatch) {
      current.startMs = readIntScalar(startMatch[1]) ?? 0;
      continue;
    }

    const endMatch = rawLine.match(/^[ \t]*end_ms:[ \t]*(.*)$/);
    if (endMatch) {
      current.endMs = readIntScalar(endMatch[1]);
    }
  }
  push();

  return finalize(collected);
}

/**
 * 解析標準 LRC（`syncedLyrics`）。時間軸只有開始時間，因此 `endMs` 為 null。
 * 一行可帶多個時間標籤（`[00:01.00][00:05.00] 歌詞`），每個標籤各產生一行。
 */
export function parseSyncedLyrics(source: string): LyricLine[] {
  if (!source) return [];

  const tagPattern = /\[(\d{1,3}:\d{2}(?:[.:]\d{1,3})?)\]/g;
  const collected: Array<Omit<LyricLine, 'index'>> = [];

  for (const rawLine of source.split(/\r?\n/)) {
    tagPattern.lastIndex = 0;

    const tags: number[] = [];
    let match: RegExpExecArray | null;
    let lastTagEnd = 0;

    while ((match = tagPattern.exec(rawLine)) !== null) {
      const ms = parseLrcTimestamp(match[1]);
      if (ms !== null) tags.push(ms);
      lastTagEnd = match.index + match[0].length;
    }

    if (tags.length === 0) continue;

    const text = rawLine.slice(lastTagEnd).trim();
    if (text.length === 0) continue;

    for (const startMs of tags) {
      collected.push({ text, startMs, endMs: null });
    }
  }

  return finalize(collected);
}

/**
 * 解析純文字歌詞（`plainLyrics`）。沒有時間軸，以固定間隔推算，
 * 僅為了讓畫面能運作；實際節奏不會準確。
 */
export function parsePlainLyrics(
  source: string,
  intervalMs: number = PLAIN_LYRICS_INTERVAL_MS
): LyricLine[] {
  if (!source) return [];

  const collected = source
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((text, position) => ({
      text,
      startMs: position * intervalMs,
      endMs: (position + 1) * intervalMs,
    }));

  return finalize(collected);
}

export interface RawLyrics {
  lyricsfile?: string | null;
  syncedLyrics?: string | null;
  plainLyrics?: string | null;
}

/** 依優先序嘗試三種來源，回傳第一個解析出內容的結果。 */
export function parseLyrics(input: RawLyrics): LyricLine[] {
  const attempts = [
    () => parseLyricsFile(input.lyricsfile ?? ''),
    () => parseSyncedLyrics(input.syncedLyrics ?? ''),
    () => parsePlainLyrics(input.plainLyrics ?? ''),
  ];

  for (const attempt of attempts) {
    const lines = attempt();
    if (lines.length > 0) return lines;
  }
  return [];
}
