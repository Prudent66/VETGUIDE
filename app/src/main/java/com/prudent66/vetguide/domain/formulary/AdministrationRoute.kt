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
    /**
     * Explicit eye-preparation or eye-administration wording only. A bare
     * "eye" anywhere in a clause is deliberately not enough: a dermal ointment
     * on a facial/head/muzzle lesion must stay a wound-care topical.
     */
    private val ocularInstruction = Regex(
        """\b(?:ophthalmic|ocular|conjunctival|corneal|""" +
            """eye\s*(?:drops?|ointment|solution|preparation|instillation)|""" +
            """(?:in|into|to|onto)\s+(?:the\s+)?(?:affected\s+)?eyes?|both\s+eyes?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val topicalCues = listOf(
        "apply to", "on the skin", "to the wound", "to the lesion", "topical",
    )
    private val oralCues = listOf("oral", "by mouth")
    private val waterCues = listOf("bath", "in water")
    private val topicalClasses = listOf(
        "ointment", "oil", "spray", "cream", "liniment", "solution", "drops",
    )

    /** Preparations applied as a layer or ribbon rather than drawn into a syringe. */
    internal val semisolidPreparationTerms =
        listOf("ointment", "cream", "paste", "gel", "salve", "powder")

    /** Liquid preparations keep mL/drop/bottle framing even on an eye route. */
    internal val liquidPreparationTerms =
        listOf("drop", "solution", "liquid", "suspension")

    /** Semisolid wording only when no liquid preparation is named in the same text. */
    internal fun semisolidPreparation(text: String): Boolean {
        val lower = text.lowercase()
        val liquid = liquidPreparationTerms.any { it in lower }
        return !liquid && semisolidPreparationTerms.any { it in lower }
    }

    /**
     * Keeps the assessed preparation on synthetic topical products so cards and
     * pickers still know an ointment from a solution when the drug name alone
     * carries only the (ophthalmic)/(wound care) context label.
     */
    internal fun preparationDisplayName(
        mention: AnalyzedMention,
        stripWild: Boolean = false,
    ): String {
        val base = if (stripWild) mention.name.removeSuffix(" (wild)") else mention.name
        val lowerBase = base.lowercase()
        val lowerContext = mention.context.lowercase()
        val eligible = mention.route == DoseRoute.TOPICAL &&
            semisolidPreparationTerms.none { it in lowerBase } &&
            semisolidPreparation(mention.context)
        val preparation = if (eligible) {
            semisolidPreparationTerms.firstOrNull { it in lowerContext }
        } else {
            null
        }
        return if (preparation == null) base else "$base $preparation"
    }

    /** Route the clinician actually wrote, before any product or class default. */
    fun explicit(text: String): DoseRoute? {
        val lower = text.lowercase()
        val short = abbreviations.find(lower)?.value?.replace(".", "")
        val contains = { cues: List<String> -> cues.any { it in lower } }
        val rules = listOf(
            (short == "sc" || contains(listOf("subcut"))) to DoseRoute.SC,
            (short == "im" || contains(listOf("intramuscular"))) to DoseRoute.IM,
            (short == "iv" || contains(listOf("intravenous"))) to DoseRoute.IV,
            (ocularInstruction.containsMatchIn(text) || contains(topicalCues)) to DoseRoute.TOPICAL,
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

    /** True only for an explicit eye preparation or eye-administration instruction. */
    fun ocular(text: String): Boolean = ocularInstruction.containsMatchIn(text)

    /**
     * Tight evidence for an eye preparation: the medication name plus the
     * administration window right after it. Findings elsewhere in the clause
     * (facial/head/muzzle lesions) must not turn a dermal ointment into an
     * ophthalmic one.
     */
    internal fun ocularEvidence(mention: AnalyzedMention): String {
        val lower = mention.context.lowercase()
        val name = mention.name.lowercase()
        return "$name ${windowAfterMention(lower, name)}"
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
