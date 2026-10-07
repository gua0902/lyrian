/**
 * M3 Modal Bottom Sheet。
 *
 * 依 UI references 與 M3 慣例：用於選擇器與說明，不用於阻斷式決策（那用 dialog）。
 * 點擊遮罩或返回鍵可關閉。
 */
import type { ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { radius, spacing, typography } from '@/constants/theme';
import { useTheme } from './ThemeProvider';

export interface M3BottomSheetProps {
  visible: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
}

export function M3BottomSheet({ visible, onClose, title, children }: M3BottomSheetProps) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.root}>
        <Pressable
          style={styles.scrim}
          onPress={onClose}
          accessibilityRole="button"
          accessibilityLabel="關閉"
        />

        <View
          style={[
            styles.sheet,
            {
              backgroundColor: colors.surfaceContainerLow,
              paddingBottom: insets.bottom + spacing.lg,
            },
          ]}
        >
          {/* M3 的拖曳指示條 */}
          <View style={[styles.handle, { backgroundColor: colors.outlineVariant }]} />

          {title ? (
            <Text style={[typography.titleLarge, styles.title, { color: colors.onSurface }]}>
              {title}
            </Text>
          ) : null}

          {children}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  scrim: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.32)',
  },
  sheet: {
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
  },
  handle: {
    alignSelf: 'center',
    width: 32,
    height: 4,
    borderRadius: radius.full,
    marginBottom: spacing.lg,
  },
  title: {
    marginBottom: spacing.lg,
  },
});
