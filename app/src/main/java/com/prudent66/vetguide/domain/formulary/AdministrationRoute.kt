package com.prudent66.vetguide.domain.formulary

/**
 * Route resolution for medications extracted from a clinical plan. A route
 * explicitly stated in the plan always outranks a drug-class hint or a product
 * default. These are pure text helpers: no formulary product, dose or warning
 * is shared between the domestic and wildlife pathways.
 */
object AdministrationRoute {

    private val abbreviations = Regex(
        """\b(?:po|im|iv|sc)\b|\bi\.[mvs]\.\b|\bs\.c\.\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ocularCues = listOf(
        "ophthalmic", "ocular", "eye", "eyelid", "conjunctiv", "corneal",
    )
    private val topicalCues = ocularCues + listOf(
        "apply to", "on the skin", "to the wound", "to the lesion", "topical",
    )
    private val oralCues = listOf("oral", "by mouth")
    private val waterCues = listOf("bath", "in water")
    private val topicalClasses = listOf(
        "ointment", "oil", "spray", "cream", "liniment", "solution", "drops",
    )

    /** Route the clinician actually wrote, before any product or class default. */
    fun explicit(text: String): DoseRoute? {
        val lower = text.lowercase()
        val short = abbreviations.find(lower)?.value?.replace(".", "")
        val contains = { cues: List<String> -> cues.any { it in lower } }
        val rules = listOf(
            (short == "sc" || contains(listOf("subcut"))) to DoseRoute.SC,
            (short == "im" || contains(listOf("intramuscular"))) to DoseRoute.IM,
            (short == "iv" || contains(listOf("intravenous"))) to DoseRoute.IV,
            contains(topicalCues) to DoseRoute.TOPICAL,
            (short == "po" || contains(oralCues)) to DoseRoute.ORAL,
            contains(waterCues) to DoseRoute.WATER,
            contains(listOf("dart")) to DoseRoute.DART,
        )
        return rules.firstOrNull { it.first }?.second
    }

    /** Explicit cue first; only then fall back to the preparation type. */
    fun resolve(text: String): DoseRoute? {
        val lower = text.lowercase()
        return explicit(text) ?: when {
            "bath" in lower || "water" in lower -> DoseRoute.WATER
            "dart" in lower -> DoseRoute.DART
            topicalClasses.any { it in lower } -> DoseRoute.TOPICAL
            else -> null
        }
    }

    /** True only for explicit ocular involvement or eye administration. */
    fun ocular(text: String): Boolean {
        val lower = text.lowercase()
        return ocularCues.any { it in lower }
    }

    /**
     * Carries the assessed route, schedule and any explicit mg/kg rule onto a
     * resolved domestic product. The plan's rate replaces the preset range so
     * the narrative note and the calculated numbers share one dose rule.
     */
    fun apply(drug: FormularyDrug, mention: AnalyzedMention?): FormularyDrug =
        if (mention == null) drug else drug.copy(
            route = mention.route ?: drug.route,
            frequency = mention.frequency.ifBlank { drug.frequency },
            doseBySpecies = mention.doseRange?.let { range ->
                drug.doseBySpecies.mapValues { range }
            } ?: drug.doseBySpecies,
        )

    /** Same resolved dose rule for the independent wildlife formulary. */
    fun apply(
        drug: WildlifeFormularyEntry,
        mention: AnalyzedMention?,
    ): WildlifeFormularyEntry =
        if (mention == null) drug else drug.copy(
            route = mention.route ?: drug.route,
            frequency = mention.frequency.ifBlank { drug.frequency },
            doseByGroup = mention.doseRange?.let { range ->
                drug.doseByGroup.mapValues { range }
            } ?: drug.doseByGroup,
            doseByAnimal = mention.doseRange?.let { range ->
                drug.doseByAnimal.mapValues { range }
            } ?: drug.doseByAnimal,
        )
}
