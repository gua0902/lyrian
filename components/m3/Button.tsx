/**
 * M3 Button
 *
 * 依 docs/DESIGN.md 第 1 點「不准自創按鈕」——本元件是 App 內唯一允許的按鈕來源。
 * M3 按鈕為**全圓角藥丸**形狀（radius.full）。
 */
import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

import { radius, spacing, typography } from '@/constants/theme';
import { useTheme } from './ThemeProvider';

export type M3ButtonVariant = 'filled' | 'tonal' | 'outlined' | 'text' | 'elevated';
export type M3ButtonSize = 'sm' | 'md' | 'lg';

export interface M3ButtonProps {
  label: string;
  onPress?: () => void;
  variant?: M3ButtonVariant;
  size?: M3ButtonSize;
  disabled?: boolean;
  loading?: boolean;
  /** 撐滿容器寬度（常用於搜尋鈕） */
  fullWidth?: boolean;
  leading?: ReactNode;
  style?: StyleProp<ViewStyle>;
}

const HEIGHT: Record<M3ButtonSize, number> = { sm: 32, md: 40, lg: 48 };

export function M3Button({
  label,
  onPress,
  variant = 'filled',
  size = 'md',
  disabled = false,
  loading = false,
  fullWidth = false,
  leading,
  style,
}: M3ButtonProps) {
  const { colors } = useTheme();
  const inactive = disabled || loading;

  const palette = {
    filled: { bg: colors.primary, fg: colors.onPrimary, border: 'transparent' },
    tonal: { bg: colors.secondaryContainer, fg: colors.onSecondaryContainer, border: 'transparent' },
    outlined: { bg: 'transparent', fg: colors.primary, border: colors.outline },
    text: { bg: 'transparent', fg: colors.primary, border: 'transparent' },
    elevated: { bg: colors.surfaceContainerLow, fg: colors.primary, border: 'transparent' },
  }[variant];

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: inactive, busy: loading }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        {
          height: HEIGHT[size],
          backgroundColor: palette.bg,
          borderColor: palette.border,
          borderWidth: variant === 'outlined' ? 1 : 0,
          opacity: inactive ? 0.38 : pressed ? 0.82 : 1,
          alignSelf: fullWidth ? 'stretch' : 'flex-start',
        },
        fullWidth && styles.fullWidth,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator size="small" color={palette.fg} />
      ) : (
        <View style={styles.content}>
          {leading ? <View style={styles.leading}>{leading}</View> : null}
          <Text
            style={[typography.labelLarge, { color: palette.fg }]}
            numberOfLines={1}
            allowFontScaling
          >
            {label}
          </Text>
        </View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: radius.full,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: spacing.xl,
  },
  fullWidth: {
    width: '100%',
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  leading: {
    marginRight: spacing.sm,
  },
});
