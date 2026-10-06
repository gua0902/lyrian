import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';

import { M3ThemeProvider, useTheme } from '@/components/m3';
import { strings } from '@/constants/strings';

export {
  // Catch any errors thrown by the Layout component.
  ErrorBoundary,
} from 'expo-router';

export const unstable_settings = {
  initialRouteName: '(tabs)',
};

export default function RootLayout() {
  return (
    <M3ThemeProvider>
      <RootLayoutNav />
    </M3ThemeProvider>
  );
}

function RootLayoutNav() {
  const { colors, scheme } = useTheme();

  // 把 M3 token 餵給 React Navigation，確保導覽容器底色與 header 與 M3 一致。
  const base = scheme === 'dark' ? DarkTheme : DefaultTheme;
  const navigationTheme = {
    ...base,
    colors: {
      ...base.colors,
      primary: colors.primary,
      background: colors.surface,
      card: colors.surface,
      text: colors.onSurface,
      border: colors.outlineVariant,
      notification: colors.error,
    },
  };

  return (
    <ThemeProvider value={navigationTheme}>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.surface },
          headerTintColor: colors.onSurface,
          headerShadowVisible: false,
          contentStyle: { backgroundColor: colors.surface },
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        {/* 過渡期：既有的搜尋畫面，P1 會把它的邏輯抽出並 M3 化後移除本路由。 */}
        <Stack.Screen name="search" options={{ title: strings.search.action }} />
      </Stack>
    </ThemeProvider>
  );
}
