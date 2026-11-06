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
 * Android-side seed orchestrator: reads the seed assets and applies each once
 * per seed version (skipping when the stored version matches — idempotent).
 */
class KbSeeder @Inject constructor(
    private val db: VetguideDatabase,
    @ApplicationContext private val context: Context,
) {

    /**
     * @return true when at least one seed (re)import was applied; false when
     * already at current versions or assets failed to parse.
     */
    suspend fun seedIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val kbApplied = applyKbSeed()
        val questionnaireApplied = applyQuestionnaireSeed()
        kbApplied || questionnaireApplied
    }

    private suspend fun applyKbSeed(): Boolean {
        val payload = parseSeed(KB_ASSET) { KbSeedParser.parse(it) } ?: return false

        val dao = db.kbDao()
        if (dao.getSettingValue(KbSeedMarker.SEED_VERSION_KEY) == payload.version.toString()) {
            return false
        }
        return runTransaction("KB seed") {
            KbSeedApplier.apply(payload, dao)
        }
    }

    private suspend fun applyQuestionnaireSeed(): Boolean {
        val payload = parseSeed(QUESTIONNAIRE_ASSET) { QuestionnaireSeedParser.parse(it) } ?: return false

        val dao = db.questionnaireDao()
        if (dao.getSettingValue(QuestionnaireSeedMarker.SEED_VERSION_KEY) == payload.version.toString()) {
            return false
        }
        return runTransaction("questionnaire seed", dao) {
            QuestionnaireSeedApplier.apply(payload, dao)
        }
    }

    /** Returns null (with a log) when the asset is unreadable or invalid. */
    private inline fun <T> parseSeed(asset: String, parse: (String) -> T): T? = try {
        parse(readAsset(asset))
    } catch (e: IOException) {
        android.util.Log.e(TAG, "Seed skipped (unreadable): $asset", e)
        null
    } catch (e: SerializationException) {
        android.util.Log.e(TAG, "Seed skipped (invalid): $asset", e)
        null
    }

    private suspend fun runTransaction(label: String, block: suspend () -> Unit): Boolean = try {
        db.withTransaction { block() }
        true
    } catch (e: SQLiteException) {
        android.util.Log.e(TAG, "$label apply failed", e)
        false
    }

    private fun readAsset(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "KbSeeder"
        const val KB_ASSET = "kb_seed.json"
        const val QUESTIONNAIRE_ASSET = "questionnaire_seed.json"
    }
}
