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

    /** Observes a plain string setting (empty when unset). */
    fun observeString(key: String, default: String = ""): Flow<String>

    /** Reads a plain string setting once (empty when unset). */
    suspend fun getString(key: String, default: String = ""): String

    /** Stores a plain string setting; never used for credentials. */
    suspend fun putString(key: String, value: String)
}
