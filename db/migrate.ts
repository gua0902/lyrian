/**
 * Schema 遷移（**不 import 任何資料庫驅動**，因此可在測試中以真 SQLite 執行）。
 *
 * 使用 Expo 官方建議的 `PRAGMA user_version` 模式
 * （見 https://docs.expo.dev/versions/v57.0.0/sdk/sqlite/）。
 */
import { CREATE_TABLES_SQL, SCHEMA_VERSION } from './schema';
import type { SqlDatabase } from './types';

/**
 * 依 `PRAGMA user_version` 遷移。重複呼叫是安全的（已是最新就直接返回）。
 *
 * 新增版本時：在下方加 `if (currentVersion < 2) { ... }`，並把 `SCHEMA_VERSION` +1。
 */
export async function migrate(database: SqlDatabase): Promise<void> {
  const row = await database.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
  const currentVersion = row?.user_version ?? 0;

  if (currentVersion >= SCHEMA_VERSION) return;

  if (currentVersion === 0) {
    await database.execAsync(CREATE_TABLES_SQL);
  }

  await database.execAsync(`PRAGMA user_version = ${SCHEMA_VERSION}`);
}
