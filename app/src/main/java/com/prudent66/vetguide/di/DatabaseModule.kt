package com.prudent66.vetguide.di

import android.content.Context
import androidx.room.Room
import com.prudent66.vetguide.data.repository.BackupRepositoryImpl
import com.prudent66.vetguide.data.repository.CaseRepositoryImpl
import com.prudent66.vetguide.data.repository.KnowledgeRepositoryImpl
import com.prudent66.vetguide.data.repository.PatientRepositoryImpl
import com.prudent66.vetguide.data.repository.PendingCloudAnalysisRepositoryImpl
import com.prudent66.vetguide.data.repository.QuestionnaireRepositoryImpl
import com.prudent66.vetguide.data.repository.SettingsRepositoryImpl
import com.prudent66.vetguide.database.AppSettingsDao
import com.prudent66.vetguide.database.CaseArtifactDao
import com.prudent66.vetguide.database.CaseDao
import com.prudent66.vetguide.database.KbDao
import com.prudent66.vetguide.database.PatientDao
import com.prudent66.vetguide.database.PendingCloudAnalysisDao
import com.prudent66.vetguide.database.QuestionnaireDao
import com.prudent66.vetguide.database.VetguideDatabase
import com.prudent66.vetguide.domain.repository.BackupRepository
import com.prudent66.vetguide.domain.repository.CaseRepository
import com.prudent66.vetguide.domain.repository.CloudAnalysisScheduler
import com.prudent66.vetguide.domain.repository.KnowledgeRepository
import com.prudent66.vetguide.domain.repository.PatientRepository
import com.prudent66.vetguide.domain.repository.PendingCloudAnalysisRepository
import com.prudent66.vetguide.domain.repository.QuestionnaireRepository
import com.prudent66.vetguide.domain.repository.SettingsRepository
import com.prudent66.vetguide.worker.WorkManagerCloudAnalysisScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the offline-first Room database and its DAOs (roadmap §11). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VetguideDatabase =
        Room.databaseBuilder(
            context = context,
            klass = VetguideDatabase::class.java,
            name = VetguideDatabase.VETGUIDE_DB_NAME,
        ).addMigrations(
            VetguideDatabase.MIGRATION_1_TO_2,
            VetguideDatabase.MIGRATION_2_TO_3,
            VetguideDatabase.migration4To5,
            VetguideDatabase.migration5To6,
        ).fallbackToDestructiveMigration(true).build()

    @Provides
    fun providePatientDao(db: VetguideDatabase): PatientDao = db.patientDao()

    @Provides
    fun provideCaseDao(db: VetguideDatabase): CaseDao = db.caseDao()

    @Provides
    fun provideCaseArtifactDao(db: VetguideDatabase): CaseArtifactDao = db.caseArtifactDao()

    @Provides
    fun provideKbDao(db: VetguideDatabase): KbDao = db.kbDao()

    @Provides
    fun provideQuestionnaireDao(db: VetguideDatabase): QuestionnaireDao = db.questionnaireDao()

    @Provides
    fun provideAppSettingsDao(db: VetguideDatabase): AppSettingsDao = db.appSettingsDao()

    @Provides
    fun providePendingCloudAnalysisDao(db: VetguideDatabase): PendingCloudAnalysisDao = db.pendingCloudAnalysisDao()
}

/** Binds repository implementations to their domain contracts. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPatientRepository(impl: PatientRepositoryImpl): PatientRepository

    @Binds
    @Singleton
    abstract fun bindCaseRepository(impl: CaseRepositoryImpl): CaseRepository

    @Binds
    @Singleton
    abstract fun bindKnowledgeRepository(impl: KnowledgeRepositoryImpl): KnowledgeRepository

    @Binds
    @Singleton
    abstract fun bindQuestionnaireRepository(impl: QuestionnaireRepositoryImpl): QuestionnaireRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindPendingCloudAnalysisRepository(impl: PendingCloudAnalysisRepositoryImpl): PendingCloudAnalysisRepository

    @Binds
    @Singleton
    abstract fun bindCloudAnalysisScheduler(impl: WorkManagerCloudAnalysisScheduler): CloudAnalysisScheduler
}
