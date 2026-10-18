package com.stanelie.transmote2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Shared top bar palette. Sits on a neutral surface tone rather than the
 * primary container, so the bar reads as part of the page instead of a
 * coloured band above the torrent list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun transmoteTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Plain screen title, styled consistently across the app. */
@Composable
fun TopBarTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * Title for the main screen: the app name with the connection state shown as a
 * status dot and a muted secondary line, instead of a long raw status string
 * squeezed into the title slot.
 */
@Composable
fun ConnectionStatusTitle(status: String) {
    val dotColor = when {
        status.startsWith("Connected") -> Color(0xFF388E3C)
        status.startsWith("Error") -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    // "Connected to 10.0.0.2:9091" reads better as just the endpoint here.
    val detail = status.removePrefix("Connected to ")

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = dotColor,
            content = {}
        )
        Spacer(modifier = Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Transmote",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
