package com.prudent66.vetguide.di

import com.prudent66.vetguide.data.ai.CloudAiProvider
import com.prudent66.vetguide.domain.ai.AiCatalog
import com.prudent66.vetguide.domain.ai.AiOrchestrator
import com.prudent66.vetguide.domain.ai.OnDeviceAiProvider
import com.prudent66.vetguide.domain.repository.SettingsKeys
import com.prudent66.vetguide.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * AI-layer DI (roadmap Phase 5). Real providers now sit behind the §5.1
 * ladder — cloud tier (mode 1, routed through the secure Workers backend,
 * §8) then the on-device tier (mode 3, stubbed until the TFLite model lands).
 * When every provider is exhausted, the orchestrator answers with the mode-4
 * deterministic fallback. FakeAiProvider never enters production DI — it is
 * for fault-injection tests only (§7). The Settings "Cloud AI Analysis"
 * toggle gates the whole ladder: off = deterministic offline results only.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = AiCatalog.BACKEND_TIMEOUT_MS
        }
    }

    @Provides
    @Singleton
    fun provideCloudAiProvider(client: HttpClient): CloudAiProvider =
        CloudAiProvider(client = client)

    @Provides
    @Singleton
    fun provideOnDeviceAiProvider(): OnDeviceAiProvider = OnDeviceAiProvider()

    @Provides
    @Singleton
    fun provideAiOrchestrator(
        cloudProvider: CloudAiProvider,
        onDeviceProvider: OnDeviceAiProvider,
        settingsRepository: SettingsRepository,
    ): AiOrchestrator = AiOrchestrator(
        providers = listOf(cloudProvider, onDeviceProvider),
        cloudAiEnabled = {
            settingsRepository
                .observeBoolean(SettingsKeys.CLOUD_AI_ENABLED, default = false)
                .first()
        },
    )
}
