import { SymbolView } from 'expo-symbols';
import { Tabs } from 'expo-router';

import { useTheme } from '@/components/m3';
import { strings } from '@/constants/strings';
import { typography } from '@/constants/theme';

/**
 * 四個 Tab：搜尋 / 練習 / 字庫 / 我的
 *
 * 依 docs/ARCHITECTURE.md §11。headerShown 為 false，各頁自繪標題
 * （與 UI references 的大字標題一致）。
 */
export default function TabLayout() {
  const { colors } = useTheme();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.onSurfaceVariant,
        tabBarStyle: {
          backgroundColor: colors.surfaceContainer,
          borderTopColor: colors.outlineVariant,
        },
        tabBarLabelStyle: typography.labelMedium,
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: strings.tabs.search,
          tabBarIcon: ({ color }) => (
            <SymbolView
              name={{ ios: 'magnifyingglass', android: 'search', web: 'search' }}
              tintColor={color}
              size={26}
            />
          ),
        }}
      />
      <Tabs.Screen
        name="practice"
        options={{
          title: strings.tabs.practice,
          tabBarIcon: ({ color }) => (
            <SymbolView
              name={{ ios: 'play.circle', android: 'play_circle', web: 'play_circle' }}
              tintColor={color}
              size={26}
            />
          ),
        }}
      />
      <Tabs.Screen
        name="vocab"
        options={{
          title: strings.tabs.vocab,
          tabBarIcon: ({ color }) => (
            <SymbolView
              name={{ ios: 'bookmark', android: 'bookmark', web: 'bookmark' }}
              tintColor={color}
              size={26}
            />
          ),
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: strings.tabs.profile,
          tabBarIcon: ({ color }) => (
            <SymbolView
              name={{ ios: 'person', android: 'person', web: 'person' }}
              tintColor={color}
              size={26}
            />
          ),
        }}
      />
    </Tabs>
  );
}
