package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
 * Modal sheet that keeps an edit in progress: a focus change while an input
 * is active never turns into an accidental dismissal, and the sheet keeps its
 * default stable expansion while the soft keyboard opens or changes insets.
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
    ) {
        Column(modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
            editing = state.hasFocus
        }) {
            content()
        }
    }
}
