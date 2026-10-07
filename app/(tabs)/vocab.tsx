import { useFocusEffect } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { M3Card, M3SegmentedButton, useTheme } from '@/components/m3';
import { ScreenHeader } from '@/components/ScreenHeader';
import { format, strings } from '@/constants/strings';
import { spacing, typography } from '@/constants/theme';
import { getDatabase } from '@/db/database';
import {
  getVocabSummary,
  listVocab,
  type VocabListItem,
  type VocabSummary,
} from '@/db/repositories/vocab';

type FilterValue = 'all' | 'mastered' | 'reviewing';

const FILTER_OPTIONS = [
  { value: 'all' as const, label: strings.vocab.filterAll },
  { value: 'mastered' as const, label: strings.vocab.filterMastered },
  { value: 'reviewing' as const, label: strings.vocab.filterReviewing },
];

/** 分段控制器只分三類，但資料庫的狀態有四種（learning/reviewing/mastered + 全部）。 */
function statusForFilter(filter: FilterValue): string | undefined {
  if (filter === 'all') return undefined;
  if (filter === 'mastered') return 'mastered';
  // 「待複習」= 尚未掌握的全部
  return 'learning';
}

/**
 * Tab 3：字庫。
 *
 * 對齊 `UI references/Lyrian · 我的字庫.png`：統計橫幅、全部/已掌握/待複習分段控制器、
 * 單字卡（單字／歌詞原句／來源歌曲／錯誤次數）。
 *
 * 資料來自練習時的作答：答錯的單字會連同歌詞原句一起收進來
 * （見 `db/repositories/practiceLog.ts` 與 `features/practice/vocabCandidate.ts`）。
 */
export default function VocabTabScreen() {
  const { colors } = useTheme();

  const [filter, setFilter] = useState<FilterValue>('all');
  const [items, setItems] = useState<VocabListItem[]>([]);
  const [summary, setSummary] = useState<VocabSummary | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');

  const load = useCallback(async (activeFilter: FilterValue) => {
    try {
      const database = await getDatabase();
      const [rows, counts] = await Promise.all([
        listVocab(database, { status: statusForFilter(activeFilter) }),
        getVocabSummary(database),
      ]);

      setItems(rows);
      setSummary(counts);
      setStatus('ready');
    } catch (error) {
      console.warn('[Lyrian] 讀取字庫失敗：', error);
      setStatus('error');
    }
  }, []);

  // 每次回到這個 Tab 都重新讀取（剛練習完的單字要立刻出現）
  useFocusEffect(
    useCallback(() => {
      void load(filter);
    }, [filter, load])
  );

  const header = (
    <View>
      <ScreenHeader
        title={strings.vocab.title}
        icon={{ ios: 'bookmark', android: 'bookmark', web: 'bookmark' }}
      />

      <M3Card
        variant="filled"
        backgroundColor={colors.primaryContainer}
        style={styles.banner}
      >
        <Text style={[typography.titleMedium, { color: colors.onPrimaryContainer }]}>
          {strings.vocab.bannerTitle}
        </Text>
        <Text
          style={[typography.bodySmall, styles.bannerSubtitle, { color: colors.onPrimaryContainer }]}
        >
          {format(strings.vocab.bannerSubtitle, {
            collected: summary?.total ?? 0,
            due: (summary?.learning ?? 0) + (summary?.reviewing ?? 0),
          })}
        </Text>
      </M3Card>

      <M3SegmentedButton
        options={FILTER_OPTIONS}
        value={filter}
        onChange={setFilter}
        style={styles.filters}
      />

      <View style={styles.listHeader}>
        <Text style={[typography.titleMedium, { color: colors.onSurface }]}>
          {strings.vocab.sectionRecentWrong}
        </Text>
        <Text style={[typography.bodySmall, { color: colors.onSurfaceVariant }]}>
          {strings.vocab.sectionCollectedAt}
        </Text>
      </View>
    </View>
  );

  const renderItem = useCallback(
    ({ item }: { item: VocabListItem }) => (
      <View style={[styles.wordRow, { borderBottomColor: colors.outlineVariant }]}>
        <View style={styles.wordMeta}>
          <View style={styles.wordTitleRow}>
            <Text style={[typography.titleLarge, styles.word, { color: colors.primary }]}>
              {item.surface_form ?? item.lemma}
            </Text>
            {item.translation ? (
              <Text style={[typography.bodyMedium, { color: colors.onSurface }]}>
                {item.translation}
              </Text>
            ) : null}
          </View>

          {item.context_line ? (
            <Text style={[typography.bodyLarge, styles.context, { color: colors.onSurface }]}>
              {item.context_line}
            </Text>
          ) : null}

          <View style={styles.wordFooter}>
            <Text style={[typography.bodySmall, { color: colors.onSurfaceVariant }]}>
              {item.song_title
                ? `${item.song_title}${item.song_artist ? strings.search.metaSeparator + item.song_artist : ''}`
                : ''}
            </Text>
            <Text style={[typography.bodySmall, { color: colors.error }]}>
              {format(strings.vocab.errorCountFormat, { count: item.error_count })}
            </Text>
          </View>
        </View>
      </View>
    ),
    [colors]
  );

  const renderEmpty = () => {
    if (status === 'loading') {
      return (
        <View style={styles.centered}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      );
    }

    const message =
      status === 'error'
        ? strings.vocab.loadFailed
        : items.length === 0 && filter !== 'all'
          ? strings.vocab.emptyFiltered
          : strings.vocab.emptyHint;

    return (
      <Text style={[typography.bodyMedium, styles.emptyMessage, { color: colors.onSurfaceVariant }]}>
        {message}
      </Text>
    );
  };

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.surface }]} edges={['top']}>
      <FlatList
        data={items}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderItem}
        ListHeaderComponent={header}
        ListEmptyComponent={renderEmpty}
        ListFooterComponent={
          items.length > 0 ? (
            <View style={styles.footer}>
              <SymbolView
                name={{ ios: 'bookmark', android: 'bookmark', web: 'bookmark' }}
                tintColor={colors.primary}
                size={16}
                style={styles.footerIcon}
              />
              <Text style={[typography.bodySmall, { color: colors.onSurfaceVariant }]}>
                {strings.vocab.remindFooter}
              </Text>
            </View>
          ) : null
        }
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
  },
  listContent: {
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.xxl,
  },
  banner: {
    marginBottom: spacing.lg,
  },
  bannerSubtitle: {
    marginTop: spacing.xs,
  },
  filters: {
    marginBottom: spacing.lg,
  },
  listHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  wordRow: {
    paddingVertical: spacing.lg,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  wordMeta: {
    gap: spacing.xs,
  },
  wordTitleRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
  },
  word: {
    flexShrink: 1,
  },
  context: {
    marginTop: spacing.xs,
  },
  wordFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: spacing.xs,
  },
  centered: {
    alignItems: 'center',
    marginTop: spacing.xl,
  },
  emptyMessage: {
    textAlign: 'center',
    marginTop: spacing.xl,
  },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: spacing.xl,
  },
  footerIcon: {
    marginRight: spacing.sm,
  },
});
