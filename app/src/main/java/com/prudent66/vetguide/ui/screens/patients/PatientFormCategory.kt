package com.prudent66.vetguide.ui.screens.patients

import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.domain.model.Species

/** Auto-selects the first species of the newly chosen pathway. */
internal fun PatientFormState.switchCategory(category: AnimalCategory) {
    if (category == animalCategory) return
    animalCategory = category
    breed = ""
    if (category == AnimalCategory.WILD) {
        wildlifeGroup = ClinicalSpeciesCatalog.first(AnimalCategory.WILD)
        species = Species.OTHER
    } else {
        wildlifeGroup = null
        species = ClinicalSpeciesCatalog.domesticSpecies(
            ClinicalSpeciesCatalog.first(AnimalCategory.DOMESTIC),
        ) ?: Species.CATTLE
    }
}
