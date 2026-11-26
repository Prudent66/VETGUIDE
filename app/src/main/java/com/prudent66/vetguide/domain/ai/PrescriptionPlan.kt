package com.prudent66.vetguide.domain.ai

/**
 * Clinical split used by the results screen, the case view and the SOAP/PDF
 * exports: drug/injection prescriptions (with dosages) stay in their own
 * surface, while non-pharmacological field management remains supportive care.
 */
data class PrescriptionPlan(
    val prescriptions: List<String> = emptyList(),
    val supportiveCare: List<String> = emptyList(),
) {
    val hasPrescriptions: Boolean get() = prescriptions.isNotEmpty()
}

/** A number + unit ("500 mg", "10 ml", "1 tablet", …) anywhere in a line. */
private val DOSAGE_UNIT_REGEX = Regex(
    """\b\d+(?:[.,]\d+)?\s*(?:mg|ml|mcg|µg|g|iu|units?|tabs?|tablets?|caps?|capsules?|drops?|puffs?)\b""",
    RegexOption.IGNORE_CASE,
)

/** A weight-normalised dose ("50 mg/kg", "10 ml/kg") is a strong signal. */
private val WEIGHT_DOSE_REGEX = Regex(
    """\b\d+(?:[.,]\d+)?\s*(?:mg|ml|mcg|µg|iu)\s*/\s*kg\b""",
    RegexOption.IGNORE_CASE,
)

/** Route/frequency abbreviations that only make sense for drug therapy. */
private val ROUTE_REGEX = Regex(
    """\b(?:po|p\.o\.|sc|s\.c\.|sq|im|i\.m\.|iv|i\.v\.|orally?|oral|""" +
        """subcut(?:aneous(?:ly)?)?|intramuscular(?:ly)?|intravenous(?:ly)?|""" +
        """topical(?:ly)?|bid|tid|sid|q\d+\s*h)\b""",
    RegexOption.IGNORE_CASE,
)

/** Injectable/drug/medication vocabulary (including common field drug stems). */
private val MEDICATION_REGEX = Regex(
    """\b(?:inject(?:ion|able|ed)?|prescri(?:be|ption)|dose|dosage|drug|medication|""" +
        """antibiotic|anthelmintic|antiparasitic|deworm(?:er|ing)?|vaccin(?:e|ate|ation)|""" +
        """tablet|capsule|syrup|suspension|ointment|cream|""" +
        """\w+(?:cillin|mycin|cycline|floxacin|azole|profen|oxicam|sone|olone)|""" +
        """ivermectin|tylosin|penicillin|amoxicillin|enrofloxacin|meloxicam|""" +
        """metronidazole|doxycycline|praziquantel|albendazole|fenbendazole|""" +
        """dexamethasone|prednisolone|ketamine|xylazine|atropine|furosemide|""" +
        """gentamicin|ceftriaxone|sulfadimidine|trimethoprim|toltrazuril|""" +
        """amprolium|closantel|levamisole|doramectin|moxidectin|selamectin)\b""",
    RegexOption.IGNORE_CASE,
)

private val SENTENCE_BOUNDARY_REGEX = Regex("""(?<=[.;])\s+""")
private val BULLET_PREFIX_REGEX = Regex("""^[\s\-•*–—]+""")

/**
 * True when a single plan line describes a drug/injection prescription rather
 * than supportive care (isolation, hygiene, feeding, follow-up, …).
 */
internal fun isPrescriptionLine(line: String): Boolean {
    val text = line.trim()
    if (text.length < 3) return false
    return WEIGHT_DOSE_REGEX.containsMatchIn(text) ||
        MEDICATION_REGEX.containsMatchIn(text) ||
        (DOSAGE_UNIT_REGEX.containsMatchIn(text) && ROUTE_REGEX.containsMatchIn(text))
}

/** A do-not-use clause is a safety note, never an active prescription. */
internal fun isWarningLine(line: String): Boolean {
    val lower = line.lowercase()
    return listOf(
        "do not use", "don't use", "do not administer", "avoid", "contraindicated",
        "not recommended", "never use", "should not be used", "unless", "caution",
    ).any { it in lower }
}

/**
 * Pulls prescription sentences out of free-form narrative (offline fallback or
 * legacy saved analyses that only carried a single text blob).
 */
