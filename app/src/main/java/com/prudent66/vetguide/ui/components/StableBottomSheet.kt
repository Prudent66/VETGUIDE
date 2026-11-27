package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * Modal sheet whose geometry stays put while the soft keyboard opens, closes
 * or changes insets. IME padding lives on the content instead of driving the
 * sheet, the sheet cannot collapse mid-typing, and a focus change while an
 * input is active never turns into an accidental dismissal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StableBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { if (!editing) onDismiss() },
        windowInsets = WindowInsets(0, 0, 0, 0),
        skipPartiallyExpanded = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .onFocusChanged { state -> editing = state.hasFocus },
        ) {
            content()
        }
    }
}
