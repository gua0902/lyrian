import { PlaceholderScreen } from '@/components/PlaceholderScreen';
import { strings } from '@/constants/strings';

/**
 * Tab 2：練習
 *
 * P1 將實作：四選一填空（接 LRCLIB 的 lyricsfile，start_ms + end_ms）。
 * P2 將實作：英翻中翻譯（需 BYOK AI）與手勢（長按查字／長按滑動反白）。
 */
export default function PracticeTabScreen() {
  return (
    <PlaceholderScreen
      title={strings.tabs.practice}
      icon={{ ios: 'play.circle', android: 'play_circle', web: 'play_circle' }}
      description="這裡會是歌詞練習：黃色高亮當前句、四個選項膠囊、作答進度。支援四選一填空與英翻中兩種模式。"
    />
  );
}
