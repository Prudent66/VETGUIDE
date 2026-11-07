package com.prudent66.vetguide.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Generic key/value settings storage (seed markers + Phase 4 UI settings). */
@Dao
interface AppSettingsDao {

    @Upsert
    suspend fun upsertSetting(setting: AppSettingEntity)

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    fun observeSetting(key: String): Flow<AppSettingEntity?>
}
