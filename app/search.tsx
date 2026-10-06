import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  FlatList,
  ActivityIndicator,
  SafeAreaView,
  Platform,
} from 'react-native';

export type GameStatus = 'isSearching' | 'isReady' | 'isPlaying' | 'isFinished';

export interface BlankQuestion {
  originalLine: string;
  hasBlank: boolean;
  prefix: string;
  answer: string;
  suffix: string;
  timeSeconds: number; // 同步時間 (秒)
  blankIndex: number; // 在所有空格中的序號
}

/**
 * 隨機挖掉一個長度 > 4 的英文單字
 */
export function extractBlank(
  line: string,
  timeSeconds: number = 0
): {
  originalLine: string;
  hasBlank: boolean;
  prefix: string;
  answer: string;
  suffix: string;
  timeSeconds: number;
} {
  const wordRegex = /\b[A-Za-z]{5,}\b/g;
  const matches = [...line.matchAll(wordRegex)];

  if (matches.length === 0) {
    return {
      originalLine: line,
      hasBlank: false,
      prefix: line,
      answer: '',
      suffix: '',
      timeSeconds,
    };
  }

  const randomIndex = Math.floor(Math.random() * matches.length);
  const selectedMatch = matches[randomIndex];
  const targetWord = selectedMatch[0];
  const startIndex = selectedMatch.index ?? 0;
  const endIndex = startIndex + targetWord.length;

  return {
    originalLine: line,
    hasBlank: true,
    prefix: line.slice(0, startIndex),
    answer: targetWord,
    suffix: line.slice(endIndex),
    timeSeconds,
  };
}

/**
 * Fisher-Yates 洗牌演算法
 */
