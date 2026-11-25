package com.prudent66.vetguide.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build 22: about copy wraps naturally and carries no provider jargon. */
class AboutVetguideScreenTest {

    private val copy = listOf(
        ABOUT_OVERVIEW,
        ABOUT_CAPABILITIES,
        ABOUT_HOW_TO,
        ABOUT_RESPONSIBILITY,
    )

    @Test
    fun `paragraphs are unbroken so Compose wraps across the full width`() {
        copy.forEach { text ->
            val paragraphs = text.trim().split("\n").filter { it.isNotBlank() }
            assertTrue(paragraphs.isNotEmpty())
            paragraphs.forEach { paragraph ->
                assertTrue(paragraph.trim().length > 40)
                assertTrue(paragraph.trim().endsWith("."))
            }
        }
    }

    @Test
    fun `numbered steps stay on separate lines`() {
        val steps = ABOUT_HOW_TO.trim().split("\n").filter { it.isNotBlank() }
        assertEquals(5, steps.size)
        steps.forEachIndexed { index, step ->
            assertTrue(step.startsWith("Step ${index + 1} —"))
        }
    }

    @Test
    fun `guide copy contains no user facing provider jargon`() {
        copy.forEach { text ->
            assertFalse(text.contains(Regex("""\bAI\b""")))
            assertFalse(text.contains("Cloud analysis"))
            assertFalse(text.contains("provider"))
        }
    }
}
