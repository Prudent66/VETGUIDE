package com.prudent66.vetguide.ui.screens.patients

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prudent66.vetguide.domain.formulary.HerdSnapshot
import com.prudent66.vetguide.domain.formulary.formulary
import com.prudent66.vetguide.domain.model.Species

/** Screen-scoped editable state for one herd/pen/flock unit. */
@Stable
class HerdFormState {
    var unitName by mutableStateOf("")
    var speciesName by mutableStateOf(Species.CATTLE.name)
    var headcountText by mutableStateOf("")
    var sickText by mutableStateOf("")
    var deadText by mutableStateOf("")
    var weightText by mutableStateOf("")
    var drugId by mutableStateOf(formulary.first().id)
    var bottleSizeText by mutableStateOf("100")

    fun snapshot(): HerdSnapshot = HerdSnapshot(
        unitName = unitName.trim(),
        species = runCatching { Species.valueOf(speciesName) }.getOrNull() ?: Species.CATTLE,
        headcount = headcountText.toIntOrNull() ?: 0,
        sickCount = sickText.toIntOrNull() ?: 0,
        deadCount = deadText.toIntOrNull() ?: 0,
        averageWeightKg = weightText.toDoubleOrNull() ?: 0.0,
    )
}
