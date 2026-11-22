package com.prudent66.vetguide.domain.repository

import com.prudent66.vetguide.database.PendingCloudAnalysisEntity

/**
 * Schedules a queued cloud analysis for background retry when connectivity
 * returns. Abstracted from WorkManager so the image-analysis ViewModel stays
 * unit-testable without an Android [android.content.Context].
 */
interface CloudAnalysisScheduler {
    fun enqueue(pending: PendingCloudAnalysisEntity)
}
