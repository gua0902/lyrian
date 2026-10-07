/**
 * M3 Linear Progress Indicator。
 *
 * 依 `UI references/Lyrian · 歌詞練習.png` 的細長進度條實作。
 */
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { radius } from '@/constants/theme';
import { useTheme } from './ThemeProvider';

export interface M3ProgressBarProps {
  /** 0..1；超出範圍會被夾在區間內 */
  value: number;
  height?: number;
  style?: StyleProp<ViewStyle>;
}

export function M3ProgressBar({ value, height = 4, style }: M3ProgressBarProps) {
  const { colors } = useTheme();

  const clamped = Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : 0;

  return (
    <View
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: Math.round(clamped * 100) }}
      style={[
        styles.track,
        { height, backgroundColor: colors.surfaceVariant },
        style,
      ]}
    >
      <View
        style={[
          styles.indicator,
          { width: `${clamped * 100}%`, backgroundColor: colors.primary },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    width: '100%',
    borderRadius: radius.full,
    overflow: 'hidden',
  },
  indicator: {
    height: '100%',
    borderRadius: radius.full,
  },
});
