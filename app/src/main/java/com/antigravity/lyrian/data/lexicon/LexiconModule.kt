package com.antigravity.lyrian.data.lexicon

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 選詞的資料來源綁定。
 *
 * 目前只有離線實作。要換成 isdict 時，新增一個 `IsdictLexiconSource`
 * 並把這裡的 `@Binds` 指過去即可——選詞邏輯一行都不用改。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LexiconModule {

    @Binds
    @Singleton
    abstract fun bindLexiconSource(impl: OfflineLexiconSource): LexiconSource
}
