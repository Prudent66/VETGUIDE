package com.prudent66.vetguide.domain.model

/**
 * Cleans legacy seed titles that still carry review markers, e.g.
 * "Bovine Helminthosis (Placeholder)" or
 * "Anthrax (Multi-species, Placeholder)". Applied at the repository boundary
 * so both freshly seeded rows and databases seeded before the v2 payload
 * render cleanly.
 */
fun String.cleanKbTitle(): String = this
    .replace(COMMA_PLACEHOLDER, "")
    .replace(PAREN_PLACEHOLDER, "")
    .trim()

private val COMMA_PLACEHOLDER = Regex(""",\s*Placeholder""", RegexOption.IGNORE_CASE)
private val PAREN_PLACEHOLDER = Regex("""\s*\(\s*Placeholder\s*\)""", RegexOption.IGNORE_CASE)
