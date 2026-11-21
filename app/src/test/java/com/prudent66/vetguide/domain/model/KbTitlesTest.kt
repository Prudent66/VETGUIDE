package com.prudent66.vetguide.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Legacy seed titles must render without review markers. */
class KbTitlesTest {

    @Test
    fun `parenthesised placeholder markers are stripped`() {
        assertEquals("Bovine Helminthosis", "Bovine Helminthosis (Placeholder)".cleanKbTitle())
        assertEquals("Anthrax (Multi-species)", "Anthrax (Multi-species, Placeholder)".cleanKbTitle())
    }

    @Test
    fun `comma placeholder suffixes are stripped`() {
        assertEquals("Anthrax", "Anthrax, Placeholder".cleanKbTitle())
        assertEquals("Anthrax", "Anthrax , placeholder".cleanKbTitle())
    }

    @Test
    fun `clean titles are unchanged`() {
        assertEquals("Canine Parvovirus", "Canine Parvovirus".cleanKbTitle())
    }
}
