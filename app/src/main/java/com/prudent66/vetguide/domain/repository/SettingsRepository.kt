package com.prudent66.vetguide.domain.repository

import kotlinx.coroutines.flow.Flow

/** Keys for persisted app settings (app_settings table). */
object SettingsKeys {

    /**
     * Legacy key for the removed "Cloud AI Analysis" opt-in toggle. Existing
     * installs may still have a row under this key; the app no longer reads
     * or writes it because cloud analysis is always attempted when online.
     * Kept so the settings table and its schema stay stable.
     */
    const val CLOUD_AI_ENABLED = "cloud_ai_enabled"
}

/** Key/value application settings persisted in Room (never plain-text secrets). */
interface SettingsRepository {

    fun observeBoolean(key: String, default: Boolean): Flow<Boolean>

    suspend fun putBoolean(key: String, value: Boolean)
}
