package com.prudent66.vetguide.domain.model

/**
 * Case lifecycle per roadmap §11.1, extended to the five-state clinical
 * workflow. [PLAN_SET] and [RESOLVED] are legacy pre-Phase-8 values kept so
 * existing Room rows stay readable; the editor offers [selectable] only.
 */
enum class CaseStatus {
    OPEN,
    INVESTIGATING,
    RECOVERED,
    REFERRED,
    CLOSED,

    /** Legacy status from the original four-state workflow (read-only). */
    PLAN_SET,

    /** Legacy status from the original four-state workflow (read-only). */
    RESOLVED;

    /** Display label ("Open", "Investigating", …). */
    val label: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        /** States offered by the case editor (excludes the legacy values). */
        val selectable: List<CaseStatus> = listOf(OPEN, INVESTIGATING, RECOVERED, REFERRED, CLOSED)

        /** Maps a legacy row onto the nearest current workflow state. */
        fun editable(status: CaseStatus): CaseStatus = when (status) {
            PLAN_SET -> INVESTIGATING
            RESOLVED -> RECOVERED
            else -> status
        }
    }
}
