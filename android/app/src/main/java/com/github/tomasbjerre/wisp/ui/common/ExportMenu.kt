package com.github.tomasbjerre.wisp.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Wraps an export [button] with a small menu offering **Share** (the existing OS share
 * sheet — [onShare]) or **Save to device** (the platform's own file/folder picker,
 * writing directly to local storage with no other app in between — [onSaveToDevice]).
 * See specs/export.md#trigger and issue #141: sharing to a cloud app and downloading
 * from there again was previously the only way to end up with a local copy.
 *
 * A menu on the existing button rather than a second button — Detail's export row
 * already has just enough width for two buttons per row (see #60); a third would force
 * an unreadable wrap on a narrow phone.
 */
@Composable
fun ExportMenu(
    onShare: () -> Unit,
    onSaveToDevice: () -> Unit,
    modifier: Modifier = Modifier,
    button: @Composable (onClick: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        button { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    expanded = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = { Text("Save to device") },
                onClick = {
                    expanded = false
                    onSaveToDevice()
                },
            )
        }
    }
}
