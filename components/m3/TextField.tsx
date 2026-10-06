/**
 * M3 Text Field（filled 變體）
 *
 * 依 UI references 的搜尋頁實測：填色圓角欄位、左側可放圖示。
 */
import type { ReactNode } from 'react';
import {
  StyleSheet,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextInputProps,
  type ViewStyle,
} from 'react-native';

import { radius, spacing, typography } from '@/constants/theme';
import { useTheme } from './ThemeProvider';

export interface M3TextFieldProps extends Omit<TextInputProps, 'style'> {
  /** 欄位上方標籤（可省略） */
  label?: string;
  /** 欄位左側圖示（例如 SymbolView） */
  leading?: ReactNode;
  /** 錯誤訊息（顯示時邊框轉為 error 色） */
  errorText?: string;
  containerStyle?: StyleProp<ViewStyle>;
}

export function M3TextField({
  label,
  leading,
  errorText,
  containerStyle,
  placeholderTextColor,
  ...inputProps
}: M3TextFieldProps) {
  const { colors } = useTheme();
  const hasError = Boolean(errorText);

  return (
    <View style={containerStyle}>
      {label ? (
        <Text style={[typography.labelMedium, styles.label, { color: colors.onSurfaceVariant }]}>
          {label}
        </Text>
      ) : null}

      <View
        style={[
          styles.field,
          {
            backgroundColor: colors.surfaceContainerHigh,
            borderColor: hasError ? colors.error : 'transparent',
            borderWidth: hasError ? 1 : 0,
          },
        ]}
      >
        {leading ? <View style={styles.leading}>{leading}</View> : null}
        <TextInput
          {...inputProps}
          style={[typography.bodyLarge, styles.input, { color: colors.onSurface }]}
          placeholderTextColor={placeholderTextColor ?? colors.onSurfaceVariant}
        />
      </View>

      {errorText ? (
        <Text style={[typography.bodySmall, styles.error, { color: colors.error }]}>
          {errorText}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  label: {
    marginBottom: spacing.xs,
    marginLeft: spacing.xs,
  },
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 52,
    borderRadius: radius.lg,
    paddingHorizontal: spacing.lg,
  },
  leading: {
    marginRight: spacing.md,
  },
  input: {
    flex: 1,
    paddingVertical: spacing.md,
  },
  error: {
    marginTop: spacing.xs,
    marginLeft: spacing.xs,
  },
});
