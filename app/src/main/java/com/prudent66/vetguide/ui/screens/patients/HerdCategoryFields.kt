package com.prudent66.vetguide.ui.screens.patients

import androidx.compose.runtime.Composable
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.ui.components.ClinicalSpeciesField

@Composable
internal fun HerdCategoryAndSpeciesFields(form: HerdFormState) {
    ClinicalSpeciesField(
        category = form.animalCategory,
        selectedDomestic = form.domesticSpecies,
        selectedWildlife = form.wildlifeGroup,
        onDomestic = { species ->
            form.speciesName = ClinicalSpeciesCatalog.label(species)
            form.wildlifeGroup = null
        },
        onWildlife = { group ->
            form.wildlifeGroup = group
            form.speciesName = group
        },
    )
}
