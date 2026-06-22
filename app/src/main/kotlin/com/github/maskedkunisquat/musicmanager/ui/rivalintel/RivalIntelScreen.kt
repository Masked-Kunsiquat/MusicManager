package com.github.maskedkunisquat.musicmanager.ui.rivalintel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.logic.model.RivalSnapshot
import com.github.maskedkunisquat.musicmanager.logic.rivalintel.RivalIntelFuzzer
import com.github.maskedkunisquat.musicmanager.ui.components.RetroButton
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel

@Composable
fun RivalIntelScreen(viewModel: InboxViewModel, onBack: () -> Unit) {
    val world by viewModel.world.collectAsStateWithLifecycle()

    val knownRivals = world.rivals.values.sortedBy { it.name }
    val intelCache = world.label.intelCache
    val currentDay = world.currentDay

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RIVAL INTEL",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            RetroButton(onClick = onBack) { Text("BACK") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        if (knownRivals.isEmpty()) {
            Text(
                text = "No rival labels identified yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(knownRivals, key = { it.id }) { rival ->
                    val snapshot = intelCache[rival.id]
                    RivalIntelCard(
                        rivalName = rival.name,
                        snapshot = snapshot,
                        currentDay = currentDay
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun RivalIntelCard(
    rivalName: String,
    snapshot: RivalSnapshot?,
    currentDay: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rivalName.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (snapshot == null) {
                Text(
                    text = "[NO DATA]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (RivalIntelFuzzer.isStale(snapshot, currentDay)) {
                Text(
                    text = "[STALE]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (snapshot != null) {
            Spacer(modifier = Modifier.height(4.dp))
            val rosterSize = RivalIntelFuzzer.fuzzedRosterSize(snapshot, currentDay)
            val genres = RivalIntelFuzzer.fuzzedGenres(snapshot, currentDay)
            Text(
                text = "roster: ~$rosterSize artists",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "focus: ${genres.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select \"Dig into what they're building\" from a rival event to gather intel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
