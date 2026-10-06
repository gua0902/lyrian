import { PlaceholderScreen } from '@/components/PlaceholderScreen';
import { strings } from '@/constants/strings';

/**
 * Tab 3：字庫
 *
 * P3 將實作：單字收集（依 ECDICT 的 lemma 去重）、FSRS 排程，
 * 以及學測式複習題型（詞彙題／文意選填／搭配詞／詞形）。
 */
export default function VocabTabScreen() {
  return (
    <PlaceholderScreen
      title={strings.vocab.title}
      icon={{ ios: 'bookmark', android: 'bookmark', web: 'bookmark' }}
      description="這裡會是你的字庫：每個答錯的單字連同歌詞原句一起收進來，並依 FSRS 排程用學測式題型複習。"
    />
  );
}
