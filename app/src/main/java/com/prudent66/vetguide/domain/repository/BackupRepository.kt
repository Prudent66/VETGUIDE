package com.prudent66.vetguide.domain.repository

/** Counts reported back to Settings after a successful import. */
data class BackupImportResult(
    val patients: Int,
    val cases: Int,
    val artifacts: Int,
)

/**
 * Local patient/case backup contract. Export yields a portable JSON document;
 * import is additive (upsert by id) so a restore never deletes records that
 * are already on the device.
 */
interface BackupRepository {

    suspend fun exportJson(): String

    /**
     * Restores patients, cases, and artifacts from [json].
     *
     * @throws IllegalArgumentException for malformed or non-VETGUIDE JSON.
     */
    suspend fun importJson(json: String): BackupImportResult
}
