package com.prudent66.vetguide

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.prudent66.vetguide.data.kb.KbSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application entry point for Hilt dependency injection. */
@HiltAndroidApp
class VetguideApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var kbSeeder: KbSeeder

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Hilt-injected WorkManager configuration. The default initializer is
     * removed in the manifest so WorkManager uses [HiltWorkerFactory] and can
     * construct [com.prudent66.vetguide.worker.CloudAnalysisWorker].
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Idempotent KB seed import on first launch (skipped when seed version matches).
        applicationScope.launch {
            try {
                kbSeeder.seedIfNeeded()
            } catch (e: IllegalStateException) {
                android.util.Log.e("VetguideApplication", "Unexpected KB seeding failure", e)
            }
        }
    }
}