function shuffleArray<T>(array: T[]): T[] {
  const arr = [...array];
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

/**
 * 對空格生成選項：
 * 選項 = [正解] + 從所有 answers 裡隨機抽 3 個 != 正解 且互相不重複，湊滿 4 個後 shuffle
 */
export function generateRandomOptions(answers: string[], currentAnswer: string): string[] {
  const normCurrent = currentAnswer.trim().toLowerCase();

  // 取得所有與正解不重複（不分大小寫）的候選單字集合
  const candidatePool: string[] = [];
  const seenCandidate = new Set<string>();

  for (const ans of answers) {
    const norm = ans.trim().toLowerCase();
    if (norm !== normCurrent && !seenCandidate.has(norm)) {
      seenCandidate.add(norm);
      candidatePool.push(ans);
    }
  }

  // 隨機抽 3 個
  const shuffledCandidates = shuffleArray(candidatePool);
  const pickedDistractors = shuffledCandidates.slice(0, 3);

  // 湊成 [正解] + 抽出的選項
  const options = [currentAnswer, ...pickedDistractors];

  return shuffleArray(options);
}

/**
 * 解析 [mm:ss.xx] 為秒數
 */
export function parseLrcTime(timeStr: string): number {
  const match = timeStr.match(/^(\d{2,}):(\d{2})(?:\.(\d+))?$/);
  if (!match) return 0;
  const minutes = parseInt(match[1], 10);
  const seconds = parseInt(match[2], 10);
  const fraction = match[3] ? parseFloat(`0.${match[3]}`) : 0;
  return minutes * 60 + seconds + fraction;
}

/**
 * 解析 syncedLyrics 文字為歌詞行與時間
 */
export function parseSyncedLyrics(syncedLyrics: string): { text: string; time: number }[] {
  const lines = syncedLyrics.split('\n');
  const result: { text: string; time: number }[] = [];

  for (const rawLine of lines) {
    const trimmed = rawLine.trim();
    if (!trimmed) continue;

    const timeMatch = trimmed.match(/^\[(\d{2,}:\d{2}(?:\.\d+)?)\](.*)$/);
    if (timeMatch) {
      const time = parseLrcTime(timeMatch[1]);
      const text = timeMatch[2].trim();
      if (text.length > 0) {
        result.push({ text, time });
      }
    }
  }

  return result.sort((a, b) => a.time - b.time);
}

export default function SearchScreen() {
  // 輸入欄位
  const [artist, setArtist] = useState('');
  const [track, setTrack] = useState('');
  const [currentSongTitle, setCurrentSongTitle] = useState('');

  // 狀態機：isSearching -> isReady -> isPlaying -> isFinished
  const [status, setStatus] = useState<GameStatus>('isSearching');

  // 資料狀態
  const [questions, setQuestions] = useState<BlankQuestion[]>([]);
  const [allAnswers, setAllAnswers] = useState<string[]>([]);
  const [totalDuration, setTotalDuration] = useState(0);

  // 作答紀錄：userSelections[blankIdx] = { choice, isCorrect }
  const [userSelections, setUserSelections] = useState<{
    [blankIdx: number]: { choice: string; isCorrect: boolean };
  }>({});

  // 當前待答空格指標與其4個選項
  const [activeBlankIndex, setActiveBlankIndex] = useState(0);
  const [bottomOptions, setBottomOptions] = useState<string[]>([]);

  // 播放時間
  const [currentSeconds, setCurrentSeconds] = useState(0);

  /**
   * 當前高亮行：**純粹由 currentSeconds 推導**，不需要用 state 存放。
   * （原本以 state + effect 實作，會造成 cascading render 並被 lint 擋下。）
   */
  const currentLine = useMemo(() => {
    if (questions.length === 0) return -1;

    let activeIdx = -1;
    for (let i = 0; i < questions.length; i++) {
      if (currentSeconds >= questions[i].timeSeconds) {
        activeIdx = i;
      } else {
        break;
      }
    }
    return activeIdx;
  }, [currentSeconds, questions]);

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const flatListRef = useRef<FlatList>(null);

  // 所有空格的列表
  const blankQuestions = useMemo(() => {
    return questions.filter((q) => q.hasBlank);
  }, [questions]);

  const totalBlanks = blankQuestions.length;

  // 即時計算分數
  const scoreStats = useMemo(() => {
    let correct = 0;
    Object.values(userSelections).forEach((item) => {
      if (item.isCorrect) correct += 1;
    });
    const percentage = totalBlanks > 0 ? Math.round((correct / totalBlanks) * 100) : 0;
    return { correct, total: totalBlanks, percentage };
  }, [userSelections, totalBlanks]);

  // 1. Timer 每秒累加，到達 totalDuration 自動停止
  useEffect(() => {
    let interval: ReturnType<typeof setInterval> | null = null;

    if (status === 'isPlaying') {
      interval = setInterval(() => {
        setCurrentSeconds((prev) => {
          const nextSec = prev + 1;
          if (totalDuration > 0 && nextSec >= totalDuration) {
            setStatus('isFinished');
            return totalDuration;
          }
          return nextSec;
        });
      }, 1000);
    }

    return () => {
      if (interval) clearInterval(interval);
    };
  }, [status, totalDuration]);

  // 2. 播放中自動捲動到當前句（外部系統副作用，與上面的純推導分離）
  const lastScrolledLineRef = useRef(-1);
  useEffect(() => {
    if (status !== 'isPlaying' || currentLine < 0) return;
    if (lastScrolledLineRef.current === currentLine) return;
    lastScrolledLineRef.current = currentLine;

    try {
      flatListRef.current?.scrollToIndex({
        index: currentLine,
        animated: true,
        viewPosition: 0.3,
      });
    } catch {
      // FlatList 尚未 render 完成
    }
  }, [currentLine, status]);

  // 3. 搜尋 API 與初始化
  const handleSearch = async () => {
    const trimmedArtist = artist.trim();
    const trimmedTrack = track.trim();

    if (!trimmedArtist || !trimmedTrack) {
      setErrorMsg('請輸入歌手與歌名');
      return;
    }

    setErrorMsg('');
    setLoading(true);
    setQuestions([]);
    setAllAnswers([]);
    setUserSelections({});
    setCurrentSeconds(0);
    setActiveBlankIndex(0);
    setBottomOptions([]);
    lastScrolledLineRef.current = -1;

    try {
      const url = `https://lrclib.net/api/get?artist_name=${encodeURIComponent(
        trimmedArtist
      )}&track_name=${encodeURIComponent(trimmedTrack)}`;

      const response = await fetch(url, {
        headers: {
          'User-Agent': 'LyrianApp/1.0.0 (https://github.com/example/lyrian)',
        },
      });

      if (!response.ok) {
        if (response.status === 404) {
          setErrorMsg('找不到此歌曲的歌詞');
        } else {
          setErrorMsg(`搜尋失敗 (代碼: ${response.status})`);
        }
        return;
      }

      const data = await response.json();
      const syncedLyrics: string | undefined = data.syncedLyrics;
      const plainLyrics: string | undefined = data.plainLyrics;

      let parsedItems: { text: string; time: number }[] = [];

      if (syncedLyrics && syncedLyrics.trim().length > 0) {
        parsedItems = parseSyncedLyrics(syncedLyrics);
      }

      if (parsedItems.length === 0 && plainLyrics && plainLyrics.trim().length > 0) {
        const lines = plainLyrics
          .split('\n')
          .map((line) => line.trim())
          .filter((line) => line.length > 0);
        parsedItems = lines.map((text, idx) => ({ text, time: idx * 4 }));
      }

      if (parsedItems.length > 0) {
        const rawBlanks = parsedItems.map((item) => extractBlank(item.text, item.time));

        // 收集所有空格答案
        const answers: string[] = [];
        rawBlanks.forEach((item) => {
          if (item.hasBlank && item.answer) {
            answers.push(item.answer);
          }
        });

        let currentBlankIndex = 0;
        const fullQuestions: BlankQuestion[] = rawBlanks.map((item) => {
          if (!item.hasBlank) {
            return {
              ...item,
              blankIndex: -1,
            };
          }
          const blankItem: BlankQuestion = {
            ...item,
            blankIndex: currentBlankIndex,
          };
          currentBlankIndex += 1;
          return blankItem;
        });

        // 計算 totalDuration
        const lastLrcTimestamp =
          parsedItems.length > 0 ? parsedItems[parsedItems.length - 1].time : 0;
        const songDuration = data.duration
          ? Math.round(data.duration)
          : Math.round(lastLrcTimestamp + 5);

        setTotalDuration(songDuration);
        setQuestions(fullQuestions);
        setAllAnswers(answers);
        setCurrentSongTitle(`${data.trackName || trimmedTrack} - ${data.artistName || trimmedArtist}`);

        // 初始化第一個空格的4個選項
        if (answers.length > 0) {
          const firstOptions = generateRandomOptions(answers, answers[0]);
          setBottomOptions(firstOptions);
        }

        setStatus('isReady');
      } else {
        setErrorMsg('這首歌沒有可用歌詞');
      }
    } catch (err: any) {
      setErrorMsg(err.message || '連線錯誤，請稍後再試');
    } finally {
      setLoading(false);
    }
  };

  /**
   * 點選底部選項區的小按鈕
   */
  const handleSelectBottomOption = (selectedChoice: string) => {
    if (activeBlankIndex >= totalBlanks) return;

    const currentBlankQ = blankQuestions[activeBlankIndex];
    if (!currentBlankQ) return;

    const isCorrect =
      selectedChoice.trim().toLowerCase() === currentBlankQ.answer.trim().toLowerCase();

    // 1. 更新使用者作答與分數紀錄
    setUserSelections((prev) => ({
      ...prev,
      [activeBlankIndex]: {
        choice: selectedChoice,
        isCorrect,
      },
    }));

    // 2. 指向下一題
    const nextIdx = activeBlankIndex + 1;
    setActiveBlankIndex(nextIdx);

    // 3. 立刻為下一個空格生成選項並刷新 Bottom 區
    if (nextIdx < totalBlanks) {
      const nextBlankQ = blankQuestions[nextIdx];
      const nextOptions = generateRandomOptions(allAnswers, nextBlankQ.answer);
      setBottomOptions(nextOptions);
    } else {
      setBottomOptions([]);
    }
  };

  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.container}>
        {/* ===================== Top 區 ===================== */}
        <View style={styles.topSection}>
          {status === 'isSearching' ? (
            <View style={styles.formContainer}>
              <Text style={styles.title}>歌詞 4 選 1 挖空挑戰</Text>
              <Text style={styles.label}>歌手名稱</Text>
              <TextInput
                style={styles.input}
                placeholder="例如: Adele"
                placeholderTextColor="#888"
                value={artist}
                onChangeText={setArtist}
                autoCapitalize="words"
                returnKeyType="next"
              />

              <Text style={styles.label}>歌曲名稱</Text>
              <TextInput
                style={styles.input}
                placeholder="例如: Hello"
                placeholderTextColor="#888"
                value={track}
                onChangeText={setTrack}
                autoCapitalize="words"
                returnKeyType="search"
                onSubmitEditing={handleSearch}
              />

              <TouchableOpacity
                style={[styles.searchButton, loading && styles.searchButtonDisabled]}
                onPress={handleSearch}
                disabled={loading}
                activeOpacity={0.8}
              >
                {loading ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <Text style={styles.searchButtonText}>搜尋並開始</Text>
                )}
              </TouchableOpacity>
              {errorMsg ? <Text style={styles.errorText}>{errorMsg}</Text> : null}
            </View>
          ) : (
            /* 播放前 / 播放中 / 結束時的頂部資訊條 */
            <View style={styles.songHeaderBar}>
              <View style={styles.songMeta}>
                <Text style={styles.songTitleText} numberOfLines={1}>
                  {currentSongTitle}
                </Text>
                <Text style={styles.timerScoreText}>
                  {formatTimer(currentSeconds)} / {formatTimer(totalDuration)}
                  {'  '}|{'  '}得分: {scoreStats.correct}/{scoreStats.total} (
                  {scoreStats.percentage}%)
                </Text>
              </View>

              <View style={styles.topControls}>
                {status === 'isReady' && (
                  <TouchableOpacity
                    style={[styles.ctrlBtn, styles.playBtn]}
                    onPress={() => setStatus('isPlaying')}
                  >
                    <Text style={styles.ctrlBtnText}>開始播放</Text>
                  </TouchableOpacity>
                )}
                {status === 'isPlaying' && (
                  <TouchableOpacity
                    style={[styles.ctrlBtn, styles.pauseBtn]}
                    onPress={() => setStatus('isReady')}
                  >
                    <Text style={styles.ctrlBtnText}>暫停</Text>
                  </TouchableOpacity>
                )}
                {status === 'isFinished' && (
                  <TouchableOpacity
                    style={[styles.ctrlBtn, styles.replayBtn]}
                    onPress={() => {
                      setCurrentSeconds(0);
                      setActiveBlankIndex(0);
                      setUserSelections({});
                      if (allAnswers.length > 0) {
                        setBottomOptions(generateRandomOptions(allAnswers, allAnswers[0]));
                      }
                      setStatus('isPlaying');
                    }}
                  >
                    <Text style={styles.ctrlBtnText}>重新挑戰</Text>
                  </TouchableOpacity>
                )}
                <TouchableOpacity
                  style={[styles.ctrlBtn, styles.reSearchBtn]}
                  onPress={() => {
                    setStatus('isSearching');
                    setCurrentSeconds(0);
                    setActiveBlankIndex(0);
                    setUserSelections({});
                    setQuestions([]);
                  }}
                >
                  <Text style={styles.ctrlBtnText}>重選歌曲</Text>
                </TouchableOpacity>
              </View>
            </View>
          )}
        </View>

        {/* ===================== Middle 區：FlatList 歌詞 ===================== */}
        <View style={styles.middleSection}>
          {questions.length > 0 ? (
            <FlatList
              ref={flatListRef}
              data={questions}
              keyExtractor={(_, index) => index.toString()}
              onScrollToIndexFailed={() => {}}
              contentContainerStyle={styles.listContent}
              showsVerticalScrollIndicator={true}
              renderItem={({ item, index }) => {
                const isCurrent = currentLine === index;
                const hasAnswered =
                  item.hasBlank && item.blankIndex in userSelections;
                const record = hasAnswered ? userSelections[item.blankIndex] : null;
                const isTargetBlank =
                  item.hasBlank && item.blankIndex === activeBlankIndex;

                return (
                  <View
                    style={[
                      styles.lyricLineContainer,
                      isCurrent && styles.activeLineHighlight,
                    ]}
                  >
                    <Text
                      style={[
                        styles.lineNumber,
                        isCurrent && styles.activeLineNumber,
                      ]}
                    >
                      {formatTimer(Math.floor(item.timeSeconds))}
                    </Text>

                    {item.hasBlank ? (
                      <View style={styles.lineContainer}>
                        <Text
                          style={[
                            styles.lyricText,
                            isCurrent && styles.activeLyricText,
                          ]}
                        >
                          {item.prefix}
                          {hasAnswered && record ? (
                            <Text
                              style={[
                                styles.blankBadge,
                                record.isCorrect
                                  ? styles.blankBadgeCorrect
                                  : styles.blankBadgeWrong,
                              ]}
                            >
                              {` [${record.choice}] `}
                            </Text>
                          ) : (
                            <Text
                              style={[
                                styles.blankBadge,
                                isTargetBlank
                                  ? styles.blankBadgeActiveTarget
                                  : styles.blankBadgePending,
                              ]}
                            >
                              {' [____] '}
                            </Text>
                          )}
                          {item.suffix}
                        </Text>
                        {hasAnswered && record && !record.isCorrect && (
                          <Text style={styles.wrongAnswerHint}>正解: {item.answer}</Text>
                        )}
                      </View>
                    ) : (
                      <Text
                        style={[
                          styles.lyricText,
                          isCurrent && styles.activeLyricText,
                        ]}
                      >
                        {item.originalLine}
                      </Text>
                    )}
                  </View>
                );
              }}
            />
          ) : status !== 'isSearching' ? (
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyText}>無歌詞內容</Text>
            </View>
          ) : null}
        </View>

        {/* ===================== Bottom 區：合併選項區 (固定 120px) ===================== */}
        {status === 'isPlaying' ? (
          <View style={styles.bottomSection}>
            <View style={styles.bottomHeader}>
              <Text style={styles.bottomPrompt}>
                空格 #{activeBlankIndex + 1} / {totalBlanks} 請選擇：
              </Text>
            </View>
            <View style={styles.optionsRow}>
              {bottomOptions.length > 0 ? (
                bottomOptions.map((opt, optIdx) => (
                  <TouchableOpacity
                    key={`${optIdx}-${opt}`}
                    style={styles.optionBox}
                    onPress={() => handleSelectBottomOption(opt)}
                    activeOpacity={0.7}
                  >
                    <Text style={styles.optionBoxText} numberOfLines={1}>
                      {opt}
                    </Text>
                  </TouchableOpacity>
                ))
              ) : (
                <Text style={styles.allDoneText}>🎉 所有題目皆已填答完成！</Text>
              )}
            </View>
          </View>
        ) : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0f172a',
  },
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },

  // 1. Top Section
  topSection: {
    backgroundColor: '#0f172a',
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#1e293b',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#ffffff',
    marginBottom: 12,
  },
  formContainer: {
    paddingVertical: 4,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: '#94a3b8',
    marginBottom: 4,
  },
  input: {
    height: 42,
    borderWidth: 1,
    borderColor: '#334155',
    borderRadius: 8,
    paddingHorizontal: 12,
    fontSize: 15,
    color: '#ffffff',
    backgroundColor: '#1e293b',
    marginBottom: 10,
  },
  searchButton: {
    backgroundColor: '#2563eb',
    height: 42,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 2,
  },
  searchButtonDisabled: {
    backgroundColor: '#1d4ed8',
  },
  searchButtonText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '600',
  },
  errorText: {
    color: '#ef4444',
    fontSize: 13,
    marginTop: 8,
    textAlign: 'center',
  },
  songHeaderBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  songMeta: {
    flex: 1,
    marginRight: 10,
  },
  songTitleText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: 'bold',
  },
  timerScoreText: {
    color: '#38bdf8',
    fontSize: 13,
    marginTop: 4,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    fontWeight: '600',
  },
  topControls: {
    flexDirection: 'row',
    gap: 6,
  },
  ctrlBtn: {
    paddingVertical: 7,
    paddingHorizontal: 12,
    borderRadius: 6,
  },
  playBtn: {
    backgroundColor: '#16a34a',
  },
  pauseBtn: {
    backgroundColor: '#d97706',
  },
  replayBtn: {
    backgroundColor: '#7c3aed',
  },
  reSearchBtn: {
    backgroundColor: '#475569',
  },
  ctrlBtnText: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: 'bold',
  },

  // 2. Middle Section (FlatList)
  middleSection: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  listContent: {
    paddingBottom: 24,
  },
  lyricLineContainer: {
    flexDirection: 'row',
    paddingVertical: 10,
    paddingHorizontal: 12,
    backgroundColor: '#ffffff',
    borderRadius: 8,
    marginBottom: 6,
    alignItems: 'flex-start',
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  activeLineHighlight: {
    backgroundColor: '#fef08a',
    borderColor: '#eab308',
  },
  lineNumber: {
    width: 44,
    fontSize: 12,
    color: '#94a3b8',
    paddingTop: 3,
  },
  activeLineNumber: {
    color: '#854d0e',
    fontWeight: 'bold',
  },
  lineContainer: {
    flex: 1,
  },
  lyricText: {
    fontSize: 15,
    color: '#1e293b',
    lineHeight: 24,
  },
  activeLyricText: {
    fontWeight: 'bold',
    color: '#713f12',
  },
  blankBadge: {
    fontWeight: 'bold',
  },
  blankBadgeActiveTarget: {
    color: '#2563eb',
    backgroundColor: '#dbeafe',
  },
  blankBadgePending: {
    color: '#64748b',
  },
  blankBadgeCorrect: {
    color: '#16a34a',
  },
  blankBadgeWrong: {
    color: '#dc2626',
  },
  wrongAnswerHint: {
    fontSize: 12,
    color: '#dc2626',
    marginTop: 4,
    fontWeight: '600',
  },
  emptyContainer: {
    alignItems: 'center',
    marginTop: 40,
  },
  emptyText: {
    color: '#64748b',
    fontSize: 15,
  },

  // 3. Bottom Section (固定 120px)
  bottomSection: {
    height: 120,
    backgroundColor: '#0f172a',
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 10,
    justifyContent: 'space-between',
    borderTopWidth: 2,
    borderTopColor: '#2563eb',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -3 },
    shadowOpacity: 0.25,
    shadowRadius: 5,
    elevation: 8,
  },
  bottomHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  bottomPrompt: {
    color: '#93c5fd',
    fontSize: 13,
    fontWeight: '600',
  },
  optionsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
    marginBottom: 4,
  },
  optionBox: {
    flex: 1,
    height: 60,
    backgroundColor: '#1e293b',
    borderWidth: 1.5,
    borderColor: '#38bdf8',
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 4,
  },
  optionBoxText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: 'bold',
    textAlign: 'center',
  },
  allDoneText: {
    flex: 1,
    color: '#4ade80',
    fontSize: 15,
    fontWeight: 'bold',
    textAlign: 'center',
    paddingVertical: 14,
  },
});
