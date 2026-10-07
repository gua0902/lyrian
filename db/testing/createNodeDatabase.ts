/**
 * 測試用的資料庫實作。
 *
 * 用 Node 內建的 `node:sqlite`（Node 22+），讓測試跑在**真正的 SQLite** 上，
 * 而不是用假物件敷衍 SQL——欄位名打錯、約束衝突這類問題才驗得出來。
 *
 * ⚠️ 只在測試中使用；App 執行時走 `db/database.ts` 的 expo-sqlite。
 */
import { DatabaseSync } from 'node:sqlite';

import type { SqlDatabase, SqlParam, SqlRunResult } from '../types';

export interface NodeSqlDatabase extends SqlDatabase {
  close(): void;
}

/**
 * 建立測試資料庫。
 *
 * @param location 預設 `:memory:`（每個測試獨立、不落地）
 */
export function createNodeDatabase(location = ':memory:'): NodeSqlDatabase {
  const database = new DatabaseSync(location);

  return {
    async execAsync(sql: string): Promise<void> {
      database.exec(sql);
    },

    async runAsync(sql: string, ...params: SqlParam[]): Promise<SqlRunResult> {
      const result = database.prepare(sql).run(...params);
      return {
        changes: Number(result.changes),
        lastInsertRowId: Number(result.lastInsertRowid),
      };
    },

    async getFirstAsync<T>(sql: string, ...params: SqlParam[]): Promise<T | null> {
      const row = database.prepare(sql).get(...params);
      return (row as T | undefined) ?? null;
    },

    async getAllAsync<T>(sql: string, ...params: SqlParam[]): Promise<T[]> {
      return database.prepare(sql).all(...params) as T[];
    },

    close(): void {
      database.close();
    },
  };
}
