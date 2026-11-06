package com.prudent66.vetguide.domain.model

/** Full condition view: overview + linked symptoms + references. */
data class KbConditionDetail(
    val id: String,
    val name: String,
    val species: List<Species>,
    val urgency: KbUrgency?,
    val overview: String,
    val symptoms: List<Symptom>,
    val references: List<KbReference>,
)

data class Symptom(
    val id: String,
    val name: String,
    val bodySystem: String,
)

/** A KB citation. Note is a safety/placeholder annotation, never clinical content. */
data class KbReference(
    val id: String,
    val title: String,
    val note: String,
)
