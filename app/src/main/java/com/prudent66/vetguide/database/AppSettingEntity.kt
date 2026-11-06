package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Generic key/value settings row (seed markers; UI settings land via Phase 4). */
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
