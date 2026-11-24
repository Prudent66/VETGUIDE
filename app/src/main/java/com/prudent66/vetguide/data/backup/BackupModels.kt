package com.prudent66.vetguide.data.backup

import com.prudent66.vetguide.database.CaseArtifactEntity
import com.prudent66.vetguide.database.CaseEntity
import com.prudent66.vetguide.database.PatientEntity
import com.prudent66.vetguide.domain.model.AgeUnit
import com.prudent66.vetguide.domain.model.CaseArtifactType
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.Species
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable JSON backup of the local clinical records. Field names are explicit
 * and enums travel as strings so a backup stays readable/restorable across
 * APK updates. Artifacts are included so SOAP notes, AI findings, and image
 * payloads survive a device move.
 */
@Serializable
data class BackupPayload(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAtEpochMs: Long = 0L,
    val appVersion: String = "",
    val patients: List<PatientBackup> = emptyList(),
    val cases: List<CaseBackup> = emptyList(),
    val artifacts: List<ArtifactBackup> = emptyList(),
) {
    companion object {
        const val FORMAT = "vetguide-backup"
        const val VERSION = 1
    }
}

@Serializable
data class PatientBackup(
    val id: String,
    val name: String,
    val animalCategory: String = "DOMESTIC",
    val species: String,
    val breed: String? = null,
    val sex: String,
    val neuterStatus: String,
    val dateOfBirthEpochMs: Long? = null,
    val ageValue: Double? = null,
    val ageUnit: String? = null,
    val weightKg: Double? = null,
    val weightUpdatedAtEpochMs: Long? = null,
    val ownerName: String? = null,
    val ownerPhone: String? = null,
    val ownerNotes: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Serializable
data class CaseBackup(
    val id: String,
    val patientId: String,
    val presentingComplaint: String,
    val status: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Serializable
data class ArtifactBackup(
    val id: String,
    val caseId: String,
    val type: String,
    val content: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/** Compact, forward-compatible JSON codec for backup files. */
object BackupJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: BackupPayload): String = json.encodeToString(BackupPayload.serializer(), payload)

    fun decode(raw: String): BackupPayload = json.decodeFromString(BackupPayload.serializer(), raw)
}

fun PatientEntity.toBackup(): PatientBackup = PatientBackup(
    id = id,
    name = name,
    animalCategory = animalCategory.name,
    species = species.name,
    breed = breed,
    sex = sex.name,
    neuterStatus = neuterStatus.name,
    dateOfBirthEpochMs = dateOfBirthEpochMs,
    ageValue = ageValue,
    ageUnit = ageUnit?.name,
    weightKg = weightKg,
    weightUpdatedAtEpochMs = weightUpdatedAtEpochMs,
    ownerName = ownerName,
    ownerPhone = ownerPhone,
    ownerNotes = ownerNotes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun PatientBackup.toEntity(): PatientEntity = PatientEntity(
    id = id,
    name = name,
    animalCategory = runCatching { enumValueOf<com.prudent66.vetguide.domain.model.AnimalCategory>(animalCategory) }
        .getOrDefault(com.prudent66.vetguide.domain.model.AnimalCategory.DOMESTIC),
    species = enumValueOf<Species>(species),
    breed = breed,
    sex = enumValueOf<PatientSex>(sex),
    neuterStatus = enumValueOf<NeuterStatus>(neuterStatus),
    dateOfBirthEpochMs = dateOfBirthEpochMs,
    ageValue = ageValue,
    ageUnit = ageUnit?.let { enumValueOf<AgeUnit>(it) },
    weightKg = weightKg,
    weightUpdatedAtEpochMs = weightUpdatedAtEpochMs,
    ownerName = ownerName,
    ownerPhone = ownerPhone,
    ownerNotes = ownerNotes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CaseEntity.toBackup(): CaseBackup = CaseBackup(
    id = id,
    patientId = patientId,
    presentingComplaint = presentingComplaint,
    status = status.name,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CaseBackup.toEntity(): CaseEntity = CaseEntity(
    id = id,
    patientId = patientId,
    presentingComplaint = presentingComplaint,
    status = enumValueOf<CaseStatus>(status),
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CaseArtifactEntity.toBackup(): ArtifactBackup = ArtifactBackup(
    id = id,
    caseId = caseId,
    type = type.name,
    content = content,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun ArtifactBackup.toEntity(): CaseArtifactEntity = CaseArtifactEntity(
    id = id,
    caseId = caseId,
    type = enumValueOf<CaseArtifactType>(type),
    content = content,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)
