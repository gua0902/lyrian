import { Link, Stack } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { M3Button, useTheme } from '@/components/m3';
import { strings } from '@/constants/strings';
import { spacing, typography } from '@/constants/theme';

export default function NotFoundScreen() {
  const { colors } = useTheme();

  return (
    <>
      <Stack.Screen options={{ title: strings.common.notFoundTitle }} />
      <View style={[styles.container, { backgroundColor: colors.surface }]}>
        <Text style={[typography.headlineSmall, { color: colors.onSurface }]}>
          {strings.common.notFoundTitle}
        </Text>
        <Link href="/" asChild>
          <View style={styles.action}>
            <M3Button label={strings.common.notFoundBack} variant="tonal" />
          </View>
        </Link>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    gap: spacing.lg,
  },
  action: {
    marginTop: spacing.sm,
  },
});
