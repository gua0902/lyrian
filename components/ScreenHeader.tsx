/**
 * 頁面標題列
 *
 * 依 UI references 實測：每頁頂部為「圖示 + 大字標題」，並可放右側動作。
 * 因此四個 Tab 的 headerShown 皆為 false，由本元件自繪標題。
 */
import { SymbolView } from 'expo-symbols';
import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { spacing, typography } from '@/constants/theme';
import { useTheme } from './m3';

export interface ScreenHeaderProps {
  title: string;
  /** expo-symbols 的圖示名稱物件（{ ios, android, web }） */
  icon?: React.ComponentProps<typeof SymbolView>['name'];
  /** 右側內容（例如統計數字） */
  right?: ReactNode;
}

export function ScreenHeader({ title, icon, right }: ScreenHeaderProps) {
  const { colors } = useTheme();

  return (
    <View style={styles.row}>
      {icon ? (
        <SymbolView name={icon} tintColor={colors.onSurface} size={26} style={styles.icon} />
      ) : null}
      <Text style={[typography.headlineSmall, styles.title, { color: colors.onSurface }]}>
        {title}
      </Text>
      {right ? <View style={styles.right}>{right}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
    paddingBottom: spacing.lg,
  },
  icon: {
    marginRight: spacing.md,
  },
  title: {
    flex: 1,
  },
  right: {
    marginLeft: spacing.md,
  },
});
