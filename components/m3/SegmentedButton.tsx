/**
 * M3 Segmented Button
 *
 * 依 UI references 的字庫頁實測：一排膠囊狀選項，選中者填色。
 */
import { Pressable, StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';

import { radius, spacing, typography } from '@/constants/theme';
import { useTheme } from './ThemeProvider';

export interface M3SegmentedOption<T extends string> {
  value: T;
  label: string;
}

export interface M3SegmentedButtonProps<T extends string> {
  options: readonly M3SegmentedOption<T>[];
  value: T;
  onChange: (value: T) => void;
  style?: StyleProp<ViewStyle>;
}

export function M3SegmentedButton<T extends string>({
  options,
  value,
  onChange,
  style,
}: M3SegmentedButtonProps<T>) {
  const { colors } = useTheme();

  return (
    <View style={[styles.row, style]}>
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="button"
            accessibilityState={{ selected }}
            onPress={() => onChange(option.value)}
            style={({ pressed }) => [
              styles.item,
              {
                backgroundColor: selected ? colors.secondaryContainer : 'transparent',
                borderColor: colors.outlineVariant,
                borderWidth: selected ? 0 : 1,
                opacity: pressed ? 0.8 : 1,
              },
            ]}
          >
            <Text
              style={[
                typography.labelLarge,
                { color: selected ? colors.onSecondaryContainer : colors.onSurfaceVariant },
              ]}
            >
              {option.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  item: {
    borderRadius: radius.full,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.lg,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
