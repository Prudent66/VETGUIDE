package com.prudent66.vetguide.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.Species
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented Room tests for the PatientDao (roadmap §13, phase 1 acceptance). */
@RunWith(AndroidJUnit4::class)
class PatientDaoTest {

    private lateinit var db: VetguideDatabase
    private lateinit var dao: PatientDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VetguideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.patientDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newPatient(
        id: String = UUID.randomUUID().toString(),
        name: String = "Rex",
        updatedAt: Long = 1_700_000_000_000L,
    ) = PatientEntity(
        id = id,
        name = name,
        species = Species.DOG,
        breed = "Labrador Retriever",
        sex = PatientSex.MALE,
        neuterStatus = NeuterStatus.NEUTERED,
        dateOfBirthEpochMs = 1_600_000_000_000L,
        estimatedAgeMonths = null,
        weightKg = 31.5,
        weightUpdatedAtEpochMs = updatedAt,
        ownerName = "A. Owner",
        ownerPhone = "+9200000000",
        ownerNotes = null,
        createdAtEpochMs = updatedAt - 1_000L,
        updatedAtEpochMs = updatedAt,
    )

    @Test
    fun insert_then_query_returns_all_fields() = runBlocking {
        val patient = newPatient()
        dao.insert(patient)

        val loaded = dao.getById(patient.id)

        assertNotNull(loaded)
        assertEquals(patient, loaded)
    }

    @Test
    fun update_is_visible_on_next_query() = runBlocking {
        val patient = newPatient()
        dao.insert(patient)

        dao.update(newPatient(id = patient.id, name = "Rexy", updatedAt = 1_700_000_001_000L))

        val loaded = dao.getById(patient.id)
        assertEquals("Rexy", loaded?.name)
        assertEquals(1_700_000_001_000L, loaded?.updatedAtEpochMs)
    }

    @Test
    fun deleteById_removes_the_row() = runBlocking {
        val patient = newPatient()
        dao.insert(patient)

        assertEquals(1, dao.deleteById(patient.id))

        assertNull(dao.getById(patient.id))
    }

    @Test
    fun observeAll_emits_recently_updated_rows_first() = runBlocking {
        dao.insert(newPatient(name = "Old", updatedAt = 1_700_000_000_000L))
        dao.insert(newPatient(name = "New", updatedAt = 1_700_000_005_000L))

        val names = dao.observeAll().first().map { it.name }

        assertEquals(listOf("New", "Old"), names)
    }

    @Test
    fun observeById_reports_null_for_missing_row() = runBlocking {
        val missing = dao.observeById("nonexistent").first()
        assertNull(missing)
    }

    @Test
    fun empty_table_queries_return_empty_results() = runBlocking {
        assertTrue(dao.observeAll().first().isEmpty())
        assertNull(dao.getById("any"))
        assertFalse(dao.deleteById("any") > 0)
        assertTrue(dao.observeAll().first().isEmpty())
    }
}
