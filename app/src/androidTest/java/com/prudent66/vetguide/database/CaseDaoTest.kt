package com.prudent66.vetguide.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prudent66.vetguide.domain.model.CaseArtifactType
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.Species
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room tests for the CaseDao and CaseArtifactDao, including
 * foreign-key integrity (patient → case → artifact chain, roadmap §11.1).
 */
@RunWith(AndroidJUnit4::class)
class CaseDaoTest {

    private val now = 1_700_000_000_000L

    private lateinit var db: VetguideDatabase
    private lateinit var patientDao: PatientDao
    private lateinit var caseDao: CaseDao
    private lateinit var artifactDao: CaseArtifactDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VetguideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        patientDao = db.patientDao()
        caseDao = db.caseDao()
        artifactDao = db.caseArtifactDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun patient(id: String) = PatientEntity(
        id = id,
        name = "Patient $id",
        species = Species.CAT,
        breed = null,
        sex = PatientSex.FEMALE,
        neuterStatus = NeuterStatus.INTACT,
        dateOfBirthEpochMs = null,
        estimatedAgeMonths = 24,
        weightKg = 4.2,
        weightUpdatedAtEpochMs = now,
        ownerName = null,
        ownerPhone = null,
        ownerNotes = null,
        createdAtEpochMs = now,
        updatedAtEpochMs = now,
    )

    private fun case(
        id: String,
        patientId: String,
        status: CaseStatus = CaseStatus.OPEN,
        updatedAt: Long = now,
    ) = CaseEntity(
        id = id,
        patientId = patientId,
        presentingComplaint = "Vomiting for two days",
        status = status,
        createdAtEpochMs = now,
        updatedAtEpochMs = updatedAt,
    )

    @Test
    fun insert_case_then_query_returns_it() = runBlocking {
        val patientId = UUID.randomUUID().toString()
        patientDao.insert(patient(patientId))
        val caseId = UUID.randomUUID().toString()

        caseDao.insert(case(caseId, patientId))

        val loaded = caseDao.getById(caseId)
        assertNotNull(loaded)
        assertEquals(caseId, loaded?.id)
    }

    @Test
    fun update_case_fields_stick() = runBlocking {
        val patientId = UUID.randomUUID().toString()
        patientDao.insert(patient(patientId))
        val caseId = UUID.randomUUID().toString()
        caseDao.insert(case(caseId, patientId))

        caseDao.update(case(caseId, patientId, updatedAt = now + 10_000L))

        assertEquals(
            now + 10_000L,
            caseDao.getById(caseId)?.updatedAtEpochMs,
        )
    }

    @Test
    fun updateStatus_changes_status_and_timestamp() = runBlocking {
        val patientId = UUID.randomUUID().toString()
        patientDao.insert(patient(patientId))
        val caseId = UUID.randomUUID().toString()
        caseDao.insert(case(caseId, patientId))

        val changed = caseDao.updateStatus(caseId, CaseStatus.INVESTIGATING.name, now + 500L)

        assertEquals(1, changed)
        val loaded = caseDao.getById(caseId)
        assertEquals(CaseStatus.INVESTIGATING, loaded?.status)
        assertEquals(now + 500L, loaded?.updatedAtEpochMs)
    }

    @Test
    fun observeByPatient_returns_only_that_patients_cases_newest_first() = runBlocking {
        val patientId = UUID.randomUUID().toString()
        patientDao.insert(patient(patientId))
        caseDao.insert(case("c1", patientId, updatedAt = now))
        caseDao.insert(case("c2", patientId, updatedAt = now + 1_000L))
        val otherPatientId = UUID.randomUUID().toString()
        patientDao.insert(patient(otherPatientId))
        caseDao.insert(case("c3", otherPatientId, updatedAt = now + 2_000L))

        val ids = caseDao.observeByPatient(patientId).first().map { it.id }

        assertEquals(listOf("c2", "c1"), ids)
    }

    @Test
    fun deleting_patient_cascades_to_cases() = runBlocking {
        val patientId = UUID.randomUUID().toString()
        patientDao.insert(patient(patientId))
        val caseId = UUID.randomUUID().toString()
        caseDao.insert(case(caseId, patientId))

        patientDao.deleteById(patientId)

        assertNull(caseDao.getById(caseId))
        assertTrue(caseDao.observeAll().first().isEmpty())
    }

    @Test
    fun artifact_flow_case_timeline_in_chronological_order() = runBlocking {
        val patientId = UUID.randomUUID().toString()
        patientDao.insert(patient(patientId))
        val caseId = UUID.randomUUID().toString()
        caseDao.insert(case(caseId, patientId))
        artifactDao.insert(artifact("a2", caseId, createdAt = now + 2_000L))
        artifactDao.insert(artifact("a1", caseId, createdAt = now))
        artifactDao.insert(artifact("a3", caseId, createdAt = now + 3_000L))

        val ids = artifactDao.observeByCase(caseId).first().map { it.id }

        assertEquals(listOf("a1", "a2", "a3"), ids)
    }

    @Test
    fun deleting_case_cascades_to_artifacts() = runBlocking {
        val patientId = UUID.randomUUID().toString()
        patientDao.insert(patient(patientId))
        val caseId = UUID.randomUUID().toString()
        caseDao.insert(case(caseId, patientId))
        artifactDao.insert(artifact("a1", caseId, createdAt = now))

        caseDao.deleteById(caseId)

        assertNull(artifactDao.getById("a1"))
    }

    @Test
    fun deleting_artifact_removes_only_that_artifact() = runBlocking {
        val patientId = UUID.randomUUID().toString()
        patientDao.insert(patient(patientId))
        val caseId = UUID.randomUUID().toString()
        caseDao.insert(case(caseId, patientId))
        artifactDao.insert(artifact("a1", caseId, createdAt = now))
        artifactDao.insert(artifact("a2", caseId, createdAt = now + 1_000L))

        assertEquals(1, artifactDao.deleteById("a1"))

        assertNull(artifactDao.getById("a1"))
        assertNotNull(artifactDao.getById("a2"))
    }

    private fun artifact(
        id: String,
        caseId: String,
        type: CaseArtifactType = CaseArtifactType.QUESTIONNAIRE,
        createdAt: Long,
    ) = CaseArtifactEntity(
        id = id,
        caseId = caseId,
        type = type,
        content = "{\"answers\":{\"appetite\":\"low\"}}",
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = createdAt,
    )
}
