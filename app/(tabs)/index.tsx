import { router } from 'expo-router';

import { PlaceholderScreen, TransitionAction } from '@/components/PlaceholderScreen';
import { strings } from '@/constants/strings';

/**
 * Tab 1：搜尋
 *
 * P1 將實作：LRCLIB 真實搜尋 + iTunes 封面/曲風 + 練習模式 Bottom Sheet。
 * 目前過渡期仍可進入既有的 /search 畫面（P1 會把它的邏輯抽出並 M3 化）。
 */
export default function SearchTabScreen() {
  return (
    <PlaceholderScreen
      title={strings.app.name}
      icon={{ ios: 'music.note', android: 'music_note', web: 'music_note' }}
      description="這裡會是歌詞搜尋：輸入歌手與歌名，從 LRCLIB 取得歌詞，並在點歌後選擇練習方式。"
    >
      <TransitionAction
        label={strings.search.action}
        onPress={() => router.push('/search')}
      />
    </PlaceholderScreen>
  );
}
