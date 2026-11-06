package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.Species

/**
 * Patients table (roadmap §11.1). Stable UUID PK + updatedAt from day one so
 * future sync never needs destructive migrations. Enums persist via Room's
 * built-in name() conversion.
 */
@Entity(
    tableName = "patients",
    indices = [Index("updatedAtEpochMs")],
)
data class PatientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val species: Species,
    val breed: String?,
    val sex: PatientSex,
    val neuterStatus: NeuterStatus,
    val dateOfBirthEpochMs: Long?,
    val estimatedAgeMonths: Int?,
    val weightKg: Double?,
    val weightUpdatedAtEpochMs: Long?,
    val ownerName: String?,
    val ownerPhone: String?,
    val ownerNotes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
