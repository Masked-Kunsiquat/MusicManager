package com.github.maskedkunisquat.musicmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.ui.components.RetroButton
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel
import com.github.maskedkunisquat.musicmanager.ui.theme.RetroTheme

private const val RIVAL_INTEL_REP_GATE = 0.5f

@Composable
fun HomeScreen(
    onOpenInbox: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenPress: () -> Unit,
    onOpenLabelOffice: () -> Unit,
    onOpenTapeDeck: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenRivalIntel: () -> Unit,
    viewModel: InboxViewModel
) {
    val world by viewModel.world.collectAsStateWithLifecycle()
    val pressRep = world.label.reputation[ReputationCommunity.PRESS] ?: 0f
    val rivalIntelUnlocked = pressRep >= RIVAL_INTEL_REP_GATE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = world.label.name.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "SEASON ${world.season.seasonNumber} — DAY ${world.currentDay}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(24.dp))
        RetroButton(onClick = onOpenInbox, modifier = Modifier.fillMaxWidth()) {
            Text("INBOX")
        }
        Spacer(modifier = Modifier.height(12.dp))
        RetroButton(onClick = onOpenCharts, modifier = Modifier.fillMaxWidth()) {
            Text("CHARTS")
        }
        Spacer(modifier = Modifier.height(12.dp))
        RetroButton(onClick = onOpenPress, modifier = Modifier.fillMaxWidth()) {
            Text("PRESS")
        }
        Spacer(modifier = Modifier.height(12.dp))
        RetroButton(onClick = onOpenLabelOffice, modifier = Modifier.fillMaxWidth()) {
            Text("LABEL OFFICE")
        }
        Spacer(modifier = Modifier.height(12.dp))
        RetroButton(onClick = onOpenTapeDeck, modifier = Modifier.fillMaxWidth()) {
            Text("TAPE DECK")
        }
        Spacer(modifier = Modifier.height(12.dp))
        RetroButton(onClick = onOpenContacts, modifier = Modifier.fillMaxWidth()) {
            Text("CONTACTS")
        }
        if (rivalIntelUnlocked) {
            Spacer(modifier = Modifier.height(12.dp))
            RetroButton(onClick = onOpenRivalIntel, modifier = Modifier.fillMaxWidth()) {
                Text("RIVAL INTEL")
            }
        }
    }
}

