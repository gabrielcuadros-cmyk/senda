package com.fuerzasalvaje.senda.di

import com.fuerzasalvaje.senda.BuildConfig
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import com.fuerzasalvaje.senda.util.gemini.FirebaseGeminiService
import com.fuerzasalvaje.senda.util.gemini.GeminiConfig
import com.fuerzasalvaje.senda.util.gemini.GeminiPrompts
import com.fuerzasalvaje.senda.util.gemini.GeminiRestService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideGeminiConfig(): GeminiConfig = GeminiConfig(
        apiKey = BuildConfig.GEMINI_API_KEY,
        model = BuildConfig.GEMINI_MODEL,
        provider = BuildConfig.AI_PROVIDER,
    )

    @Provides
    @Singleton
    fun provideGeminiPrompts(json: Json): GeminiPrompts = GeminiPrompts(json)

    /** AI_PROVIDER = "firebase" uses Firebase AI Logic; anything else uses the REST API with the key. */
    @Provides
    @Singleton
    fun provideGeminiService(
        config: GeminiConfig,
        rest: Provider<GeminiRestService>,
        firebase: Provider<FirebaseGeminiService>,
    ): GeminiService = when (config.provider.lowercase()) {
        GeminiConfig.PROVIDER_FIREBASE -> firebase.get()
        else -> rest.get()
    }
}
