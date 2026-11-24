package com.prudent66.vetguide.data.backup

import com.prudent66.vetguide.domain.model.AnimalCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/** Legacy rows/backups without a category must read back as DOMESTIC. */
class MigrationDefaultTest {

    @Test
    fun `legacy backup without category defaults to domestic`() {
        val backup = PatientBackup(
            id = "p1",
            name = "Old patient",
            species = "CATTLE",
            sex = "UNKNOWN",
            neuterStatus = "UNKNOWN",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )

        assertEquals(AnimalCategory.DOMESTIC, backup.toEntity().animalCategory)
    }

    @Test
    fun `wild category survives a backup round trip`() {
        val entity = PatientBackup(
            id = "p2",
            name = "Wild antelope",
            animalCategory = "WILD",
            species = "OTHER",
            sex = "UNKNOWN",
            neuterStatus = "UNKNOWN",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        ).toEntity()

        assertEquals(AnimalCategory.WILD, entity.toBackup().let {
            com.prudent66.vetguide.domain.model.AnimalCategory.valueOf(it.animalCategory)
        })
    }
}
