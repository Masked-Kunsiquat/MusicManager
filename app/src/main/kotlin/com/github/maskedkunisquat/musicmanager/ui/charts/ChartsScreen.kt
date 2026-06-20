package com.github.maskedkunisquat.musicmanager.ui.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel
import com.github.maskedkunisquat.musicmanager.ui.theme.RetroTheme
import kotlin.math.roundToInt

@Composable
fun ChartsScreen(viewModel: InboxViewModel, onBack: () -> Unit) {
    val world by viewModel.world.collectAsStateWithLifecycle()
    ChartsContent(
        snapshot = world.chartSnapshot,
        currentDay = world.currentDay,
        onBack = onBack
    )
}

@Composable
private fun ChartsContent(
    snapshot: MarketState,
    currentDay: Int,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "CHARTS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "day $currentDay",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (snapshot.genreTrends.isEmpty()) {
            Text(
                text = "no data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            val ranked = snapshot.genreTrends.entries
                .sortedByDescending { it.value }

            ranked.forEachIndexed { index, (genre, trend) ->
                GenreRow(rank = index + 1, genre = genre, trend = trend)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            text = "~ data delayed ~",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun GenreRow(rank: Int, genre: String, trend: Float) {
    val blocks = (trend * 8).roundToInt().coerceIn(0, 8)
    val bar = "█".repeat(blocks).padEnd(8)
    val pct = (trend * 100).roundToInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = genre,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = bar,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChartsContentPreview() {
    RetroTheme {
        ChartsContent(
            snapshot = MarketState(
                genreTrends = mapOf(
                    "hip-hop" to 0.82f,
                    "pop" to 0.63f,
                    "electronic" to 0.51f,
                    "indie-rock" to 0.44f,
                    "r&b" to 0.32f,
                    "folk" to 0.18f,
                )
            ),
            currentDay = 12,
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChartsContentEmptyPreview() {
    RetroTheme {
        ChartsContent(
            snapshot = MarketState(emptyMap()),
            currentDay = 1,
            onBack = {}
        )
    }
}
