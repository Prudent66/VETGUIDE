package com.prudent66.vetguide.ui.screens.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.formulary.BatchTreatment
import com.prudent66.vetguide.domain.formulary.HerdMetrics
import com.prudent66.vetguide.domain.formulary.batchTreatment
import com.prudent66.vetguide.domain.formulary.findDrug
import com.prudent66.vetguide.domain.formulary.formulary
import com.prudent66.vetguide.domain.formulary.herdMetrics
import com.prudent66.vetguide.domain.formulary.quarantineChecklist
import com.prudent66.vetguide.domain.formulary.wildlifeQuarantineChecklist
import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.ui.components.AnimalCategoryToggle

/** Full-screen herd/flock triage: headcount, batch medication and quarantine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerdOutbreakScreen(onDismiss: () -> Unit) {
    val form = remember { HerdFormState() }
    val snapshot = form.snapshot()
    val metrics = herdMetrics(snapshot)
    val drug = findDrug(form.drugId) ?: formulary.first()
    val batch = batchTreatment(
        drug = drug,
        snapshot = snapshot,
        bottleSizeMl = form.bottleSizeText.toDoubleOrNull() ?: 100.0,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Herd / Flock Outbreak Mode",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.nav_back),
                    contentDescription = "Close",
                )
            }
        }
        HerdFormFields(form)
        metrics?.let { MetricsPanel(it) }
        batch?.let { BatchPanel(it) }
        QuarantinePanel(snapshot.species, form.animalCategory)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HerdFormFields(form: HerdFormState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimalCategoryToggle(
            selected = form.animalCategory,
            onSelect = { form.switchCategory(it) },
        )
        OutlinedTextField(
            value = form.unitName,
            onValueChange = { form.unitName = it },
            label = { Text("Unit name (herd / pen / flock)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        HerdCategoryAndSpeciesFields(form)
        OutlinedTextField(
            value = form.headcountText,
            onValueChange = { form.headcountText = it },
            label = { Text("Total headcount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.sickText,
                onValueChange = { form.sickText = it },
                label = { Text("Sick / affected") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.deadText,
                onValueChange = { form.deadText = it },
                label = { Text("Dead") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = form.weightText,
            onValueChange = { form.weightText = it },
            label = { Text("Average weight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DrugAndBottleFields(form)
    }
}

@Composable
private fun DrugAndBottleFields(form: HerdFormState) {
    SelectionField("Drug", findDrug(form.drugId)?.name.orEmpty(), formulary.map { it.name }) { name ->
        formulary.firstOrNull { it.name == name }?.let { form.drugId = it.id }
    }
    OutlinedTextField(
        value = form.bottleSizeText,
        onValueChange = { form.bottleSizeText = it },
        label = { Text("Bottle size (mL)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value", modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.nav_chevron_down),
                contentDescription = null,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MetricsPanel(metrics: HerdMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Outbreak metrics", style = MaterialTheme.typography.titleSmall)
            Text("Affected animals: ${metrics.affectedCount}")
            Text("Morbidity rate: ${formatPercent(metrics.morbidityPercent)}")
            Text("Case fatality rate: ${formatPercent(metrics.caseFatalityPercent)}")
        }
    }
}

@Composable
private fun BatchPanel(batch: BatchTreatment) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Batch treatment & vial logistics", style = MaterialTheme.typography.titleSmall)
            Text("Drug: ${batch.drugName} · ${formatAmount(batch.doseMgPerKg)} mg/kg")
            Text("Total drug required: ${formatAmount(batch.totalMg)} mg")
            Text("Total volume: ${formatAmount(batch.totalMl)} mL (${formatAmount(batch.totalLiters)} L)")
            batch.waterVolumeLiters?.let { litres ->
                Text("Drinking-water mix: ${formatAmount(litres)} L at 100 mL stock per L")
            }
            Text("Bottles needed: ${batch.bottleCount} × ${formatAmount(batch.bottleSizeMl)} mL")
            Text("Meat withdrawal: ${batch.meatWithdrawalDays} days · Milk: ${batch.milkWithdrawalDays} days")
        }
    }
}

@Composable
private fun QuarantinePanel(species: Species, category: AnimalCategory) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Contagious outbreak quarantine checklist", style = MaterialTheme.typography.titleSmall)
            val steps = if (category == AnimalCategory.WILD) {
                wildlifeQuarantineChecklist()
            } else {
                quarantineChecklist(suspectedConditions(species))
            }
            steps.forEach { step ->
                Text("• $step", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun suspectedConditions(species: Species): List<String> = when (species) {
    Species.CATTLE -> listOf("contagious-bovine-pleuropneumonia", "foot-and-mouth-disease", "mastitis")
    Species.GOAT, Species.SHEEP -> listOf("peste-des-petits-ruminants", "foot-and-mouth-disease")
    Species.POULTRY -> listOf("newcastle-disease", "fowl-pox")
    Species.PIG -> listOf("foot-and-mouth-disease")
    else -> emptyList()
}

private fun formatPercent(value: Double): String = "${String.format(java.util.Locale.US, "%.1f", value)}%"

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", value)
    }
