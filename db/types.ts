/**
 * 資料庫抽象層。
 *
 * 為什麼要這層：App 執行時用的是 **expo-sqlite**（原生模組，jest 無法真的開檔），
 * 但我們希望在測試中用**真正的 SQLite** 驗證 schema 與 SQL（不是假物件）。
 * Node 22+ 內建 `node:sqlite`，因此只要依賴這個最小介面，就能兩邊共用同一份 repository。
 *
 * 介面刻意與 expo-sqlite 的 `SQLiteDatabase` **結構相容**，所以 App 端可以直接傳入。
 */

/** 可绑定的參數型別（刻意不含 boolean / Uint8Array：node:sqlite 不支援）。 */
export type SqlParam = string | number | null;

export interface SqlRunResult {
  changes: number;
  lastInsertRowId: number;
}

export interface SqlDatabase {
  /** 執行多段 SQL。**不做參數跳脫**，只能用在寫死的語句。 */
  execAsync(sql: string): Promise<void>;
  runAsync(sql: string, ...params: SqlParam[]): Promise<SqlRunResult>;
  getFirstAsync<T>(sql: string, ...params: SqlParam[]): Promise<T | null>;
  getAllAsync<T>(sql: string, ...params: SqlParam[]): Promise<T[]>;
}
