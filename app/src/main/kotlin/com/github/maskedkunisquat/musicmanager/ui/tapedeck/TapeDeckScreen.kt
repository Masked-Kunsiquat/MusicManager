package com.github.maskedkunisquat.musicmanager.ui.tapedeck

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
import com.github.maskedkunisquat.musicmanager.logic.inbox.TapeDeckItem
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.ui.components.RetroButton
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel

// Must match EventGenerator.PASS_LEAD_COOLDOWN.
private const val PASS_LEAD_COOLDOWN = 10
// Must match SimRepositoryImpl.MAX_SURFACED_LEADS.
private const val MAX_SURFACED_LEADS = 3

@Composable
fun TapeDeckScreen(viewModel: InboxViewModel, onBack: () -> Unit) {
    val world by viewModel.world.collectAsStateWithLifecycle()
    val leads by viewModel.activeSurfacedLeads.collectAsStateWithLifecycle()

    val browsePool = world.prospects.values
        .filter { p ->
            p.id !in world.surfacedLeads &&
            p.id !in world.unavailableProspects &&
            p.id !in world.activeNegotiations &&
            (world.passedLeads[p.id].let { it == null || world.currentDay - it >= PASS_LEAD_COOLDOWN })
        }
        .sortedByDescending { it.signabilityScore }

    val deckFull = world.surfacedLeads.size >= MAX_SURFACED_LEADS

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TAPE DECK",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                RetroButton(onClick = onBack) { Text("BACK") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (leads.isNotEmpty()) {
            item {
                Text(
                    text = "ACTIVE DEMOS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(leads, key = { it.id }) { item ->
                LeadCard(
                    item = item,
                    world = world,
                    onResolve = { option -> viewModel.resolveEvent(item.id, option) }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        item {
            Text(
                text = "BROWSE UNSIGNED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (deckFull) {
                Text(
                    text = "Deck full — resolve an active demo to pull more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (browsePool.isEmpty()) {
            item {
                Text(
                    text = "No unsigned artists available right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(browsePool, key = { it.id }) { prospect ->
                BrowseRow(
                    prospect = prospect,
                    canPull = !deckFull,
                    onPull = { viewModel.requestLead(prospect.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun BrowseRow(
    prospect: ProspectState,
    canPull: Boolean,
    onPull: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prospect.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = prospect.genre.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = prospect.demo.descriptor,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RetroButton(
            onClick = onPull,
            enabled = canPull,
            filled = false
        ) {
            Text("PULL")
        }
    }
}

@Composable
private fun LeadCard(
    item: TapeDeckItem,
    world: SimWorld,
    onResolve: (ResponseOption) -> Unit
) {
    val prospect = world.prospects[item.prospectId]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = prospect?.name ?: "Unknown Artist",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = prospect?.genre?.uppercase() ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (prospect != null) {
            Text(
                text = prospect.demo.descriptor,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pursue = item.options.firstOrNull { opt -> opt.effects.any { it is StateEffect.PursueLead } }
            val pass   = item.options.firstOrNull { opt -> opt.effects.any { it is StateEffect.PassLead } }
            val watch  = item.options.firstOrNull { opt -> opt.effects.any { it is StateEffect.WatchLead } }

            if (pursue != null) {
                RetroButton(onClick = { onResolve(pursue) }) { Text("PURSUE") }
            }
            if (pass != null) {
                RetroButton(onClick = { onResolve(pass) }) { Text("PASS") }
            }
            if (watch != null) {
                RetroButton(onClick = { onResolve(watch) }) { Text("WATCH") }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
