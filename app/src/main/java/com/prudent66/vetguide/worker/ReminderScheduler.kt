package com.prudent66.vetguide.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules offline follow-up, repeat-dose and withdrawal reminders. */
@Singleton
class ReminderScheduler @Inject constructor() {

    /** Returns false when POST_NOTIFICATIONS has not been granted. */
    fun schedule(
        context: Context,
        title: String,
        body: String,
        delayMillis: Long,
    ): Boolean {
        if (!canNotify(context) || delayMillis <= 0L) return false
        val data = Data.Builder()
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(ReminderWorker.KEY_BODY, body)
            .build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        return true
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < ANDROID_13 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val ANDROID_13 = 33
    }
}
