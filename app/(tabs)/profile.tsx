import { PlaceholderScreen } from '@/components/PlaceholderScreen';
import { strings } from '@/constants/strings';

/**
 * Tab 4：我的
 *
 * P4 將實作：累積總分、已學會單字、連續學習天數（週曆）。
 * P2 的 BYOK 設定頁（settings/ai.tsx）也會從這裡進入。
 */
export default function ProfileTabScreen() {
  return (
    <PlaceholderScreen
      title={strings.tabs.profile}
      icon={{ ios: 'person', android: 'person', web: 'person' }}
      description="這裡會是你的學習總覽：累積總分、已學會的單字、連續學習天數，以及設定入口（含 BYOK AI 金鑰）。"
    />
  );
}
