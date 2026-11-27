package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared modal sheet used by the prescription, save-to-case and herd flows.
 *
 * Dismissal is never intercepted. Swallowing onDismissRequest leaves the
 * dialog window and its scrim mounted while the sheet animates away, so every
 * later tap lands on an invisible overlay and the app looks frozen until it is
 * killed. Material 3 already keeps the sheet stable when the IME opens, closes
 * or changes insets, so no focus/inset state is kept here either — a focus
 * loop would likewise block touch dispatch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StableBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
