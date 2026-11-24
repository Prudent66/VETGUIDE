package com.prudent66.vetguide.domain.model

/** Summary row for the KB search/list screen. */
data class KbConditionSummary(
    val id: String,
    val name: String,
    val species: List<Species> = emptyList(),
    val urgency: KbUrgency? = null,
    /** Raw species tags from the seed, including wildlife group labels. */
    val speciesTags: List<String> = emptyList(),
)
