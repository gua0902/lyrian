/**
 * 練習階段的交接。
 *
 * 為什麼需要它：搜尋結果的 `SongSearchResult` **已經包含完整歌詞**（LRCLIB 的 `/api/search`
 * 一次就回傳歌詞），若改用路由參數傳遞會塞進大量字串，選歌後再重新請求也是浪費。
 * 因此用一個模組層級的暫存把手上的資料交給練習畫面。
 *
 * 這裡**只 import type**，所以不會與 `features/search` → `features/practice` 的執行期相依形成循環。
 */
import type { PracticeModeId } from './practice';
import type { SongSearchResult } from './search';

export interface PracticeSession {
  song: SongSearchResult;
  modeId: PracticeModeId;
}

let pendingSession: PracticeSession | null = null;

/** 設定待開始的練習階段。 */
export function setPendingSession(session: PracticeSession): void {
  pendingSession = session;
}

/** 讀取但不清除（畫面重繪時可重複取得）。 */
export function peekPendingSession(): PracticeSession | null {
  return pendingSession;
}

/** 讀取並清除（練習結束或離開時使用）。 */
export function takePendingSession(): PracticeSession | null {
  const session = pendingSession;
  pendingSession = null;
  return session;
}

/** 清除（測試或使用者改變心意時使用）。 */
export function clearPendingSession(): void {
  pendingSession = null;
}
