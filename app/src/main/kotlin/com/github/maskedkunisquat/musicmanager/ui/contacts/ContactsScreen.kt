package com.github.maskedkunisquat.musicmanager.ui.contacts

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.logic.contacts.recencyDescriptor
import com.github.maskedkunisquat.musicmanager.logic.contacts.toneDescriptor
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistInteractionEntry
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.ui.components.RetroButton
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel

@Composable
fun ContactsScreen(viewModel: InboxViewModel, onBack: () -> Unit) {
    val world by viewModel.world.collectAsStateWithLifecycle()
    val artistHistories by viewModel.artistHistories.collectAsStateWithLifecycle()
    var expandedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(expandedId) {
        expandedId?.let { viewModel.loadArtistHistory(it) }
    }

    val rosterArtists = world.label.rosterIds
        .mapNotNull { id -> world.artists[id] }
        .sortedBy { it.name }

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
                text = "CONTACTS",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            RetroButton(onClick = onBack) { Text("BACK") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        if (rosterArtists.isEmpty()) {
            Text(
                text = "No artists on the roster yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(rosterArtists, key = { it.id }) { artist ->
                    val isExpanded = expandedId == artist.id
                    ContactRow(
                        artist = artist,
                        currentDay = world.currentDay,
                        isExpanded = isExpanded,
                        history = artistHistories[artist.id],
                        canCheckIn = viewModel.canCheckIn(artist.id),
                        onToggle = { expandedId = if (isExpanded) null else artist.id },
                        onCheckIn = { viewModel.checkInWithArtist(artist.id) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    artist: ArtistState,
    currentDay: Int,
    isExpanded: Boolean,
    history: List<ArtistInteractionEntry>?,
    canCheckIn: Boolean,
    onToggle: () -> Unit,
    onCheckIn: () -> Unit
) {
    val daysSince = currentDay - artist.lastInteractionDay
    val recency = recencyDescriptor(daysSince)
    val tone = toneDescriptor(artist.relationshipBalance)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = artist.genre.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "$recency / $tone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(10.dp))

            val lowNeeds = artist.needs.values
                .filter { it.value < 0.35f }
                .sortedBy { it.value }
            if (lowNeeds.isNotEmpty()) {
                Text(
                    text = "NEEDS ATTENTION: ${lowNeeds.joinToString(", ") { it.type.shortLabel() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            when {
                history == null -> Text(
                    text = "loading...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                history.isEmpty() -> Text(
                    text = "No interactions recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    Text(
                        text = "── HISTORY ─────────────────────",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    history.asReversed().forEach { entry ->
                        HistoryEntry(entry)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            RetroButton(
                onClick = onCheckIn,
                enabled = canCheckIn
            ) {
                Text(if (canCheckIn) "REACH OUT" else "RECENTLY CONTACTED")
            }
        }
    }
}

@Composable
private fun HistoryEntry(entry: ArtistInteractionEntry) {
    Column {
        Text(
            text = "Day ${entry.day}  ${entry.eventSummary}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "  → ${entry.choiceMade}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun NeedType.shortLabel(): String = when (this) {
    NeedType.CREATIVE_FULFILLMENT -> "creative"
    NeedType.FINANCIAL_SECURITY   -> "money"
    NeedType.RECOGNITION          -> "recognition"
    NeedType.BELONGING            -> "belonging"
    NeedType.AUTONOMY             -> "autonomy"
}
