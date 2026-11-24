package com.prudent66.vetguide.domain.model

/** Strict Domestic / Wild filtering shared by the KB screen and its tests. */
object KnowledgeFilters {

    /** True when any tag belongs to a wildlife group (not a domestic species). */
    fun isWildlife(tags: List<String>): Boolean =
        tags.any { tag -> ClinicalSpeciesCatalog.wildlifeGroup(tag) != null }

    fun categoryOf(tags: List<String>): AnimalCategory =
        if (isWildlife(tags)) AnimalCategory.WILD else AnimalCategory.DOMESTIC

    /** Filters a condition by the selected pathway and optional species/group tag. */
    fun matches(
        condition: KbConditionSummary,
        category: AnimalCategory,
        tag: String?,
    ): Boolean = categoryOf(condition.speciesTags) == category &&
        (
            tag.isNullOrBlank() ||
                condition.speciesTags.any { stored ->
                    stored.equals(tag, ignoreCase = true) ||
                        (
                            ClinicalSpeciesCatalog.wildlifeGroup(tag)?.let { selected ->
                                ClinicalSpeciesCatalog.wildlifeGroup(stored) == selected
                            } == true
                            )
                }
            )

    /** Filter options for a pathway: "All species" plus the pathway's species. */
    fun filterOptions(category: AnimalCategory): List<String> =
        listOf("All species") + ClinicalSpeciesCatalog.labels(category)
}
