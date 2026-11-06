package com.prudent66.vetguide.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * VETGUIDE local database (roadmap §11: offline-first, migrations only).
 * Every schema change bumps [VETGUIDE_DB_VERSION] and adds a Migration —
 * never fallbackToDestructiveMigration. Schemas are exported to app/schemas
 * and committed.
 */
@Database(
    entities = [PatientEntity::class, CaseEntity::class, CaseArtifactEntity::class],
    version = VETGUIDE_DB_VERSION,
    exportSchema = true,
)
abstract class VetguideDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun caseDao(): CaseDao
    abstract fun caseArtifactDao(): CaseArtifactDao

    companion object {
        const val VETGUIDE_DB_VERSION = 1
        const val VETGUIDE_DB_NAME = "vetguide.db"
    }
}
