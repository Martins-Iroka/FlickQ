package com.martdev.flickq.feature.notification.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.presentation.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationPromptRoot(
    modifier: Modifier = Modifier,
    viewModel: NotificationPromptViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermission { status ->
        viewModel.onAction(NotificationPromptAction.OnPermissionChecked(status))
    }

    // Re-check on every resume: the user may have come back from system settings.
    LifecycleResumeEffect(permission) {
        viewModel.onAction(NotificationPromptAction.OnPermissionChecked(permission.status()))
        onPauseOrDispose { }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            NotificationPromptEvent.RequestPermission -> permission.request()
            NotificationPromptEvent.OpenSettings -> permission.openSettings()
        }
    }

    NotificationPromptCard(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun NotificationPromptCard(
    state: NotificationPromptState,
    onAction: (NotificationPromptAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prompt = state.prompt ?: return
    val shape = RoundedCornerShape(16.dp)
    val blocked = prompt == NotificationPrompt.OPEN_SETTINGS

    Column(
        modifier = modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .padding(top = 24.dp)
            .background(FlickQColors.SurfaceNavy, shape)
            .border(1.dp, FlickQColors.OutlineNavy, shape)
            .padding(16.dp),
    ) {
        Text(
            text = if (blocked) "Notifications are off" else "Get booking updates",
            color = FlickQColors.Gold,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (blocked) {
                "Allow notifications for FlickQ in Settings to hear when your seats are held and your booking is confirmed."
            } else {
                "Turn on notifications to hear when your seats are held and your booking is confirmed."
            },
            color = FlickQColors.TicketPaper,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onAction(NotificationPromptAction.OnNotNowClick) }) {
                Text(text = "Not now", color = FlickQColors.GoldHighlight)
            }
            FlickQButton(
                text = if (blocked) "Open settings" else "Enable",
                onClick = {
                    onAction(
                        if (blocked) NotificationPromptAction.OnOpenSettingsClick
                        else NotificationPromptAction.OnEnableClick
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}