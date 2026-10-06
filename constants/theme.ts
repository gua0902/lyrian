/**
 * Lyrian — Material 3 Design Tokens
 *
 * 本檔案是整個 App 的**唯一設計來源**。畫面中不得再散落寫死的顏色值。
 * 依據：docs/ARCHITECTURE.md §10、docs/DESIGN.md
 *
 * 注意：React Native 沒有原生 Material 3 元件，因此我們以 token 自建
 * `components/m3/` 元件庫來實作 M3 視覺（見 ARCHITECTURE.md §2 裁決表）。
 */

export type ColorSchemeName = 'light' | 'dark';

export interface M3ColorTokens {
  // ---- Primary ----
  primary: string;
  onPrimary: string;
  primaryContainer: string;
  onPrimaryContainer: string;

  // ---- Secondary ----
  secondary: string;
  onSecondary: string;
  secondaryContainer: string;
  onSecondaryContainer: string;

  // ---- Tertiary ----
  tertiary: string;
  tertiaryContainer: string;
  onTertiaryContainer: string;

  // ---- Error ----
  error: string;
  onError: string;
  errorContainer: string;
  onErrorContainer: string;

  // ---- Success（M3 無此角色，Lyrian 自訂：答對）----
  success: string;
  onSuccess: string;
  successContainer: string;
  onSuccessContainer: string;

  // ---- Warning（Lyrian 自訂：歌詞高亮卡）----
  warningContainer: string;
  onWarningContainer: string;

  // ---- Surface ----
  surface: string;
  onSurface: string;
  surfaceVariant: string;
  onSurfaceVariant: string;
  surfaceContainerLow: string;
  surfaceContainer: string;
  surfaceContainerHigh: string;

  // ---- Outline ----
  outline: string;
  outlineVariant: string;

  // ---- 歌詞練習專用（無法用 M3 角色表達）----
  /** 正在播放的黃色高亮卡底色 */
  lyricHighlight: string;
  onLyricHighlight: string;
  /** 尚未唱到的歌詞（灰） */
  lyricUpcoming: string;
  /** 當前正在唱的句子 */
  lyricCurrent: string;
  /** 空格佔位符 */
  blankPending: string;
}

/**
 * M3 baseline palette（Light）。
 * 其中 success / warning / lyric* 為 Lyrian 自訂，其餘取自 M3 baseline。
 * 歌詞高亮黃取自 UI references 的實際畫面。
 */
export const lightColors: M3ColorTokens = {
  primary: '#6750A4',
  onPrimary: '#FFFFFF',
  primaryContainer: '#EADDFF',
  onPrimaryContainer: '#21005D',

  secondary: '#625B71',
  onSecondary: '#FFFFFF',
  secondaryContainer: '#E8DEF8',
  onSecondaryContainer: '#1D192B',

  tertiary: '#7D5260',
  tertiaryContainer: '#FFD8E4',
  onTertiaryContainer: '#31111D',

  error: '#B3261E',
  onError: '#FFFFFF',
  errorContainer: '#F9DEDC',
  onErrorContainer: '#410E0B',

  success: '#1B6B3A',
  onSuccess: '#FFFFFF',
  successContainer: '#B7F0C4',
  onSuccessContainer: '#00210B',

  warningContainer: '#FBF0A0',
  onWarningContainer: '#2B2200',

  surface: '#FEF7FF',
  onSurface: '#1D1B20',
  surfaceVariant: '#E7E0EC',
  onSurfaceVariant: '#49454F',
  surfaceContainerLow: '#F7F2FA',
  surfaceContainer: '#F3EDF7',
  surfaceContainerHigh: '#ECE6F0',

  outline: '#79747E',
  outlineVariant: '#CAC4D0',

  lyricHighlight: '#FBF0A0',
  onLyricHighlight: '#2B2200',
  lyricUpcoming: '#79747E',
  lyricCurrent: '#1D1B20',
  blankPending: '#79747E',
};

