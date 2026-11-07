package com.prudent66.vetguide.domain.repository

import kotlinx.coroutines.flow.Flow

/** Keys for persisted app settings (app_settings table). */
object SettingsKeys {

    /** Roadmap §5: cloud AI analysis is opt-in; off = deterministic offline only. */
    const val CLOUD_AI_ENABLED = "cloud_ai_enabled"
}

/** Key/value application settings persisted in Room (never plain-text secrets). */
interface SettingsRepository {

    fun observeBoolean(key: String, default: Boolean): Flow<Boolean>

    suspend fun putBoolean(key: String, value: Boolean)
}
