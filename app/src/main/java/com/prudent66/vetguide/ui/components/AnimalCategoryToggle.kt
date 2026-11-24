package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.model.AnimalCategory

/** Segmented Domestic / Wild pathway switch shared by all clinical screens. */
@Composable
fun AnimalCategoryToggle(
    selected: AnimalCategory,
    onSelect: (AnimalCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimalCategory.entries.forEach { category ->
            val active = category == selected
            val select = { onSelect(category) }
            if (active) {
                Button(onClick = select, modifier = Modifier.weight(1f)) {
                    Text(category.label)
                }
            } else {
                OutlinedButton(onClick = select, modifier = Modifier.weight(1f)) {
                    Text(category.label)
                }
            }
        }
    }
}
