package com.prudent66.vetguide.data.kb

import android.content.Context
import androidx.room.withTransaction
import com.prudent66.vetguide.database.VetguideDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-side orchestrator: reads the seed asset and applies it once per
 * seed version (skipping when the stored version matches — idempotent).
 */
class KbSeeder @Inject constructor(
    private val db: VetguideDatabase,
    @ApplicationContext private val context: Context,
) {

    /**
     * @return true when a seed (re)import was applied; false if already at
     * the current seed version or the asset failed to parse.
     */
    suspend fun seedIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val payload = try {
            KbSeedParser.parse(readSeedAsset(SEED_ASSET))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "KB seed skipped: invalid seed asset", e)
            return@withContext false
        }

        val dao = db.kbDao()
        if (dao.getSettingValue(KbSeedMarker.SEED_VERSION_KEY) == payload.version.toString()) {
            return@withContext false
        }

        db.withTransaction {
            KbSeedApplier.apply(payload, dao)
        }
        true
    }

    private fun readSeedAsset(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "KbSeeder"
        const val SEED_ASSET = "kb_seed.json"
    }
}
