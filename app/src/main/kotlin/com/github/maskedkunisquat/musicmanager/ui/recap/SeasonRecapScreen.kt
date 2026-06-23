package com.github.maskedkunisquat.musicmanager.ui.recap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonSummary
import com.github.maskedkunisquat.musicmanager.ui.components.RetroButton
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel
import java.util.Locale
import kotlin.math.abs

@Composable
fun SeasonRecapScreen(
    viewModel: InboxViewModel,
    onStartNewSeason: () -> Unit
) {
    val summary by viewModel.seasonSummary.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadSeasonSummary() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (summary == null) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                RecapContent(summary = summary!!)
            }
        }

        if (summary != null) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            RetroButton(
                onClick = onStartNewSeason,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("[ START SEASON ${summary!!.seasonNumber + 1} ]")
            }
        }
    }
}

@Composable
private fun RecapContent(summary: SeasonSummary) {
    Text(
        text = "== SEASON ${summary.seasonNumber} CLOSED ==",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(modifier = Modifier.height(16.dp))

    // Roster
    Text(
        text = "Roster: ${summary.artistsRetained} signed / ${summary.artistsLost} departed",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    if (summary.departedArtistNames.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        summary.departedArtistNames.forEach { name ->
            Text(
                text = "  — $name",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    // Deadlines
    Text(
        text = "Deadlines: ${summary.deadlinesMet} met / ${summary.deadlinesMissed} missed",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Reputation deltas — only show non-trivial changes
    val repLines = summary.reputationDeltas
        .filter { (_, delta) -> abs(delta) > 0.005f }
        .entries.sortedBy { it.key }
    if (repLines.isNotEmpty()) {
        repLines.forEach { (community, delta) ->
            val sign = if (delta >= 0f) "+" else ""
            val formatted = String.format(Locale.US, "%.2f", delta)
            Text(
                text = "${community.replace('_', ' ')} $sign$formatted",
                style = MaterialTheme.typography.bodyMedium,
                color = if (delta >= 0f) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Funds
    Text(
        text = "Funds: ${formatFundsDelta(summary.fundsNet)}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (summary.fundsNet >= 0L) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurface
    )
}

private fun formatFundsDelta(cents: Long): String {
    val sign = if (cents >= 0L) "+" else "-"
    val absDollars = abs(cents) / 100L
    return if (absDollars >= 1000L) {
        "$sign\$${absDollars / 1000L}k"
    } else {
        "$sign\$$absDollars"
    }
}
