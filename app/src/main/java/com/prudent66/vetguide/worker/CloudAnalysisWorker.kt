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
import com.prudent66.vetguide.database.PendingCloudAnalysisEntity
import com.prudent66.vetguide.database.VetguideDatabase
import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.AiAttempt
import com.prudent66.vetguide.domain.ai.AiRequest
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseArtifact
import com.prudent66.vetguide.domain.model.CaseArtifactType
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.repository.CaseRepository
import com.prudent66.vetguide.domain.repository.PatientRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker that retries pending cloud AI analyses when connectivity
 * is available (store-and-forward). Scheduled by
 * [WorkManagerCloudAnalysisScheduler] with a
 * [androidx.work.NetworkType.CONNECTED] constraint.
 *
 * Flow:
 * 1. Load the queued analysis referenced by the work input.
 * 2. Call the cloud provider with the stored image payload.
 * 3. On success: save the AI findings as a [CaseArtifactType.AI] artifact
 *    linked to the original case (created when none is linked), remove the
 *    queue row, and post a local notification.
 * 4. On failure: increment the retry counter and ask WorkManager to back off;
 *    after [MAX_RETRIES] failed runs the row is dropped and a failure
 *    notification is posted.
 */
@HiltWorker
class CloudAnalysisWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val db: VetguideDatabase,
    private val caseRepository: CaseRepository,
    private val patientRepository: PatientRepository,
    private val cloudProvider: CloudAiProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workId = params.inputData.getString(KEY_WORK_ID)
            ?: return@withContext Result.success()
        val dao = db.pendingCloudAnalysisDao()
        val pending = dao.getById(workId)
            ?: return@withContext Result.success() // already handled or cancelled

        val attempt = runCatching { cloudProvider.analyze(pending.toAiRequest()) }
            .getOrElse { error ->
                if (error is kotlin.coroutines.cancellation.CancellationException) throw error
                AiAttempt.Failure(error.message ?: "queued cloud analysis failed")
            }
        return@withContext when (attempt) {
            is AiAttempt.Success -> {
                val analysis = attempt.analysisData
                if (analysis == null) {
                    // Defensive: a success without structured data cannot be saved.
                    postFailureNotification(pending)
                    dao.delete(pending)
                    Result.failure()
                } else {
                    saveAiFindings(pending, analysis)
                    dao.delete(pending)
                    postCompletionNotification(pending)
                    Result.success()
                }
            }
            is AiAttempt.Failure -> handleFailure(pending)
        }
    }

    private suspend fun handleFailure(pending: PendingCloudAnalysisEntity): Result {
        val updated = pending.copy(retryCount = pending.retryCount + 1)
        val dao = db.pendingCloudAnalysisDao()
        return if (updated.retryCount < MAX_RETRIES) {
            dao.update(updated)
            Result.retry() // WorkManager applies exponential back-off
        } else {
            dao.deleteById(pending.id)
            postFailureNotification(pending)
            Result.failure()
        }
    }

    private suspend fun saveAiFindings(
        pending: PendingCloudAnalysisEntity,
        analysis: AiAnalysisData,
    ) {
        val caseId = pending.caseId
            ?.takeIf { caseRepository.getCase(it) != null }
            ?: createCaseForAnalysis(pending)
        val now = System.currentTimeMillis()
        caseRepository.upsertArtifact(
            CaseArtifact(
                id = UUID.randomUUID().toString(),
                caseId = caseId,
                type = CaseArtifactType.AI,
                content = analysis.toJson(),
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    private suspend fun createCaseForAnalysis(pending: PendingCloudAnalysisEntity): String {
        val patientId = pending.patientId
            ?.takeIf { patientRepository.getPatient(it) != null }
            ?: createPatientForAnalysis(pending)
        val now = System.currentTimeMillis()
        val newCase = Case(
            id = UUID.randomUUID().toString(),
            patientId = patientId,
            presentingComplaint = "AI image analysis (queued)",
            status = CaseStatus.OPEN,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        caseRepository.upsertCase(newCase)
        return newCase.id
    }

    private suspend fun createPatientForAnalysis(pending: PendingCloudAnalysisEntity): String {
        val now = System.currentTimeMillis()
        val species = Species.entries.firstOrNull { it.name == pending.species } ?: Species.OTHER
        val label = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(pending.createdAtEpochMs))
        val patient = Patient(
            id = UUID.randomUUID().toString(),
            name = "AI Analysis $label",
            species = species,
            breed = null,
            sex = PatientSex.UNKNOWN,
            neuterStatus = NeuterStatus.UNKNOWN,
            dateOfBirthEpochMs = null,
            ageValue = null,
            ageUnit = null,
            weightKg = null,
            weightUpdatedAtEpochMs = null,
            ownerName = null,
            ownerPhone = null,
            ownerNotes = "Created for queued AI analysis",
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        patientRepository.upsertPatient(patient)
        return patient.id
    }

    private fun postCompletionNotification(pending: PendingCloudAnalysisEntity) {
        postNotification(
            pending,
            title = "Cloud AI analysis complete",
            text = "Your queued image analysis is ready. Open the case to review.",
        )
    }

    private fun postFailureNotification(pending: PendingCloudAnalysisEntity) {
        postNotification(
            pending,
            title = "Cloud AI analysis failed",
            text = "Automatic retry exhausted. Try again manually from the case.",
        )
    }

    private fun postNotification(
        pending: PendingCloudAnalysisEntity,
        title: String,
        text: String,
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cloud AI Analysis",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        manager.notify(pending.id.hashCode(), notification)
    }

    private fun PendingCloudAnalysisEntity.toAiRequest(): AiRequest = AiRequest(
        headline = species?.let { "Species: $it" } ?: "Unspecified species",
        findings = buildString {
            append(
                "Photograph of the animal's presenting condition submitted " +
                    "for AI image analysis.",
            )
            clinicalContext?.let { contextText -> append(" $contextText") }
        },
        imageBase64 = imageBase64,
        clinicalContext = clinicalContext?.trim()?.takeIf { it.isNotEmpty() },
    )

    private companion object {
        const val KEY_WORK_ID = "workId"
        const val CHANNEL_ID = "cloud_analysis"
        const val MAX_RETRIES = 5
    }
}
