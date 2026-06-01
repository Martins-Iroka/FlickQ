package com.martdev.flickq.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Confirmation dialog for destructive or irreversible admin actions (delete a movie, cancel a
 * reservation). [destructive] tints the confirm label red.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlickQColors.DeepNavy,
        title = { Text(text = title, color = FlickQColors.Gold, fontWeight = FontWeight.Bold) },
        text = { Text(text = message, color = FlickQColors.TicketPaper) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) FlickQColors.Error else FlickQColors.GoldHighlight,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel, color = FlickQColors.SeatAvailable)
            }
        },
    )
}

/**
 * Form dialog for create/edit flows: a titled card hosting arbitrary [content] (text fields),
 * with a confirm action that can be gated via [confirmEnabled].
 */
@Composable
fun FlickQFormDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlickQColors.DeepNavy,
        title = { Text(text = title, color = FlickQColors.Gold, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    text = confirmLabel,
                    color = if (confirmEnabled) FlickQColors.GoldHighlight else FlickQColors.SeatAvailable,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = FlickQColors.SeatAvailable)
            }
        },
    )
}

/** A small inline text button used for table row actions, tintable for destructive ops. */
@Composable
fun RowAction(label: String, onClick: () -> Unit, tint: Color = FlickQColors.GoldHighlight) {
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 4.dp)) {
        Text(text = label, color = tint, fontWeight = FontWeight.Medium)
    }
}
