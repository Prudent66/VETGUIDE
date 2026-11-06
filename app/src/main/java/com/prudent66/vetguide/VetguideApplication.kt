package com.prudent66.vetguide

import android.app.Application
import com.prudent66.vetguide.data.kb.KbSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application entry point for Hilt dependency injection. */
@HiltAndroidApp
class VetguideApplication : Application() {

    @Inject
    lateinit var kbSeeder: KbSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Idempotent KB seed import on first launch (skipped when seed version matches).
        applicationScope.launch {
            try {
                kbSeeder.seedIfNeeded()
            } catch (e: Exception) {
                android.util.Log.e("VetguideApplication", "Unexpected KB seeding failure", e)
            }
        }
    }
}
