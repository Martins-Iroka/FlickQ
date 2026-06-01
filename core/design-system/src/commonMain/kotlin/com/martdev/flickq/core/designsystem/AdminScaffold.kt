package com.martdev.flickq.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared chrome for every admin screen: a gold title bar with an optional back affordance and a
 * trailing action slot, over the standard [RoomBackgroundBrush]. Content fills the area below.
 */
@Composable
fun AdminScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(RoomBackgroundBrush)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FlickQColors.DeepNavy)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    TextButton(onClick = onBack) {
                        Text(text = "‹ Back", color = FlickQColors.GoldHighlight, fontSize = 14.sp)
                    }
                }
                Text(
                    text = title,
                    color = FlickQColors.Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = if (onBack != null) 8.dp else 0.dp),
                )
            }
            actions()
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

/** Centred spinner for an admin screen's loading state. */
@Composable
fun AdminLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = FlickQColors.Gold)
    }
}

/** Centred error message with an optional retry button. */
@Composable
fun AdminError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Something went wrong", color = FlickQColors.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = message, color = FlickQColors.Error, modifier = Modifier.padding(top = 8.dp))
        if (onRetry != null) {
            FlickQButton(text = "Retry", onClick = onRetry, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** Empty-state placeholder for a list with no rows. */
@Composable
fun AdminEmpty(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().background(FlickQColors.SurfaceNavy, RoundedCornerShape(10.dp)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, color = FlickQColors.SeatAvailable, fontSize = 14.sp)
    }
}
