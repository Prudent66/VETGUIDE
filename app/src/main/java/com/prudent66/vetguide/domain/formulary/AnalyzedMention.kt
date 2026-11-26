package com.prudent66.vetguide.domain.formulary

/**
 * Parses medication mentions out of assessment text so a concrete drug, dose
 * rate and route can be offered in the dosage picker even when it is not part
 * of the offline formulary. Domestic and wild callers pass their own isolated
 * dictionaries; no product, dose or warning is shared between pathways.
 */

data class AnalyzedMention(
    val name: String,
    val doseRange: DoseRange? = null,
    val route: DoseRoute? = null,
    val frequency: String = "",
    /** Sentence the mention came from, so a strength-aware product can be resolved. */
    val context: String = "",
)

private val strengthSuffixRegex = Regex(
    """(?:\s+\d+(?:[.,]\d+)?\s*%)|(?:\s+LA\b)|(?:\s*\([^)]*\))""",
    RegexOption.IGNORE_CASE,
)
private val separatorRegex = Regex("""\s*/\s*|\s{2,}""")

private val doseRegex = Regex(
    """(\d+(?:[.,]\d+)?)\s*(?:[-–to]{1,3}\s*(\d+(?:[.,]\d+)?)\s*)?mg\s*/\s*kg""",
    RegexOption.IGNORE_CASE,
)
private val abbreviatedRouteRegex = Regex(
    """\b(?:po|im|iv|sc)\b|\bi\.[mvs]\.\b|\bs\.c\.\b""",
    RegexOption.IGNORE_CASE,
)
private val frequencyRegex = Regex(
    """\b(?:once daily|twice daily|every \d+ hours?|bid|tid|sid|q\d+\s*h|for \d+ days?|daily)\b""",
    RegexOption.IGNORE_CASE,
)
private val genericMedicationRegex = Regex(
    """\b[A-Z][A-Za-z-]*(?:\s+[A-Za-z-]+){0,2}\s+""" +
        """(?i:ointment|oil|spray|solution|drops|cream|injection|drench)\b""",
)

/** A clause that only warns against a medicine is never a recommendation. */
internal val warningMarkers = listOf(
    "do not use", "don't use", "do not administer", "do not give", "don't give",
    "avoid", "contraindicated", "contraindication", "not recommended",
    "never use", "must not", "should not be used", "unless", "caution",
    "withhold", "discontinue", "not for use", "not advisable",
)

/** Wording that makes two assessed medicines interchangeable choices. */
private val alternativeMarkers = listOf(
    " or ", "alternatively", "either ", "instead", "if not", "if no improvement",
    "second-line", "second line", "or else",
)

/** Findings and recommendations are often separate sentences in one narrative. */
private val clauseRegex = Regex("""(?<=[.;])\s+|\n+""")

/** Strips strength/category suffixes so "Ivermectin" matches "Ivermectin 1%". */
fun normalizedDrugName(name: String): String =
    name.replace(strengthSuffixRegex, "")
        .replace(separatorRegex, " ")
        .trim()
        .lowercase()

/** True when two labels describe the same medication regardless of suffix. */
fun namesMatch(first: String, second: String): Boolean =
    normalizedDrugName(first) == normalizedDrugName(second)

/** Window after a mention where an explicit dose/route/frequency may appear. */
private fun windowAfterMention(text: String, name: String): String {
    val index = text.indexOf(name)
    if (index < 0) return ""
    val start = index + name.length
    return text.substring(start, minOf(text.length, start + 90))
}

/** Reads an explicit mg/kg rate ("50 mg/kg", "5-10 mg/kg") from the window. */
private fun doseRangeFrom(window: String): DoseRange? =
    doseRegex.find(window)?.let { match ->
        val low = match.groupValues[1].replace(',', '.').toDoubleOrNull()
        val high = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?.replace(',', '.')?.toDoubleOrNull()
        low?.let { DoseRange(it, high?.takeIf { value -> value > it } ?: it) }
    }

/** Derives a route from explicit route words or abbreviations. */
private fun routeFrom(window: String): DoseRoute? {
    val short = abbreviatedRouteRegex.find(window)?.value?.lowercase()
    return when {
        listOf(
            "topical", "ointment", "oil", "spray", "solution", "ophthalmic",
            "eye drop", "cream", "liniment",
        ).any { it in window } -> DoseRoute.TOPICAL
        "bath" in window || "water" in window -> DoseRoute.WATER
        "dart" in window -> DoseRoute.DART
        "oral" in window || short == "po" -> DoseRoute.ORAL
        short == "im" || "intramuscular" in window -> DoseRoute.IM
        short == "iv" || "intravenous" in window -> DoseRoute.IV
        short == "sc" || "subcut" in window -> DoseRoute.SC
        else -> null
    }
}

/**
 * Medication names from findings, differentials and prescriptions. Known names
 * win first; a capitalized topical/injection phrase is accepted as a fallback.
 */
fun medicationMentions(text: String, dictionary: List<String>): List<AnalyzedMention> {
    val lower = text.lowercase()
    val known = dictionary.filter { it.lowercase() in lower }
    val generic = genericMedicationRegex.findAll(text)
        .map { it.value.trim() }
        .filter { phrase -> dictionary.none { it.lowercase() in phrase.lowercase() } }
        .filter { it.length <= 60 }
    return (known + generic)
        .distinctBy(::normalizedDrugName)
        .map { name ->
            val window = windowAfterMention(lower, name.lowercase())
            val topicalName = listOf("ointment", "oil", "spray", "drench", "injection")
                .any { it in name.lowercase() }
            AnalyzedMention(
                name = name,
                doseRange = doseRangeFrom(window),
                route = routeFrom(window) ?: if (topicalName) DoseRoute.TOPICAL else null,
                frequency = frequencyRegex.find(window)?.value
                    ?.replaceFirstChar { it.uppercase() }.orEmpty(),
                context = text,
            )
        }
}

/** True when a clause only forbids or cautions against a medication. */
internal fun isWarningClause(clause: String): Boolean {
    val lower = clause.lowercase()
    return warningMarkers.any { it in lower }
}

/** Clauses that may safely drive drug selection — warnings are kept out. */
internal fun actionableClauses(text: String): List<String> =
    clauseRegex.split(text)
        .map { it.trim() }
        .filter { it.isNotBlank() && !isWarningClause(it) }

/**
 * Every actionable medication in an assessment. Drugs mentioned only in a
 * do-not-use/contraindication clause are dropped so they can never be
 * auto-selected or calculated as active treatments.
 */
fun recommendedMedicationMentions(
    text: String,
    dictionary: List<String>,
): List<AnalyzedMention> =
    actionableClauses(text)
        .flatMap { clause -> medicationMentions(clause, dictionary) }
        .distinctBy { mention -> normalizedDrugName(mention.name) }

/**
 * True when assessed medicines are interchangeable "or" options rather than
 * stacked treatments. The marker may sit in the same clause or in a later
 * conditional clause ("if no improvement, ...").
 */
fun hasReplaceableAlternatives(mentions: List<AnalyzedMention>): Boolean {
    if (mentions.size < 2) return false
    return mentions.any { mention ->
        val clause = mention.context.lowercase()
        alternativeMarkers.any { it in clause }
    }
}
