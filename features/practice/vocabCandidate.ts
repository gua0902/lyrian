/**
 * 決定「哪些字要進單字庫」。
 *
 * 依 docs/ARCHITECTURE.md §3 的架構鐵律：`Attempt` 是唯一收斂點，
 * 而「什麼值得收藏」是**領域決策**，因此放在這裡（不放在 UI，也不放在 DB 層）。
 *
 * 需求：`docs/REQUIREMENTS.md`——「最後可能是將不會或是聽錯的放入單字庫」。
 */
import { normalizeAnswer } from './fillChoice';
import type { Attempt, FillChoiceQuestion } from './types';

export interface VocabCandidate {
  /**
   * 去重鍵。
   *
   * 目前等於正規化後的答案；P1.5 接上 ECDICT 的 `exchange` 欄位後，
   * 會改用真正的詞形還原（讓 loved / loving / loves 收斂成 love）。
   */
  lemma: string;
  /** 原文實際出現的形（例如 `Hope`） */
  surfaceForm: string;
  /** 出現的完整歌詞原句——字庫複習時當題幹用 */
  contextLine: string;
  songId: number | null;
}

/**
 * 從一次作答決定是否要收進字庫。
 *
 * **只有答錯才收**；答對回傳 null。
 */
export function vocabCandidateFromAttempt(
  question: FillChoiceQuestion,
  attempt: Attempt,
  songId: number | null
): VocabCandidate | null {
  if (attempt.isCorrect) return null;

  return {
    lemma: normalizeAnswer(question.answer),
    surfaceForm: question.answer,
    contextLine: `${question.prefix}${question.answer}${question.suffix}`,
    songId,
  };
}
