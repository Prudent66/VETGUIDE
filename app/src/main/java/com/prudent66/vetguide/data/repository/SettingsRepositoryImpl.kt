package com.prudent66.vetguide.data.repository

import com.prudent66.vetguide.database.AppSettingsDao
import com.prudent66.vetguide.database.AppSettingEntity
import com.prudent66.vetguide.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed settings storage backed by the dedicated app-settings dao. */
class SettingsRepositoryImpl @Inject constructor(
    private val dao: AppSettingsDao,
) : SettingsRepository {

    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> =
        dao.observeSetting(key).map { entity ->
            entity?.value?.toBooleanStrictOrNull() ?: default
        }

    override suspend fun putBoolean(key: String, value: Boolean) {
        dao.upsertSetting(AppSettingEntity(key = key, value = value.toString()))
    }
}
