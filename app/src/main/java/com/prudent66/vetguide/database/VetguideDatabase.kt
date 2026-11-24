package com.prudent66.vetguide.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * VETGUIDE local database (roadmap §11: offline-first).
 * v4 (age value + unit) is a development-stage schema change: patients are
 * rebuilt via the destructive fallback in [com.prudent66.vetguide.di.DatabaseModule].
 * Restore strictly additive migrations before the first production release.
 * Schemas are exported to app/schemas and committed.
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
        QuestionEntity::class,
        QuestionOptionEntity::class,
        ConditionWeightEntity::class,
        PendingCloudAnalysisEntity::class,
    ],
    version = 7, // literal required by the Room processor
    exportSchema = true,
)
abstract class VetguideDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun caseDao(): CaseDao
    abstract fun caseArtifactDao(): CaseArtifactDao
    abstract fun kbDao(): KbDao
    abstract fun questionnaireDao(): QuestionnaireDao

    abstract fun appSettingsDao(): AppSettingsDao

    abstract fun pendingCloudAnalysisDao(): PendingCloudAnalysisDao

    companion object {
        const val VETGUIDE_DB_VERSION = 7
        const val VETGUIDE_DB_NAME = "vetguide.db"
        const val KB_SEED_VERSION_KEY = "kb_seed_version"

        /** v2: knowledge-base tables (§9/§11.1). Additive only — no data touched. */
        val MIGRATION_1_TO_2: Migration = object : Migration(1, 2) {            override fun migrate(db: SupportSQLiteDatabase) {
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

        /** v3: guided questionnaire (§Phase 3) — questions, options, dummy weights. */
        val MIGRATION_2_TO_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kb_questions` " +
                        "(`id` TEXT NOT NULL, `species` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kb_question_options` " +
                        "(`id` TEXT NOT NULL, `questionId` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`symptomId` TEXT, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kb_condition_weights` " +
                        "(`conditionId` TEXT NOT NULL, `symptomId` TEXT NOT NULL, " +
                        "`weight` REAL NOT NULL, PRIMARY KEY(`conditionId`, `symptomId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_question_options_questionId` " +
                    "ON `kb_question_options` (`questionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_condition_weights_symptomId` " +
                    "ON `kb_condition_weights` (`symptomId`)")
            }
        }

        /** v5: pending cloud analyses for store-and-forward (§9 resilience). */
        val migration4To5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_cloud_analyses` " +
                        "(`id` TEXT NOT NULL, `imageBase64` TEXT NOT NULL, " +
                        "`species` TEXT, `clinicalContext` TEXT, " +
                        "`caseId` TEXT, `patientId` TEXT, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, " +
                        "`compactRetryAttempted` INTEGER NOT NULL, " +
                        "`retryCount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_pending_cloud_analyses_createdAtEpochMs` " +
                        "ON `pending_cloud_analyses` (`createdAtEpochMs`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_cloud_analyses_caseId` " +
                    "ON `pending_cloud_analyses` (`caseId`)")
            }
        }

        /** v6: domestic/wildlife pathway on patient records. */
        val migration5To6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `patients` ADD COLUMN `animalCategory` TEXT NOT NULL " +
                        "DEFAULT 'DOMESTIC'",
                )
            }
        }

        /** v7: wildlife group label for Wild patients. */
        val migration6To7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `patients` ADD COLUMN `wildlifeGroup` TEXT")
            }
        }
    }
}
