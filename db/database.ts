/**
 * App 執行時的資料庫入口（expo-sqlite）。
 *
 * 只負責「開啟連線 + 執行遷移」；遷移邏輯在 `db/migrate.ts`（不依賴驅動，可測試）。
 */
import * as SQLite from 'expo-sqlite';

import { migrate } from './migrate';
import { DATABASE_NAME } from './schema';
import type { SqlDatabase } from './types';

let databasePromise: Promise<SqlDatabase> | null = null;

/** 取得資料庫；第一次呼叫時開啟並執行遷移。重複呼叫共用同一條連線。 */
export function getDatabase(): Promise<SqlDatabase> {
  if (!databasePromise) {
    databasePromise = openAndMigrate();
  }
  return databasePromise;
}

async function openAndMigrate(): Promise<SqlDatabase> {
  const database = await SQLite.openDatabaseAsync(DATABASE_NAME);
  await migrate(database);
  return database;
}

/** 測試或需要重新開啟時重置快取的連線。 */
export function resetDatabaseCache(): void {
  databasePromise = null;
}
