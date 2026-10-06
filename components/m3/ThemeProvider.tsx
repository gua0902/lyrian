/**
 * M3 Theme Provider
 *
 * 提供目前生效的 Material 3 色彩 token 給整個 App。
 * 依 docs/ARCHITECTURE.md §10，Phase 1 先使用 baseline palette；
 * Dynamic Color（Material You）列為 Phase 5 選配。
 */
import { createContext, useContext, useMemo, type ReactNode } from 'react';

import { useColorScheme } from '@/components/useColorScheme';
import { darkColors, lightColors, type ColorSchemeName, type M3ColorTokens } from '@/constants/theme';

export interface M3Theme {
  /** 目前生效的配色 */
  scheme: ColorSchemeName;
  colors: M3ColorTokens;
}

const ThemeContext = createContext<M3Theme>({
  scheme: 'light',
  colors: lightColors,
});

export function M3ThemeProvider({
  children,
  forceScheme,
}: {
  children: ReactNode;
  /** 強制指定配色（測試或預覽用）；不傳則跟隨系統。 */
  forceScheme?: ColorSchemeName;
}) {
  const systemScheme = useColorScheme();

  const scheme: ColorSchemeName = forceScheme ?? (systemScheme === 'dark' ? 'dark' : 'light');

  const value = useMemo<M3Theme>(
    () => ({
      scheme,
      colors: scheme === 'dark' ? darkColors : lightColors,
    }),
    [scheme]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/** 取得目前生效的 M3 主題。 */
export function useTheme(): M3Theme {
  return useContext(ThemeContext);
}
