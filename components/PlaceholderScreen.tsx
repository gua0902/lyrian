/**
 * 佔位畫面
 *
 * P0 階段四個 Tab 皆為骨架，用本元件呈現一致的「尚未實作」外觀。
 * 每個階段完成時會逐一被真正的畫面取代。
 */
import { SymbolView } from 'expo-symbols';
import type { ReactNode } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { spacing, typography } from '@/constants/theme';
import { strings } from '@/constants/strings';
import { M3Button, M3Card, useTheme } from './m3';
import { ScreenHeader } from './ScreenHeader';

export interface PlaceholderScreenProps {
  title: string;
  icon?: React.ComponentProps<typeof SymbolView>['name'];
  /** 這個頁面未來會做什麼（一句話） */
  description: string;
  /** 額外內容（例如過渡期的入口按鈕） */
  children?: ReactNode;
}

export function PlaceholderScreen({
  title,
  icon,
  description,
  children,
}: PlaceholderScreenProps) {
  const { colors } = useTheme();

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.surface }]} edges={['top']}>
      <ScreenHeader title={title} icon={icon} />
      <ScrollView
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <M3Card variant="filled">
          <Text style={[typography.titleMedium, { color: colors.onSurface }]}>
            {strings.common.comingSoon}
          </Text>
          <Text
            style={[
              typography.bodyMedium,
              styles.description,
              { color: colors.onSurfaceVariant },
            ]}
          >
            {description}
          </Text>
        </M3Card>

        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

/** 過渡期入口：連到既有（尚未 M3 化）的畫面。 */
export function TransitionAction({
  label,
  onPress,
}: {
  label: string;
  onPress: () => void;
}) {
  const { colors } = useTheme();
  return (
    <View style={styles.transition}>
      <Text style={[typography.bodySmall, { color: colors.onSurfaceVariant }]}>
        {strings.common.comingSoon}
      </Text>
      <M3Button label={label} variant="outlined" onPress={onPress} fullWidth style={styles.transitionButton} />
    </View>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
  },
  content: {
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.xxl,
    gap: spacing.lg,
  },
  description: {
    marginTop: spacing.sm,
  },
  transition: {
    gap: spacing.sm,
  },
  transitionButton: {
    marginTop: spacing.xs,
  },
});
