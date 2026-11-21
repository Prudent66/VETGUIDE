package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pending cloud AI analysis queued for background retry when connectivity
 * improves (store-and-forward). Created when an image upload times out or
 * the device is offline; consumed by [CloudAnalysisWorker].
 */
@Entity(
    tableName = "pending_cloud_analyses",
    indices = [
        Index("createdAtEpochMs"),
        Index("caseId"),
    ],
)
data class PendingCloudAnalysisEntity(
    /** UUID — also the WorkManager work ID for idempotent scheduling. */
    @PrimaryKey val id: String,
    /** Compressed JPEG Base64 payload (800px/70% or 512px/50% retry). */
    val imageBase64: String,
    /** Species context for the analysis prompt. */
    val species: String?,
    /** Optional clinical context (age, etc.) from the questionnaire. */
    val clinicalContext: String?,
    /** Optional linked case ID (if Save-to-Case was used). */
    val caseId: String?,
    /** Optional patient ID for the linked case. */
    val patientId: String?,
    /** Epoch millis when the original attempt failed. */
    val createdAtEpochMs: Long,
    /** Whether the compact 512px/50% retry has already been attempted. */
    val compactRetryAttempted: Boolean = false,
    /** Number of background retry attempts (for back-off / max-retry logic). */
    val retryCount: Int = 0,
)