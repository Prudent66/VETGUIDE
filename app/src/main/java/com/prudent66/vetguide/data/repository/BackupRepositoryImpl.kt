package com.prudent66.vetguide.data.repository

import androidx.room.withTransaction
import com.prudent66.vetguide.BuildConfig
import com.prudent66.vetguide.data.backup.BackupJson
import com.prudent66.vetguide.data.backup.BackupPayload
import com.prudent66.vetguide.data.backup.toBackup
import com.prudent66.vetguide.data.backup.toEntity
import com.prudent66.vetguide.database.VetguideDatabase
import com.prudent66.vetguide.domain.repository.BackupImportResult
import com.prudent66.vetguide.domain.repository.BackupRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JSON backup over the local Room tables. Export is a consistent snapshot
 * inside one transaction; import is additive (upsert by id) and ignores rows
 * whose parent is missing, so restoring an older backup never deletes newer
 * records. Room schema/version is untouched.
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val db: VetguideDatabase,
) : BackupRepository {

    override suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val payload = BackupPayload(
                exportedAtEpochMs = System.currentTimeMillis(),
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                patients = db.patientDao().getAll().map { it.toBackup() },
                cases = db.caseDao().getAll().map { it.toBackup() },
                artifacts = db.caseArtifactDao().getAll().map { it.toBackup() },
            )
            BackupJson.encode(payload)
        }
    }

    override suspend fun importJson(json: String): BackupImportResult =
        withContext(Dispatchers.IO) {
            val payload = BackupJson.decode(json)
            require(payload.format == BackupPayload.FORMAT) {
                "This file is not a VETGUIDE backup"
            }
            require(payload.version <= BackupPayload.VERSION) {
                "This backup was created by a newer VETGUIDE version"
            }
            db.withTransaction { restore(payload) }
        }

    private suspend fun restore(payload: BackupPayload): BackupImportResult {
        val patients = payload.patients.map { it.toEntity() }
        val patientIds = db.patientDao().getAll().mapTo(mutableSetOf()) { it.id }
        patientIds += patients.map { it.id }

        val cases = payload.cases
            .filter { it.patientId in patientIds }
            .map { it.toEntity() }
        val caseIds = db.caseDao().getAll().mapTo(mutableSetOf()) { it.id }
        caseIds += cases.map { it.id }

        val artifacts = payload.artifacts
            .filter { it.caseId in caseIds }
            .map { it.toEntity() }

        db.patientDao().upsertAll(patients)
        db.caseDao().upsertAll(cases)
        db.caseArtifactDao().upsertAll(artifacts)
        return BackupImportResult(
            patients = patients.size,
            cases = cases.size,
            artifacts = artifacts.size,
        )
    }
}
