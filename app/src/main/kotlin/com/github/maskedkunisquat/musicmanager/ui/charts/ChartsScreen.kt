package com.github.maskedkunisquat.musicmanager.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel
import com.github.maskedkunisquat.musicmanager.ui.theme.RetroTheme
import kotlin.math.roundToInt

@Composable
fun ChartsScreen(viewModel: InboxViewModel, onBack: () -> Unit) {
    val world by viewModel.world.collectAsStateWithLifecycle()
    val trendHistory by viewModel.trendHistory.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadTrendHistory() }

    ChartsContent(world = world, trendHistory = trendHistory, onBack = onBack)
}

@Composable
private fun ChartsContent(
    world: SimWorld,
    trendHistory: Map<String, List<Float>>,
    onBack: () -> Unit
) {
    val rosterGenres = world.label.rosterIds
        .mapNotNull { world.artists[it]?.genre }
        .toSet()

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
                text = "day ${world.currentDay}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (world.chartSnapshot.genreTrends.isEmpty()) {
            Text(
                text = "no data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                val ranked = world.chartSnapshot.genreTrends.entries
                    .sortedByDescending { it.value }

                ranked.forEachIndexed { index, (genre, trend) ->
                    GenreRow(
                        rank = index + 1,
                        genre = genre,
                        trend = trend,
                        isRosterGenre = genre in rosterGenres,
                        history = trendHistory[genre]
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

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
private fun GenreRow(
    rank: Int,
    genre: String,
    trend: Float,
    isRosterGenre: Boolean,
    history: List<Float>?
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val barFillColor = lerp(outlineColor, primaryColor, trend.coerceIn(0f, 1f))
    val pct = (trend * 100).roundToInt()

    val spark = history?.let { sparkline(it) }.orEmpty()
    val direction = history?.let { trendDirection(it) }.orEmpty()
    val directionColor = when {
        direction.contains("↑") -> MaterialTheme.colorScheme.primary
        direction.contains("↓") -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isRosterGenre) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                )
                if (isRosterGenre) {
                    Text(
                        text = " ●",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            ) {
                drawRect(color = surfaceVariantColor)
                if (trend > 0f) {
                    drawRect(
                        color = barFillColor,
                        size = Size(size.width * trend.coerceIn(0f, 1f), size.height)
                    )
                }
            }
            if (spark.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = spark + direction,
                    style = MaterialTheme.typography.labelSmall,
                    color = directionColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
    }
}

private fun sparkline(values: List<Float>): String {
    if (values.size < 2) return ""
    val recent = values.takeLast(8)
    val min = recent.min()
    val max = recent.max()
    val range = max - min
    val chars = listOf("▁", "▂", "▃", "▄", "▅", "▆", "▇", "█")
    return if (range < 0.02f) {
        "─".repeat(recent.size)
    } else {
        recent.joinToString("") { v ->
            val normalized = ((v - min) / range).coerceIn(0f, 1f)
            chars[(normalized * 7).roundToInt()]
        }
    }
}

private fun trendDirection(values: List<Float>): String {
    if (values.size < 4) return ""
    val recent = values.takeLast(3).average().toFloat()
    val older = values.dropLast(3).takeLast(3).takeIf { it.isNotEmpty() }?.average()?.toFloat()
        ?: return ""
    return when {
        recent > older + 0.03f -> " ↑"
        recent < older - 0.03f -> " ↓"
        else -> " →"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun ChartsContentPreview() {
    RetroTheme {
        ChartsContent(
            world = SimWorld(
                seed = 1L,
                currentDay = 42,
                artists = mapOf(
                    "a1" to com.github.maskedkunisquat.musicmanager.logic.model.ArtistState(
                        id = "a1", name = "Wild Stars", genre = "indie-rock",
                        dimensions = com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions(0.5f, 0.5f, 0.5f, 0.7f),
                        needs = emptyMap(), activeWants = emptyList(), contractId = null
                    )
                ),
                label = com.github.maskedkunisquat.musicmanager.logic.model.LabelState(
                    funds = 3_200_000L,
                    reputation = com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity.entries.associateWith { 0.35f },
                    rosterIds = setOf("a1")
                ),
                market = MarketState(emptyMap()),
                contracts = emptyMap(),
                chartSnapshot = MarketState(
                    genreTrends = mapOf(
                        "hip-hop" to 0.82f,
                        "pop" to 0.63f,
                        "electronic" to 0.51f,
                        "indie-rock" to 0.44f,
                        "r&b" to 0.32f,
                        "folk" to 0.18f,
                    )
                )
            ),
            trendHistory = mapOf(
                "hip-hop" to listOf(0.55f, 0.62f, 0.68f, 0.71f, 0.74f, 0.78f, 0.82f),
                "pop" to listOf(0.70f, 0.68f, 0.65f, 0.64f, 0.63f),
                "electronic" to listOf(0.49f, 0.51f, 0.50f, 0.51f),
                "indie-rock" to listOf(0.60f, 0.55f, 0.50f, 0.46f, 0.44f),
            ),
            onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun ChartsContentEmptyPreview() {
    RetroTheme {
        ChartsContent(
            world = SimWorld(
                seed = 1L,
                currentDay = 1,
                artists = emptyMap(),
                label = com.github.maskedkunisquat.musicmanager.logic.model.LabelState(
                    funds = 0L,
                    reputation = com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity.entries.associateWith { 0f },
                    rosterIds = emptySet()
                ),
                market = MarketState(emptyMap()),
                contracts = emptyMap()
            ),
            trendHistory = emptyMap(),
            onBack = {}
        )
    }
}
