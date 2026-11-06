package com.prudent66.vetguide.data.repository

import com.prudent66.vetguide.database.CaseArtifactEntity
import com.prudent66.vetguide.database.CaseEntity
import com.prudent66.vetguide.database.PatientEntity
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseArtifact
import com.prudent66.vetguide.domain.model.Patient

/** Entity ↔ domain mapping (keeps the domain layer free of Room types). */

fun PatientEntity.toDomain(): Patient = Patient(
    id = id,
    name = name,
    species = species,
    breed = breed,
    sex = sex,
    neuterStatus = neuterStatus,
    dateOfBirthEpochMs = dateOfBirthEpochMs,
    estimatedAgeMonths = estimatedAgeMonths,
    weightKg = weightKg,
    weightUpdatedAtEpochMs = weightUpdatedAtEpochMs,
    ownerName = ownerName,
    ownerPhone = ownerPhone,
    ownerNotes = ownerNotes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun Patient.toEntity(): PatientEntity = PatientEntity(
    id = id,
    name = name,
    species = species,
    breed = breed,
    sex = sex,
    neuterStatus = neuterStatus,
    dateOfBirthEpochMs = dateOfBirthEpochMs,
    estimatedAgeMonths = estimatedAgeMonths,
    weightKg = weightKg,
    weightUpdatedAtEpochMs = weightUpdatedAtEpochMs,
    ownerName = ownerName,
    ownerPhone = ownerPhone,
    ownerNotes = ownerNotes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CaseEntity.toDomain(): Case = Case(
    id = id,
    patientId = patientId,
    presentingComplaint = presentingComplaint,
    status = status,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun Case.toEntity(): CaseEntity = CaseEntity(
    id = id,
    patientId = patientId,
    presentingComplaint = presentingComplaint,
    status = status,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CaseArtifactEntity.toDomain(): CaseArtifact = CaseArtifact(
    id = id,
    caseId = caseId,
    type = type,
    content = content,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CaseArtifact.toEntity(): CaseArtifactEntity = CaseArtifactEntity(
    id = id,
    caseId = caseId,
    type = type,
    content = content,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)
