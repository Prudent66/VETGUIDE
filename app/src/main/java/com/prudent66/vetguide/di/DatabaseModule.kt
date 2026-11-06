package com.prudent66.vetguide.di

import android.content.Context
import androidx.room.Room
import com.prudent66.vetguide.data.repository.CaseRepositoryImpl
import com.prudent66.vetguide.data.repository.PatientRepositoryImpl
import com.prudent66.vetguide.database.CaseArtifactDao
import com.prudent66.vetguide.database.CaseDao
import com.prudent66.vetguide.database.PatientDao
import com.prudent66.vetguide.database.VetguideDatabase
import com.prudent66.vetguide.domain.repository.CaseRepository
import com.prudent66.vetguide.domain.repository.PatientRepository
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
        ).build()

    @Provides
    fun providePatientDao(db: VetguideDatabase): PatientDao = db.patientDao()

    @Provides
    fun provideCaseDao(db: VetguideDatabase): CaseDao = db.caseDao()

    @Provides
    fun provideCaseArtifactDao(db: VetguideDatabase): CaseArtifactDao = db.caseArtifactDao()
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
}
