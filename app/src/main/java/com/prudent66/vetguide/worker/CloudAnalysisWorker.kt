package com.prudent66.vetguide.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prudent66.vetguide.R
import com.prudent66.vetguide.data.ai.CloudAiProvider
import com.prudent66.vetguide.data.ai.ImageEncoder
import com.prudent66.vetguide.database.PendingCloudAnalysisDao
import com.prudent66.vetguide.database.PendingCloudAnalysisEntity
import com.prudent66.vetguide.database.VetguideDatabase
import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.AiRequest
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseArtifact
import com.prudent66.vetguide.domain.model.CaseArtifactType
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.repository.CaseRepository
import com.prudent66.vetguide.domain.repository.PatientRepository
import com.prudent66.vetguide.ui.navigation.Destinations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Assisted
import javax.inject.Inject

/**
 * Background worker that retries pending cloud AI analyses when connectivity
 * is available (store-and-forward). Scheduled by [CloudAnalysisScheduler]
 * with a [NetworkType.CONNECTED] constraint.
 *
 * Flow:
 * 1. Pick the oldest pending analysis that hasn't exceeded max retries.
 * 2. Call the cloud provider with the stored image payload.
 * 3. On success: save the AI findings as a CaseArtifact (type=AI) linked to
 *    the original case, or create a new case if none exists.
 * 4. On failure: increment retry count and reschedule (exponential back-off).
 * 5. Post a local notification when the analysis is ready.
 */
@HiltWorker
class CloudAnalysisWorker @Inject constructor(
    @ApplicationContext private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val db: VetguideDatabase,
    private val caseRepository: CaseRepository,
    private val patientRepository: PatientRepository,
    private val cloudProvider: CloudAiProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workId = params.inputData.getString("workId") ?: return Result.success()
        val dao = db.pendingCloudAnalysisDao()
        val pending = dao.getById(workId) ?: return Result.success()

        // Build the analysis request from stored data.
        val request = AiRequest(
            headline = pending.species
                ?.let { "Species: $it" }
                ?: "Unspecified species",
            findings = buildString {
                append("Photograph of the animal's presenting condition submitted for AI image analysis.")
                pending.clinicalContext?.let { ctx -> append(" $ctx") }
            },
            imageBase64 = pending.imageBase64,
            clinicalContext = pending.clinicalContext?.trim()?.takeIf { it.isNotEmpty() },
        )

        // Attempt cloud analysis.
        val attempt = cloudProvider.analyze(request)

        return@withContext when (attempt) {
            is com.prudent66.vetguide.domain.ai.AiAttempt.Success -> {
                // Persist the AI findings into the case record.
                saveAiFindings(pending, attempt.analysisData!!)
                // Remove from queue.
                dao.delete(pending)
                // Notify the user.
                postCompletionNotification(pending)
                Result.success()
            }
            is com.prudent66.vetguide.domain.ai.AiAttempt.Failure -> {
                // Increment retry count and update.
                val updated = pending.copy(retryCount = pending.retryCount + 1)
                dao.update(updated)
                // Reschedule with exponential back-off if under limit.
                if (updated.retryCount < 5) {
                    CloudAnalysisScheduler.scheduleRetry(context, updated.id)
                } else {
                    // Max retries exceeded — delete and notify failure.
                    dao.delete(pending)
                    postFailureNotification(pending)
                }
                Result.retry() // WorkManager will back off automatically
            }
        }
    }

    private suspend fun saveAiFindings(pending: PendingCloudAnalysisEntity, analysis: AiAnalysisData) {
        val caseId = pending.caseId ?: createCaseForAnalysis(pending)
        val artifact = CaseArtifact(
            id = UUID.randomUUID().toString(),
            caseId = caseId,
            type = CaseArtifactType.AI,
            content = analysis.toJson(), // Store full structured analysis as JSON
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        db.caseArtifactDao().insert(artifact)
    }

    private suspend fun createCaseForAnalysis(pending: PendingCloudAnalysisEntity): String {
        val patientId = pending.patientId ?: createPatientForAnalysis(pending)
        val newCase = Case(
            id = UUID.randomUUID().toString(),
            patientId = patientId,
            presentingComplaint = "AI image analysis (queued)",
            status = CaseStatus.OPEN,
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        caseRepository.insert(newCase)
        return newCase.id
    }

    private suspend fun createPatientForAnalysis(pending: PendingCloudAnalysisEntity): String {
        val species = pending.species?.let { com.prudent66.vetguide.domain.model.Species.valueOf(it) }
            ?: com.prudent66.vetguide.domain.model.Species.OTHER
        val patient = com.prudent66.vetguide.domain.model.Patient(
            id = UUID.randomUUID().toString(),
            name = "AI Analysis ${SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(pending.createdAtEpochMs))}",
            species = species,
            breed = null,
            sex = com.prudent66.vetguide.domain.model.PatientSex.UNKNOWN,
            neuterStatus = com.prudent66.vetguide.domain.model.NeuterStatus.UNKNOWN,
            dateOfBirthEpochMs = null,
            ageValue = null,
            ageUnit = null,
            weightKg = null,
            weightUpdatedAtEpochMs = null,
            ownerName = null,
            ownerPhone = null,
            ownerNotes = "Created for queued AI analysis",
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        patientRepository.insert(patient)
        return patient.id
    }

    private fun postCompletionNotification(pending: PendingCloudAnalysisEntity) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "cloud_analysis",
                "Cloud AI Analysis",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, "cloud_analysis")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Cloud AI analysis complete")
            .setContentText("Your queued image analysis is ready. Open the case to review.")
            .setAutoCancel(true)
            .build()
        manager.notify(pending.id.hashCode(), notification)
    }

    private fun postFailureNotification(pending: PendingCloudAnalysisEntity) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "cloud_analysis",
                "Cloud AI Analysis",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, "cloud_analysis")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Cloud AI analysis failed")
            .setContentText("Automatic retry exhausted. Try again manually from the case.")
            .setAutoCancel(true)
            .build()
        manager.notify(pending.id.hashCode(), notification)
    }
}