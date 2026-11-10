package com.prudent66.vetguide.di

import com.prudent66.vetguide.domain.ai.AiOrchestrator
import com.prudent66.vetguide.domain.repository.SettingsKeys
import com.prudent66.vetguide.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * AI-layer DI (roadmap Phase 4). The orchestrator is production-wired with its
 * Settings gate from Phase 4; the provider list stays empty until Phase 5
 * delivers real providers behind the AiProvider contract — FakeAiProvider
 * never enters production DI. The Settings "Cloud AI Analysis" toggle (§5)
 * gates the whole failover ladder: off = deterministic offline results only.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiOrchestrator(
        settingsRepository: SettingsRepository,
    ): AiOrchestrator = AiOrchestrator(
        providers = emptyList(),
        cloudAiEnabled = {
            settingsRepository
                .observeBoolean(SettingsKeys.CLOUD_AI_ENABLED, default = false)
                .first()
        },
    )
}
