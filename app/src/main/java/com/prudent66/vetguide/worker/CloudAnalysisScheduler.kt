package com.prudent66.vetguide.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prudent66.vetguide.database.PendingCloudAnalysisEntity
import java.util.concurrent.TimeUnit

/**
 * Schedules [CloudAnalysisWorker] requests for the store-and-forward queue.
 * Work is constrained to [NetworkType.CONNECTED] so retries only run once the
 * device has an active internet connection. Retries use exponential back-off;
 * the worker itself enforces the maximum attempt count via
 * [PendingCloudAnalysisEntity.retryCount].
 */
object CloudAnalysisScheduler {

    private const val WORK_TAG = "cloud_analysis_retry"
    private const val BACKOFF_MINUTES = 15L

    /**
     * Enqueues a queued analysis for background retry. A unique work name per
     * pending row keeps repeated taps from stacking duplicate requests.
     */
    fun enqueueNewAnalysis(context: Context, pending: PendingCloudAnalysisEntity) {
        val request = OneTimeWorkRequestBuilder<CloudAnalysisWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf("workId" to pending.id))
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(pending.id, ExistingWorkPolicy.KEEP, request)
    }
}
