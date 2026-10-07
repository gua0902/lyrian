import type { Attempt, FillChoiceQuestion } from '../types';
import { vocabCandidateFromAttempt } from '../vocabCandidate';

const QUESTION: FillChoiceQuestion = {
  blankIndex: 2,
  lineIndex: 2,
  prefix: 'I keep a little ',
  answer: 'hope',
  suffix: ' inside my heart',
  startMs: 0,
  endMs: null,
};

function attempt(overrides: Partial<Attempt> = {}): Attempt {
  return {
    modeId: 'fill-choice',
    lineIndex: 2,
    blankIndex: 2,
    prompt: 'I keep a little ____ inside my heart',
    userAnswer: 'love',
    correctAnswer: 'hope',
    isCorrect: false,
    score: 0,
    ...overrides,
  };
}

describe('vocabCandidateFromAttempt', () => {
  it('答對時不收藏（回傳 null）', () => {
    expect(vocabCandidateFromAttempt(QUESTION, attempt({ isCorrect: true }), null)).toBeNull();
  });

  it('答錯時回傳收藏目標', () => {
    expect(vocabCandidateFromAttempt(QUESTION, attempt(), null)).toEqual({
      lemma: 'hope',
      surfaceForm: 'hope',
      contextLine: 'I keep a little hope inside my heart',
      songId: null,
    });
  });

  it('lemma 會正規化為小寫，讓同一個字收斂成一筆', () => {
    const upper: FillChoiceQuestion = { ...QUESTION, answer: 'Hope' };
    expect(vocabCandidateFromAttempt(upper, attempt(), null)?.lemma).toBe('hope');
  });

  it('保留原文的實際大小寫於 surfaceForm', () => {
    const upper: FillChoiceQuestion = { ...QUESTION, answer: 'Hope' };
    expect(vocabCandidateFromAttempt(upper, attempt(), null)?.surfaceForm).toBe('Hope');
  });

  it('contextLine 是可還原的完整歌詞原句', () => {
    const candidate = vocabCandidateFromAttempt(QUESTION, attempt(), null);
    expect(candidate?.contextLine).toBe(
      `${QUESTION.prefix}${QUESTION.answer}${QUESTION.suffix}`
    );
  });

  it('帶上來源歌曲 id', () => {
    expect(vocabCandidateFromAttempt(QUESTION, attempt(), 42)?.songId).toBe(42);
  });
});
