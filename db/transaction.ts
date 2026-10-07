/**
 * 交易輔助。
 *
 * 用 `BEGIN` / `COMMIT` / `ROLLBACK` 實作，因此 expo-sqlite 與 node:sqlite 都適用
 * （不依賴任何一方特有的 API）。
 *
 * **可重入**：SQLite 不支援巢狀 `BEGIN`（會丟 "cannot start a transaction within a
 * transaction"）。當一個 repository 想在交易中呼叫另一個同樣會開交易的 repository
 * （例如 `logPracticeAnswer` → `addVocabFromError`），內層必須直接沿用外層的交易，
 * 由最外層負責提交或回滾。
 */
import type { SqlDatabase } from './types';

/** 目前已開啟交易的資料庫（避免巢狀 BEGIN）。 */
const activeTransactions = new WeakSet<object>();

/** 在單一交易中執行 `task`；失敗時回滾並把錯誤往外丟。可安全巢狀呼叫。 */
export async function withTransaction<T>(
  database: SqlDatabase,
  task: () => Promise<T>
): Promise<T> {
  // 已在交易中：直接執行，成敗交由最外層決定
  if (activeTransactions.has(database)) {
    return task();
  }

  activeTransactions.add(database);
  await database.execAsync('BEGIN');

  try {
    const result = await task();
    await database.execAsync('COMMIT');
    return result;
  } catch (error) {
    await database.execAsync('ROLLBACK');
    throw error;
  } finally {
    activeTransactions.delete(database);
  }
}
