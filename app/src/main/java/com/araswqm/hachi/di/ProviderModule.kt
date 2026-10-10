package com.araswqm.hachi.di

import com.araswqm.hachi.core.IoDispatcher
import com.araswqm.hachi.data.ai.provider.AiProvider
import com.araswqm.hachi.data.ai.provider.AnthropicProvider
import com.araswqm.hachi.data.ai.provider.GeminiProvider
import com.araswqm.hachi.data.ai.provider.OpenAiCompatProvider
import com.araswqm.hachi.data.ai.provider.ProviderId
import com.araswqm.hachi.data.ai.provider.ProviderKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * §6: saglayici kayitlari. Hepsi `Map<ProviderId, AiProvider>` olarak birlesir;
 * [LlmRouter] zinciri bu haritadan cozer.
 *
 * DeepSeek/OpenAI/Mistral ayni tel formatini paylasir ([OpenAiCompatProvider]);
 * yalnizca DeepSeek govdesi `thinking: disabled` alanini ekler.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @IntoMap
    @ProviderKey(ProviderId.DEEPSEEK)
    @Singleton
    fun provideDeepSeek(
        httpClient: OkHttpClient,
        json: Json,
        @IoDispatcher io: CoroutineDispatcher,
    ): AiProvider = OpenAiCompatProvider(
        id = ProviderId.DEEPSEEK,
        httpClient = httpClient,
        json = json,
        io = io,
        thinkingDisabledField = true,
    )

    @Provides
    @IntoMap
    @ProviderKey(ProviderId.OPENAI)
    @Singleton
    fun provideOpenAi(
        httpClient: OkHttpClient,
        json: Json,
        @IoDispatcher io: CoroutineDispatcher,
    ): AiProvider = OpenAiCompatProvider(ProviderId.OPENAI, httpClient, json, io)

    @Provides
    @IntoMap
    @ProviderKey(ProviderId.MISTRAL)
    @Singleton
    fun provideMistral(
        httpClient: OkHttpClient,
        json: Json,
        @IoDispatcher io: CoroutineDispatcher,
    ): AiProvider = OpenAiCompatProvider(ProviderId.MISTRAL, httpClient, json, io)

    @Provides
    @IntoMap
    @ProviderKey(ProviderId.ANTHROPIC)
    @Singleton
    fun provideAnthropic(
        httpClient: OkHttpClient,
        json: Json,
        @IoDispatcher io: CoroutineDispatcher,
    ): AiProvider = AnthropicProvider(httpClient, json, io)

    @Provides
    @IntoMap
    @ProviderKey(ProviderId.GEMINI)
    @Singleton
    fun provideGemini(
        httpClient: OkHttpClient,
        json: Json,
        @IoDispatcher io: CoroutineDispatcher,
    ): AiProvider = GeminiProvider(httpClient, json, io)
}