/** M3 baseline palette（Dark）。 */
export const darkColors: M3ColorTokens = {
  primary: '#D0BCFF',
  onPrimary: '#381E72',
  primaryContainer: '#4F378B',
  onPrimaryContainer: '#EADDFF',

  secondary: '#CCC2DC',
  onSecondary: '#332D41',
  secondaryContainer: '#4A4458',
  onSecondaryContainer: '#E8DEF8',

  tertiary: '#EFB8C8',
  tertiaryContainer: '#633B48',
  onTertiaryContainer: '#FFD8E4',

  error: '#F2B8B5',
  onError: '#601410',
  errorContainer: '#8C1D18',
  onErrorContainer: '#F9DEDC',

  success: '#7BD69B',
  onSuccess: '#00391A',
  successContainer: '#10502A',
  onSuccessContainer: '#B7F0C4',

  warningContainer: '#4A4200',
  onWarningContainer: '#FBF0A0',

  surface: '#141218',
  onSurface: '#E6E0E9',
  surfaceVariant: '#49454F',
  onSurfaceVariant: '#CAC4D0',
  surfaceContainerLow: '#1D1B20',
  surfaceContainer: '#211F26',
  surfaceContainerHigh: '#2B2930',

  outline: '#938F99',
  outlineVariant: '#49454F',

  lyricHighlight: '#4A4200',
  onLyricHighlight: '#FBF0A0',
  lyricUpcoming: '#938F99',
  lyricCurrent: '#E6E0E9',
  blankPending: '#938F99',
};

/**
 * 間距：8dp 節奏（docs/DESIGN.md 第 3 點：4, 8, 12, 16, 24）
 */
export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

/**
 * 圓角：卡片 16／28，藥丸 full（依 UI references 實測）
 */
export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 28,
  full: 999,
} as const;

/**
 * 字級：M3 type scale。
 * docs/DESIGN.md 第 4 點指定 TitleMedium 用於歌名、BodyMedium 用於歌詞、LabelMedium 用於圖塊；
 * 第 5 點指定歌詞原文字 18sp、行高 28sp。
 */
export const typography = {
  displaySmall: { fontSize: 36, lineHeight: 44, fontWeight: '400' },
  headlineMedium: { fontSize: 28, lineHeight: 36, fontWeight: '400' },
  headlineSmall: { fontSize: 24, lineHeight: 32, fontWeight: '400' },
  titleLarge: { fontSize: 22, lineHeight: 28, fontWeight: '400' },
  titleMedium: { fontSize: 16, lineHeight: 24, fontWeight: '500' },
  titleSmall: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
  bodyLarge: { fontSize: 16, lineHeight: 24, fontWeight: '400' },
  bodyMedium: { fontSize: 14, lineHeight: 20, fontWeight: '400' },
  bodySmall: { fontSize: 12, lineHeight: 16, fontWeight: '400' },
  labelLarge: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
  labelMedium: { fontSize: 12, lineHeight: 16, fontWeight: '500' },
  labelSmall: { fontSize: 11, lineHeight: 16, fontWeight: '500' },
  /** 歌詞行（DESIGN.md：18sp / 28sp） */
  lyric: { fontSize: 18, lineHeight: 28, fontWeight: '400' },
  /** 歌詞行——當前句加重 */
  lyricEmphasis: { fontSize: 18, lineHeight: 28, fontWeight: '700' },
  /** 歌詞行——尚未唱到 */
  lyricMuted: { fontSize: 18, lineHeight: 28, fontWeight: '400' },
} as const;

/**
 * M3 elevation（以 RN 的 shadow + android elevation 近似）。
 */
export const elevation = {
  level0: {},
  level1: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.15,
    shadowRadius: 2,
    elevation: 1,
  },
  level2: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.16,
    shadowRadius: 4,
    elevation: 3,
  },
  level3: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.18,
    shadowRadius: 8,
    elevation: 6,
  },
} as const;

/** 動畫時長（M3 motion tokens，取常用值） */
export const motion = {
  short: 150,
  medium: 250,
  long: 400,
} as const;
