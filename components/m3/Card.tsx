/**
 * M3 Card
 *
 * 三種變體：elevated（陰影）、filled（填色）、outlined（外框）。
 * 依 UI references 實測，卡片圓角為 16。
 */
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { elevation, radius, spacing } from '@/constants/theme';
import { useTheme } from './ThemeProvider';

export type M3CardVariant = 'elevated' | 'filled' | 'outlined';

export interface M3CardProps {
  children: ReactNode;
  variant?: M3CardVariant;
  onPress?: () => void;
  /** 覆寫底色（例如歌詞高亮卡要用 warningContainer） */
  backgroundColor?: string;
  borderColor?: string;
  style?: StyleProp<ViewStyle>;
}

const PADDING = spacing.lg;

export function M3Card({
  children,
  variant = 'filled',
  onPress,
  backgroundColor,
  borderColor,
  style,
}: M3CardProps) {
  const { colors } = useTheme();

  const palette = {
    elevated: { bg: colors.surfaceContainerLow, border: 'transparent', lift: elevation.level1 },
    filled: { bg: colors.surfaceContainerHigh, border: 'transparent', lift: elevation.level0 },
    outlined: { bg: 'transparent', border: colors.outlineVariant, lift: elevation.level0 },
  }[variant];

  const cardStyle: StyleProp<ViewStyle> = [
    styles.card,
    palette.lift,
    {
      backgroundColor: backgroundColor ?? palette.bg,
      borderColor: borderColor ?? palette.border,
      borderWidth: variant === 'outlined' || borderColor ? 1 : 0,
    },
    style,
  ];

  if (!onPress) {
    return <View style={cardStyle}>{children}</View>;
  }

  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [cardStyle, { opacity: pressed ? 0.85 : 1 }]}
    >
      {children}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.lg,
    padding: PADDING,
  },
});