fun extractPrescriptionLines(text: String): List<String> =
    text.lineSequence()
        .flatMap { line -> line.split(SENTENCE_BOUNDARY_REGEX) }
        .map { it.replace(BULLET_PREFIX_REGEX, "").trim() }
        .filter { isPrescriptionLine(it) }
        .map { sanitizeAiItem(it) }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

/** First condition name inside a narrative's suspected-conditions clause. */
private val narrativeConditionRegex = Regex(
    """Suspected conditions \(decision support, not a diagnosis\):\s*([^.;(]+)""",
    RegexOption.IGNORE_CASE,
)

/** Top suspected condition when only a narrative is available (offline tier). */
fun firstNarrativeCondition(narrative: String): String? =
    narrativeConditionRegex.find(narrative)?.groupValues?.getOrNull(1)?.trim()

/** A full "Suspected conditions ..." clause in a vet-facing narrative. */
private val SUSPECTED_CONDITIONS_CLAUSE = Regex(
    """Suspected conditions \(decision support, not a diagnosis\):[^.]*\.""",
    RegexOption.IGNORE_CASE,
)

/**
 * Older saved analyses sometimes repeat the whole suspected-conditions block
 * inside the narrative. Keep the first occurrence and drop the rest so the
 * case record lists each condition once.
 */
fun withoutDuplicateSuspectedConditions(narrative: String): String {
    if (SUSPECTED_CONDITIONS_CLAUSE.findAll(narrative).count() < 2) return narrative
    var seen = 0
    return SUSPECTED_CONDITIONS_CLAUSE.replace(narrative) { match ->
        if (seen++ == 0) match.value else ""
    }.replace(Regex("""\s{2,}"""), " ").trim()
}

/** Start of the redundant free-form next-steps clause in a legacy narrative. */
private val NEXT_STEPS_START_REGEX = Regex(
    """(?:recommended\s+)?next\s+steps\s*:""",
    RegexOption.IGNORE_CASE,
)

/**
 * Narrative text for the top assessment summary. When the structured
 * "Supportive field care suggested:" list is rendered separately below it,
 * the free-form "Recommended next steps: ..." clause is redundant — drop just
 * that clause, never findings or differential text.
 */
fun withoutNextSteps(narrative: String): String {
    val start = NEXT_STEPS_START_REGEX.find(narrative)?.range?.first ?: return narrative
    val period = narrative.indexOf('.', start)
    val end = if (period < 0) narrative.length else period + 1
    val before = narrative.substring(0, start).trim()
    val after = narrative.substring(end).trim()
    return listOf(before, after).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Every medication-bearing part of an assessment, flattened for drug matching.
 * Findings and differentials often carry dose instructions that never appear
 * in the structured prescription list, so the dosage picker must see them too.
 */
fun analysisNarrative(
    data: AiAnalysisData? = null,
    narrative: String? = null,
): String {
    val structured = data?.let {
        it.findings + it.suspectedConditions.map { condition -> condition.name } +
            it.recommendedNextSteps + it.prescriptions
    }.orEmpty()
    return (structured + listOfNotNull(narrative))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")
}

/**
 * Builds the dedicated prescription plan for a structured analysis. When the
 * backend supplied explicit `prescriptions` they win; otherwise drug lines are
 * partitioned out of the legacy `recommendedNextSteps` so no dosage is lost or
 * duplicated. Narrative-only results (offline mode) are scanned as a fallback.
 */
fun buildPrescriptionPlan(
    analysisData: AiAnalysisData?,
    narrative: String? = null,
): PrescriptionPlan {
    val explicit = analysisData?.prescriptions.orEmpty()
        .map { sanitizeAiItem(it) }
        .filter { it.isNotEmpty() && !isWarningLine(it) }
        .distinct()
    val steps = analysisData?.recommendedNextSteps.orEmpty()
        .map { sanitizeAiItem(it) }
        .filter { it.isNotEmpty() && !isWarningLine(it) }
    val extractedFromSteps = steps.filter { isPrescriptionLine(it) }
    return PrescriptionPlan(
        prescriptions = explicit.ifEmpty {
            if (steps.isNotEmpty()) extractedFromSteps else extractPrescriptionLines(narrative.orEmpty())
        },
        supportiveCare = if (explicit.isNotEmpty()) steps else steps.filterNot(::isPrescriptionLine),
    )
}
