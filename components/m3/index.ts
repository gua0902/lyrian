/**
 * Lyrian — Material 3 元件庫
 *
 * 依 docs/DESIGN.md 第 1 點：App 內所有 UI 一律使用這裡的元件，不得自創。
 * 畫面中也不得硬寫顏色值，顏色一律取自 `useTheme()`。
 */
export { M3ThemeProvider, useTheme, type M3Theme } from './ThemeProvider';
export { M3Button, type M3ButtonProps, type M3ButtonVariant, type M3ButtonSize } from './Button';
export { M3TextField, type M3TextFieldProps } from './TextField';
export { M3Card, type M3CardProps, type M3CardVariant } from './Card';
export {
  M3SegmentedButton,
  type M3SegmentedButtonProps,
  type M3SegmentedOption,
} from './SegmentedButton';
