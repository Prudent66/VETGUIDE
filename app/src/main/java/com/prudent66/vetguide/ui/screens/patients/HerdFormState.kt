package com.prudent66.vetguide.ui.screens.patients

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prudent66.vetguide.domain.formulary.HerdSnapshot
import com.prudent66.vetguide.domain.formulary.formulary
import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.domain.model.Species

/** Screen-scoped editable state for one herd/pen/flock unit. */
@Stable
class HerdFormState {
    var unitName by mutableStateOf("")
    var animalCategory by mutableStateOf(AnimalCategory.DOMESTIC)
    var speciesName by mutableStateOf(ClinicalSpeciesCatalog.first(AnimalCategory.DOMESTIC))
    var wildlifeGroup by mutableStateOf<String?>(null)
    var headcountText by mutableStateOf("")
    var sickText by mutableStateOf("")
    var deadText by mutableStateOf("")
    var weightText by mutableStateOf("")
    var drugId by mutableStateOf(formulary.first().id)
    var bottleSizeText by mutableStateOf("100")

    val domesticSpecies: Species
        get() = ClinicalSpeciesCatalog.domesticSpecies(speciesName) ?: Species.CATTLE

    /** Auto-selects the first species of the newly chosen pathway. */
    fun switchCategory(category: AnimalCategory) {
        if (category == animalCategory) return
        animalCategory = category
        speciesName = ClinicalSpeciesCatalog.first(category)
        wildlifeGroup = if (category == AnimalCategory.WILD) speciesName else null
    }

    fun snapshot(): HerdSnapshot = HerdSnapshot(
        unitName = unitName.trim(),
        species = if (animalCategory == AnimalCategory.WILD) Species.OTHER else domesticSpecies,
        headcount = headcountText.toIntOrNull() ?: 0,
        sickCount = sickText.toIntOrNull() ?: 0,
        deadCount = deadText.toIntOrNull() ?: 0,
        averageWeightKg = weightText.toDoubleOrNull() ?: 0.0,
    )
}
