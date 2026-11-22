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
import com.prudent66.vetguide.domain.repository.CloudAnalysisScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-backed [CloudAnalysisScheduler]. Work is constrained to
 * [NetworkType.CONNECTED] so retries only run once the device has an active
 * internet connection. Retries use exponential back-off; the worker itself
 * enforces the maximum attempt count via [PendingCloudAnalysisEntity.retryCount].
 */
@Singleton
class WorkManagerCloudAnalysisScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : CloudAnalysisScheduler {

    override fun enqueue(pending: PendingCloudAnalysisEntity) {
        val request = OneTimeWorkRequestBuilder<CloudAnalysisWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_WORK_ID to pending.id))
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(pending.id, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val KEY_WORK_ID = "workId"
        const val WORK_TAG = "cloud_analysis_retry"
        const val BACKOFF_MINUTES = 15L
    }
}
