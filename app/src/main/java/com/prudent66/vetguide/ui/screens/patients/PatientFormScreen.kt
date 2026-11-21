package com.prudent66.vetguide.ui.screens.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.AgeUnit
import com.prudent66.vetguide.domain.model.BreedCatalog
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.PatientDraft
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.ui.components.SpeciesField
import com.prudent66.vetguide.ui.viewmodels.PatientViewModel

/**
 * Add/Edit Patient form (Phase 1B + Phase 8): the add route starts blank; the
 * edit route pre-fills every form field from Room and saves in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientFormScreen(
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: PatientViewModel = hiltViewModel(),
) {
    val form = remember { PatientFormState() }
    val editingPatient by viewModel.editingPatient.collectAsState()
    var loadedPatientId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editingPatient) {
        val patient = editingPatient
        if (patient != null && loadedPatientId != patient.id) {
            form.load(patient)
            loadedPatientId = patient.id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit patient" else "Add patient") },
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
        if (viewModel.isEditing && editingPatient == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            PatientFormBody(
                form = form,
                viewModel = viewModel,
                onSaved = onSaved,
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun PatientFormBody(
    form: PatientFormState,
    viewModel: PatientViewModel,
    onSaved: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Species", style = MaterialTheme.typography.labelLarge)
        SpeciesField(
            selected = form.species,
            onSelect = { species ->
                val changed = species != form.species
                form.species = species
                if (changed) {
                    form.breed = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        PatientTextFields(form)
        BreedField(
            selected = form.breed,
            breeds = BreedCatalog.forSpecies(form.species),
            onSelect = { form.breed = it },
            modifier = Modifier.fillMaxWidth(),
        )
        ChoiceRow(
            label = "Sex",
            options = listOf("Female", "Male", "Unknown"),
            selectedIndex = form.sex.ordinal,
            onSelect = { index -> form.sex = PatientSex.entries[index] },
        )
        SaveButton(form, viewModel, onSaved)
        Text(
            text = "Owner details are optional and can be added later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SaveButton(
    form: PatientFormState,
    viewModel: PatientViewModel,
    onSaved: () -> Unit,
) {
    Button(
        onClick = {
            viewModel.savePatient(
                PatientDraft(
                    name = form.name.trim(),
                    species = form.species,
                    breed = form.breed.trim().ifBlank { null },
                    sex = form.sex,
                    ageValue = form.ageValue,
                    ageUnit = form.ageUnit,
                    weightKg = form.weightKg,
                ),
                onSaved = onSaved,
            )
        },
        enabled = form.valid,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (viewModel.isEditing) "Save changes" else "Save patient") }
}

/** Screen-scoped form state + validation (kept out of the ViewModel on purpose). */
@Stable
class PatientFormState(
    species: Species = Species.DOG,
    sex: PatientSex = PatientSex.UNKNOWN,
) {
    var name: String by mutableStateOf("")
    var breed: String by mutableStateOf("")
    var ageValueText: String by mutableStateOf("")
    var ageUnit: AgeUnit by mutableStateOf(AgeUnit.MONTHS)
    var weightText: String by mutableStateOf("")
    var species: Species by mutableStateOf(species)
    var sex: PatientSex by mutableStateOf(sex)

    /** Pre-fills every editable field from an existing patient (edit mode). */
    fun load(patient: Patient) {
        name = patient.name
        species = patient.species
        sex = patient.sex
        breed = patient.breed.orEmpty()
        ageValueText = patient.ageValue?.let(::formatNumber).orEmpty()
        ageUnit = patient.ageUnit ?: AgeUnit.MONTHS
        weightText = patient.weightKg?.let(::formatNumber).orEmpty()
    }

    private val ageIsValid: Boolean
        get() = ageValueText.isBlank() || (ageValue != null)

    private val weightIsValid: Boolean
        get() = weightText.isBlank() || weightKg != null

    val valid: Boolean
        get() = name.isNotBlank() && ageIsValid && weightIsValid

    val ageValue: Double?
        get() = ageValueText.toDoubleOrNull()?.takeIf { value -> value > 0.0 }

    val weightKg: Double?
        get() = weightText.toDoubleOrNull()?.takeIf { it >= 0.0 }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
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
    AgeFields(form)
    OutlinedTextField(
        value = form.weightText,
        onValueChange = { form.weightText = it },
        label = { Text("Weight (kg)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Age value + unit pair: poultry in days/weeks, adults in months/years. */
@Composable
private fun AgeFields(form: PatientFormState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = form.ageValueText,
            onValueChange = { form.ageValueText = it },
            label = { Text("Age value") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        )
        AgeUnitField(
            selected = form.ageUnit,
            onSelect = { form.ageUnit = it },
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        )
    }
}

@Composable
private fun AgeUnitField(
    selected: AgeUnit,
    onSelect: (AgeUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = selected.label, modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.nav_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        AgeUnit.entries.forEach { unit ->
            DropdownMenuItem(
                text = { Text(unit.label) },
                onClick = {
                    onSelect(unit)
                    expanded = false
                },
            )
        }
    }
}

/** Breed picker scoped to the selected species (BreedCatalog-driven). */
@Composable
private fun BreedField(
    selected: String,
    breeds: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.ifBlank { "Select breed" },
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.nav_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        breeds.forEach { breed ->
            DropdownMenuItem(
                text = { Text(breed) },
                onClick = {
                    onSelect(breed)
                    expanded = false
                },
            )
        }
    }
}

/** Species selector now shared via ui.components.SpeciesField. */

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
