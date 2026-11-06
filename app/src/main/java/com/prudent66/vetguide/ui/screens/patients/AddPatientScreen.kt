package com.prudent66.vetguide.ui.screens.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.ui.viewmodels.PatientViewModel
import kotlin.math.roundToInt

/**
 * Add Patient form (Phase 1B): Name, Species, Breed, Age, Sex, Weight.
 * Saves through the PatientViewModel and pops back on success.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatientScreen(
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: PatientViewModel = hiltViewModel(),
) {
    val form = remember { PatientFormState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add patient") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.nav_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PatientTextFields(form)
            ChoiceRow(
                label = "Species",
                options = listOf("Dog", "Cat"),
                selectedIndex = if (form.species == Species.DOG) 0 else 1,
                onSelect = { index -> form.species = if (index == 0) Species.DOG else Species.CAT },
            )
            ChoiceRow(
                label = "Sex",
                options = listOf("Female", "Male", "Unknown"),
                selectedIndex = form.sex.ordinal,
                onSelect = { index -> form.sex = PatientSex.entries[index] },
            )
            Button(
                onClick = {
                    viewModel.addPatient(
                        name = form.name.trim(),
                        species = form.species,
                        breed = form.breed.trim().ifBlank { null },
                        sex = form.sex,
                        ageMonths = form.ageMonths,
                        weightKg = form.weightKg,
                        onSaved = onSaved,
                    )
                },
                enabled = form.valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save patient") }
            Text(
                text = "Owner details are optional and can be added later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Screen-scoped form state + validation (kept out of the ViewModel on purpose). */
@Stable
class PatientFormState(
    species: Species = Species.DOG,
    sex: PatientSex = PatientSex.UNKNOWN,
) {
    var name: String by mutableStateOf("")
    var breed: String by mutableStateOf("")
    var ageText: String by mutableStateOf("")
    var weightText: String by mutableStateOf("")
    var species: Species by mutableStateOf(species)
    var sex: PatientSex by mutableStateOf(sex)

    private val ageYears: Double? get() = ageText.toDoubleOrNull()

    private val ageIsValid: Boolean
        get() = ageText.isBlank() || (ageYears != null && ageYears >= 0.0)

    private val weightIsValid: Boolean
        get() = weightText.isBlank() || weightKg != null

    val valid: Boolean
        get() = name.isNotBlank() && ageIsValid && weightIsValid

    val ageMonths: Int?
        get() = ageYears?.let { (it * 12).roundToInt().takeIf { months -> months > 0 } }

    val weightKg: Double?
        get() = weightText.toDoubleOrNull()?.takeIf { it >= 0.0 }
}

@Composable
private fun PatientTextFields(form: PatientFormState) {
    OutlinedTextField(
        value = form.name,
        onValueChange = { form.name = it },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.breed,
        onValueChange = { form.breed = it },
        label = { Text("Breed") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.ageText,
        onValueChange = { form.ageText = it },
        label = { Text("Age (years)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.weightText,
        onValueChange = { form.weightText = it },
        label = { Text("Weight (kg)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            if (index == selectedIndex) {
                Button(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(option)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(index) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) { Text(option) }
            }
        }
    }
}
