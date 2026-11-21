package com.prudent66.vetguide.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 8 case-workflow mapping (five editable states + legacy rows). */
class CaseStatusTest {

    @Test
    fun `editor offers the five-state workflow only`() {
        assertEquals(
            listOf(
                CaseStatus.OPEN,
                CaseStatus.INVESTIGATING,
                CaseStatus.RECOVERED,
                CaseStatus.REFERRED,
                CaseStatus.CLOSED,
            ),
            CaseStatus.selectable,
        )
    }

    @Test
    fun `legacy statuses map to the nearest current workflow state`() {
        assertEquals(CaseStatus.INVESTIGATING, CaseStatus.editable(CaseStatus.PLAN_SET))
        assertEquals(CaseStatus.RECOVERED, CaseStatus.editable(CaseStatus.RESOLVED))
        assertEquals(CaseStatus.REFERRED, CaseStatus.editable(CaseStatus.REFERRED))
    }

    @Test
    fun `labels are human readable`() {
        assertEquals("Open", CaseStatus.OPEN.label)
        assertEquals("Investigating", CaseStatus.INVESTIGATING.label)
        assertEquals("Recovered", CaseStatus.RECOVERED.label)
        assertEquals("Referred", CaseStatus.REFERRED.label)
        assertEquals("Closed", CaseStatus.CLOSED.label)
    }
}
