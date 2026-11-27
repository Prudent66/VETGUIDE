package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Keeps digits and at most one decimal separator, normalising a comma to a
 * dot. Selecting an existing value and replacing it therefore never produces
 * a malformed intermediate value such as "12..5" or "1e3".
 */
internal fun cleanNumericInput(raw: String, allowDecimal: Boolean): String {
    var separatorSeen = false
    val cleaned = buildString {
        raw.forEach { char ->
            when {
                char in '0'..'9' -> append(char)
                allowDecimal && (char == '.' || char == ',') && !separatorSeen -> {
                    separatorSeen = true
                    append('.')
                }
            }
        }
    }
    return cleaned.take(12)
}

/** Numeric field used for weight, headcount, bottle size and outbreak counts. */
@Composable
internal fun NumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    allowDecimal: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(cleanNumericInput(it, allowDecimal)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        singleLine = true,
        modifier = modifier,
    )
}
