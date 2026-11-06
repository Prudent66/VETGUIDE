package com.prudent66.vetguide.domain.model

/** Summary row for the KB search/list screen. */
data class KbConditionSummary(
    val id: String,
    val name: String,
    val species: List<Species>,
    val urgency: KbUrgency?,
)
