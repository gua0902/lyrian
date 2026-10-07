import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  Keyboard,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { M3BottomSheet, M3Button, M3Card, M3TextField, useTheme } from '@/components/m3';
import { ScreenHeader } from '@/components/ScreenHeader';
import { format, strings } from '@/constants/strings';
import { radius, spacing, typography } from '@/constants/theme';
import type { PracticeModeId } from '@/features/practice';
import { searchSongs, type SongSearchResult } from '@/features/search';
import { setPendingSession } from '@/features/session';
import { formatDuration } from '@/utils/time';

type SearchStatus = 'idle' | 'searching' | 'ready' | 'error';

/**
 * Tab 1：搜尋。
 *
 * 對齊 `UI references/Lyrian · 搜尋.png`：
 * 歌手／歌名輸入 → 紫色藥丸搜尋鈕 → 「找到你的下一首」+ 歌曲數 →
 * 歌曲卡片（封面／歌名／歌手·時長／填空數·曲風／播放鍵）。
 *
 * 點卡片後彈出 M3 Bottom Sheet 選練習模式。參考圖沒有這一頁，
 * 這是 docs/ARCHITECTURE.md §6.4 的設計。
 */
export default function SearchTabScreen() {
  const { colors } = useTheme();
  const router = useRouter();

  const [artist, setArtist] = useState('');
  const [track, setTrack] = useState('');
  const [status, setStatus] = useState<SearchStatus>('idle');
  const [results, setResults] = useState<SongSearchResult[]>([]);
  const [errorMsg, setErrorMsg] = useState('');
  const [selectedSong, setSelectedSong] = useState<SongSearchResult | null>(null);

  const handleSearch = useCallback(async () => {
    const trimmedArtist = artist.trim();
    const trimmedTrack = track.trim();

    if (!trimmedArtist || !trimmedTrack) {
      setStatus('error');
      setResults([]);
      setErrorMsg(strings.search.errorNoInput);
      return;
    }

    Keyboard.dismiss();
    setStatus('searching');
    setErrorMsg('');

    try {
      const found = await searchSongs({ artist: trimmedArtist, track: trimmedTrack });
      setResults(found);
      setStatus('ready');
    } catch (error) {
      setResults([]);
      setStatus('error');
      const message = error instanceof Error ? error.message : strings.search.errorGeneric;
      setErrorMsg(format(strings.search.errorSearchFailedFormat, { message }));
    }
  }, [artist, track]);

  const handleSelectMode = useCallback(
    (modeId: PracticeModeId) => {
      if (!selectedSong) return;

      setPendingSession({ song: selectedSong, modeId });
      setSelectedSong(null);
      router.push('/practice');
    },
    [router, selectedSong]
  );

  const renderSong = useCallback(
    ({ item }: { item: SongSearchResult }) => {
      const duration = formatDuration(item.durationMs);
      const metaLine = [item.artist, duration].filter(Boolean).join(strings.search.metaSeparator);
      const detailLine = [
        format(strings.search.blankCountFormat, { count: item.blankCount }),
        item.genre,
      ]
        .filter(Boolean)
        .join(strings.search.metaSeparator);

      return (
        <M3Card variant="filled" onPress={() => setSelectedSong(item)} style={styles.songCard}>
          <View style={styles.songRow}>
            {item.coverUrl ? (
              <Image source={{ uri: item.coverUrl }} style={styles.cover} />
            ) : (
              <View
                style={[
                  styles.cover,
                  styles.coverFallback,
                  { backgroundColor: colors.surfaceContainerHigh },
                ]}
              >
                <SymbolView
                  name={{ ios: 'music.note', android: 'music_note', web: 'music_note' }}
                  tintColor={colors.onSurfaceVariant}
                  size={24}
                />
              </View>
            )}

            <View style={styles.songMeta}>
              <Text style={[typography.titleMedium, { color: colors.onSurface }]} numberOfLines={1}>
                {item.title}
              </Text>
              <Text
                style={[
                  typography.bodySmall,
                  styles.songMetaLine,
                  { color: colors.onSurfaceVariant },
                ]}
                numberOfLines={1}
              >
                {metaLine}
              </Text>
              <Text
                style={[typography.bodySmall, { color: colors.onSurfaceVariant }]}
                numberOfLines={1}
              >
                {detailLine}
              </Text>
            </View>

            <SymbolView
              name={{ ios: 'play.fill', android: 'play_arrow', web: 'play_arrow' }}
              tintColor={colors.onSurface}
              size={22}
            />
          </View>
        </M3Card>
      );
    },
    [colors]
  );

  const listHeader = (
    <View>
      <ScreenHeader
        title={strings.app.name}
        icon={{ ios: 'music.note', android: 'music_note', web: 'music_note' }}
      />

      <View style={styles.form}>
        <M3TextField
          label={strings.search.artistLabel}
          placeholder={strings.search.artistPlaceholder}
          leading={
            <SymbolView
              name={{ ios: 'person', android: 'person', web: 'person' }}
              tintColor={colors.onSurfaceVariant}
              size={20}
            />
          }
          value={artist}
          onChangeText={setArtist}
          autoCapitalize="words"
          returnKeyType="next"
        />

        <M3TextField
          label={strings.search.trackLabel}
          placeholder={strings.search.trackPlaceholder}
          leading={
            <SymbolView
              name={{ ios: 'music.note', android: 'music_note', web: 'music_note' }}
              tintColor={colors.onSurfaceVariant}
              size={20}
            />
          }
          value={track}
          onChangeText={setTrack}
          autoCapitalize="words"
          returnKeyType="search"
          onSubmitEditing={handleSearch}
        />

        <M3Button
          label={strings.search.action}
          onPress={handleSearch}
          loading={status === 'searching'}
          fullWidth
          leading={
            <SymbolView
              name={{ ios: 'magnifyingglass', android: 'search', web: 'search' }}
              tintColor={colors.onPrimary}
              size={18}
            />
          }
        />

        {status === 'error' ? (
          <Text style={[typography.bodySmall, styles.errorText, { color: colors.error }]}>
            {errorMsg}
          </Text>
        ) : null}
      </View>

      {status !== 'idle' ? (
        <View style={styles.resultsHeader}>
          <Text style={[typography.titleMedium, { color: colors.onSurface }]}>
            {strings.search.resultTitle}
          </Text>
          {status === 'ready' ? (
            <Text style={[typography.bodySmall, { color: colors.onSurfaceVariant }]}>
              {format(strings.search.resultCountFormat, { count: results.length })}
            </Text>
          ) : null}
        </View>
      ) : null}
    </View>
  );

  const renderEmpty = () => {
    if (status === 'searching') {
      return (
        <View style={styles.centered}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text
            style={[typography.bodyMedium, styles.emptyText, { color: colors.onSurfaceVariant }]}
          >
            {strings.search.searching}
          </Text>
        </View>
      );
    }

    if (status === 'ready') {
      return (
        <Text style={[typography.bodyMedium, styles.emptyMessage, { color: colors.onSurfaceVariant }]}>
          {strings.search.emptyResult}
        </Text>
      );
    }

    if (status === 'idle') {
      return (
        <Text style={[typography.bodyMedium, styles.emptyMessage, { color: colors.onSurfaceVariant }]}>
          {strings.search.emptyHint}
        </Text>
      );
    }

    return null;
  };

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.surface }]} edges={['top']}>
      <FlatList
        data={results}
        keyExtractor={(item) => String(item.lrclibId)}
        renderItem={renderSong}
        ListHeaderComponent={listHeader}
        ListEmptyComponent={renderEmpty}
        contentContainerStyle={styles.listContent}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      />

      <M3BottomSheet
        visible={selectedSong !== null}
        onClose={() => setSelectedSong(null)}
        title={strings.practice.chooseTitle}
      >
        <View style={styles.modeList}>
          <M3Card variant="filled" onPress={() => handleSelectMode('fill-choice')}>
            <Text style={[typography.titleMedium, { color: colors.onSurface }]}>
              {strings.practice.modeFillChoice}
            </Text>
            <Text
              style={[
                typography.bodySmall,
                styles.modeDescription,
                { color: colors.onSurfaceVariant },
              ]}
            >
              {strings.practice.modeFillChoiceDesc}
            </Text>
          </M3Card>

          {/* zh-translate 需要 BYOK AI（P2），先停用 */}
          <M3Card variant="outlined">
            <Text style={[typography.titleMedium, { color: colors.onSurfaceVariant }]}>
              {strings.practice.modeZhTranslate}
            </Text>
            <Text
              style={[
                typography.bodySmall,
                styles.modeDescription,
                { color: colors.onSurfaceVariant },
              ]}
            >
              {strings.practice.needsAiSetup}
            </Text>
          </M3Card>
        </View>

        <M3Button
          label={strings.common.cancel}
          variant="text"
          fullWidth
          onPress={() => setSelectedSong(null)}
        />
      </M3BottomSheet>
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
  form: {
    gap: spacing.md,
  },
  errorText: {
    marginTop: spacing.xs,
  },
  resultsHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: spacing.xl,
    marginBottom: spacing.md,
  },
  songCard: {
    padding: spacing.md,
    marginBottom: spacing.md,
  },
  songRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  cover: {
    width: 64,
    height: 64,
    borderRadius: radius.md,
  },
  coverFallback: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  songMeta: {
    flex: 1,
    marginLeft: spacing.md,
    marginRight: spacing.sm,
  },
  songMetaLine: {
    marginTop: 2,
  },
  centered: {
    alignItems: 'center',
    marginTop: spacing.xl,
  },
  emptyText: {
    marginTop: spacing.md,
  },
  emptyMessage: {
    textAlign: 'center',
    marginTop: spacing.xl,
  },
  modeList: {
    gap: spacing.md,
    marginBottom: spacing.sm,
  },
  modeDescription: {
    marginTop: spacing.xs,
  },
});
