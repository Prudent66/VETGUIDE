package com.prudent66.vetguide.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * VETGUIDE local database (roadmap §11: offline-first, migrations only).
 * Every schema change bumps the @Database version literal and adds a
 * Migration — never fallbackToDestructiveMigration. Schemas are exported to
 * app/schemas and committed.
 */
@Database(
    entities = [
        PatientEntity::class,
        CaseEntity::class,
        CaseArtifactEntity::class,
        ConditionEntity::class,
        SymptomEntity::class,
        ReferenceEntity::class,
        ConditionSpeciesJunction::class,
        ConditionSymptomJunction::class,
        ConditionReferenceJunction::class,
        AppSettingEntity::class,
    ],
    version = 2, // literal required by the Room processor
    exportSchema = true,
)
abstract class VetguideDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun caseDao(): CaseDao
    abstract fun caseArtifactDao(): CaseArtifactDao
    abstract fun kbDao(): KbDao

    companion object {
        const val VETGUIDE_DB_VERSION = 2
        const val VETGUIDE_DB_NAME = "vetguide.db"
        const val KB_SEED_VERSION_KEY = "kb_seed_version"

        /** v2: knowledge-base tables (§9/§11.1). Additive only — no data touched. */
        val MIGRATION_1_TO_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kb_conditions` " +
                        "(`id` TEXT NOT NULL, `name` TEXT NOT NULL, `overview` TEXT NOT NULL, " +
                        "`urgency` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kb_symptoms` " +
                        "(`id` TEXT NOT NULL, `name` TEXT NOT NULL, `bodySystem` TEXT NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kb_references` " +
                        "(`id` TEXT NOT NULL, `title` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kb_condition_species` " +
                        "(`conditionId` TEXT NOT NULL, `species` TEXT NOT NULL, " +
                        "PRIMARY KEY(`conditionId`, `species`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kb_condition_symptoms` " +
                        "(`conditionId` TEXT NOT NULL, `symptomId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`conditionId`, `symptomId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kb_condition_references` " +
                        "(`conditionId` TEXT NOT NULL, `referenceId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`conditionId`, `referenceId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_settings` " +
                        "(`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_condition_species_species` " +
                    "ON `kb_condition_species` (`species`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_condition_symptoms_symptomId` " +
                    "ON `kb_condition_symptoms` (`symptomId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_condition_references_referenceId` " +
                    "ON `kb_condition_references` (`referenceId`)")
            }
        }
    }
}
