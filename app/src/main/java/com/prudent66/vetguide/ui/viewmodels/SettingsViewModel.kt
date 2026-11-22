package com.prudent66.vetguide.ui.viewmodels

import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.data.ai.CloudHealthClient
import com.prudent66.vetguide.data.ai.CloudHealthResult
import com.prudent66.vetguide.domain.ai.AiOrchestrator
import com.prudent66.vetguide.domain.model.ClinicProfile
import com.prudent66.vetguide.domain.repository.BackupRepository
import com.prudent66.vetguide.domain.repository.ClinicProfileSettings
import com.prudent66.vetguide.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live status of the Settings "Test Cloud Connection" probe. */
sealed interface CloudConnectionUiState {
    data object Idle : CloudConnectionUiState
    data object Checking : CloudConnectionUiState
    data class Success(
        val status: String,
        val mode: String?,
        val elapsedMs: Long,
    ) : CloudConnectionUiState

    data class Failure(val message: String, val elapsedMs: Long) : CloudConnectionUiState
}

/**
 * Settings actions: clinic profile persistence (existing app_settings K/V),
 * the backend health probe, and JSON backup export/import through SAF URIs.
 * All storage work is off the main thread; user-facing outcomes are emitted on
 * [messages] for the screen snackbar.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val healthClient: CloudHealthClient,
    private val orchestrator: AiOrchestrator,
) : ViewModel() {

    val profile: StateFlow<ClinicProfile> = ClinicProfileSettings.observe(settingsRepository)
        .catch { throwable ->
            Log.e(TAG, "Clinic profile unavailable", throwable)
            emit(ClinicProfile())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ClinicProfile(),
        )

    private val connectionMut = MutableStateFlow<CloudConnectionUiState>(
        CloudConnectionUiState.Idle,
    )
    val connection: StateFlow<CloudConnectionUiState> = connectionMut.asStateFlow()

    private val busyMut = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = busyMut.asStateFlow()

    val messages = MutableSharedFlow<String>(extraBufferCapacity = MESSAGE_BUFFER)

    fun saveProfile(profile: ClinicProfile) {
        viewModelScope.launch {
            try {
                ClinicProfileSettings.save(settingsRepository, profile)
                messages.emit("Clinic profile saved")
            } catch (e: SQLiteException) {
                Log.e(TAG, "Could not save clinic profile", e)
                messages.emit("Could not save the clinic profile")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Could not save clinic profile", e)
                messages.emit("Could not save the clinic profile")
            }
        }
    }

    fun testConnection() {
        if (connectionMut.value == CloudConnectionUiState.Checking) return
        connectionMut.value = CloudConnectionUiState.Checking
        viewModelScope.launch {
            connectionMut.value = when (val result = healthClient.check()) {
                is CloudHealthResult.Healthy -> {
                    // Backend is reachable again — drop any stale breaker so the
                    // next analysis retries cloud immediately (field fix).
                    orchestrator.resetCircuitBreaker()
                    CloudConnectionUiState.Success(
                        status = result.status,
                        mode = result.mode,
                        elapsedMs = result.elapsedMs,
                    )
                }
                is CloudHealthResult.Unhealthy -> CloudConnectionUiState.Failure(
                    message = result.message,
                    elapsedMs = result.elapsedMs,
                )
            }
        }
    }

    fun exportBackup(uri: Uri) {
        if (busyMut.value) return
        busyMut.value = true
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val json = backupRepository.exportJson()
                    writeText(uri, json)
                    json.length
                }
                messages.emit("Backup exported (${bytes / 1024} KB)")
            } catch (e: IOException) {
                messages.emit("Export failed: ${e.message ?: "could not write the file"}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Backup export blocked by storage security", e)
                messages.emit("Export failed: storage access was denied")
            } finally {
                busyMut.value = false
            }
        }
    }

    fun importBackup(uri: Uri) {
        if (busyMut.value) return
        busyMut.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    backupRepository.importJson(readText(uri))
                }
                messages.emit(
                    "Imported ${result.patients} patients, ${result.cases} cases, " +
                        "${result.artifacts} notes",
                )
            } catch (e: IllegalArgumentException) {
                messages.emit("Import failed: ${e.message ?: "invalid backup file"}")
            } catch (e: SQLiteException) {
                Log.e(TAG, "Backup import failed", e)
                messages.emit("Import failed: the database rejected the backup")
            } catch (e: IOException) {
                messages.emit("Import failed: ${e.message ?: "could not read the file"}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Backup import blocked by storage security", e)
                messages.emit("Import failed: storage access was denied")
            } finally {
                busyMut.value = false
            }
        }
    }

    private fun writeText(uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("The selected file could not be opened for writing")
        OutputStreamWriter(stream, Charsets.UTF_8).use { writer -> writer.write(text) }
    }

    private fun readText(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("The selected file could not be opened")
        return InputStreamReader(stream, Charsets.UTF_8).use { reader -> reader.readText() }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        const val MESSAGE_BUFFER = 8
    }
}
