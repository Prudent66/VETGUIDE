package com.prudent66.vetguide.data.kb

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.prudent66.vetguide.database.VetguideDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

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
        } catch (e: IOException) {
            android.util.Log.e(TAG, "KB seed skipped: seed asset unreadable", e)
            return@withContext false
        } catch (e: SerializationException) {
            android.util.Log.e(TAG, "KB seed skipped: invalid seed asset", e)
            return@withContext false
        }

        val dao = db.kbDao()
        if (dao.getSettingValue(KbSeedMarker.SEED_VERSION_KEY) == payload.version.toString()) {
            return@withContext false
        }

        try {
            db.withTransaction {
                KbSeedApplier.apply(payload, dao)
            }
        } catch (e: SQLiteException) {
            android.util.Log.e(TAG, "KB seed apply failed", e)
            return@withContext false
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
