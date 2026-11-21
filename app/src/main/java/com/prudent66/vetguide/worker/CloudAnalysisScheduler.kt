package com.prudent66.vetguide.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.prudent66.vetguide.database.PendingCloudAnalysisEntity
import java.util.concurrent.TimeUnit

/**
 * Schedules and manages [CloudAnalysisWorker] requests for the store-and-forward
 * queue. All work is constrained to [NetworkType.CONNECTED] so retries only
 * run when the device has an active internet connection.
 */
object CloudAnalysisScheduler {

    /** Unique work tag for enqueueing/cancelling. */
    private const val WORK_TAG = "cloud_analysis_retry"

    /**
     * Schedules a background retry for the given pending analysis ID.
     * Uses exponential back-off (WorkManager default) with a minimum delay
     * that grows with each retry attempt.
     */
    fun scheduleRetry(context: Context, workId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequest.Builder(CloudAnalysisWorker::class.java)
            .setConstraints(constraints)
            .setInitialDelay(calculateBackoffDelay(workId), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .setInputData(androidx.work.Data.Builder().putString("workId", workId).build())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workId, androidx.work.ExistingWorkPolicy.KEEP, workRequest)
    }

    /**
     * Enqueues a new pending analysis for background retry when connectivity
     * returns. Called when an image upload times out or the device is offline.
     */
    fun enqueueNewAnalysis(context: Context, pending: PendingCloudAnalysisEntity) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequest.Builder(CloudAnalysisWorker::class.java)
            .setConstraints(constraints)
            .setInputData(androidx.work.Data.Builder().putString("workId", pending.id).build())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(pending.id, androidx.work.ExistingWorkPolicy.KEEP, workRequest)
    }

    /** Cancels any pending retry for the given work ID. */
    fun cancelRetry(context: Context, workId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workId)
    }

    /** Calculates a simple back-off delay based on retry count encoded in the work ID hash. */
    private fun calculateBackoffDelay(workId: String): Long {
        // Simple deterministic delay based on workId hash for demonstration.
        // In practice, the retry count is stored in the entity and passed via input data.
        return (workId.hashCode().absoluteValue % 5 + 1) * 60_000L // 1-5 minutes
    }
}