package com.github.maskedkunisquat.musicmanager.ui.labeloffice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.logic.model.CapabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.sim.LabelNeedEvaluator
import com.github.maskedkunisquat.musicmanager.ui.components.RetroButton
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel
import com.github.maskedkunisquat.musicmanager.ui.theme.RetroTheme

@Composable
fun LabelOfficeScreen(viewModel: InboxViewModel, onBack: () -> Unit, onOpenIdentity: () -> Unit) {
    val world by viewModel.world.collectAsStateWithLifecycle()
    LabelOfficeContent(world = world, onBack = onBack, onOpenIdentity = onOpenIdentity)
}

@Composable
private fun LabelOfficeContent(world: SimWorld, onBack: () -> Unit, onOpenIdentity: () -> Unit) {
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
                text = "LABEL OFFICE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SectionHeader("FINANCIAL")
            StatusLine(
                label = "Cash flow",
                value = cashFlowLabel(LabelNeedEvaluator.cashFlow(world)),
                critical = LabelNeedEvaluator.cashFlow(world) < 0.35f
            )
            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("ROSTER")
            StatusLine(
                label = "Diversity",
                value = rosterLabel(world)
            )
            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("CAPABILITIES")
            CapabilityType.entries.forEach { cap ->
                CapabilityRow(
                    type = cap,
                    unlocked = cap in world.label.capabilities,
                    gateText = gateText(cap, world)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            RetroButton(
                onClick = onOpenIdentity,
                filled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("IDENTITY >")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun StatusLine(label: String, value: String, critical: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (critical) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CapabilityRow(type: CapabilityType, unlocked: Boolean, gateText: String) {
    val prefix = if (unlocked) "■" else "□"
    val label = capabilityLabel(type)
    val suffix = if (!unlocked) " — $gateText" else ""
    Text(
        text = "$prefix $label$suffix",
        style = MaterialTheme.typography.bodyMedium,
        color = if (unlocked) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun cashFlowLabel(score: Float): String = when {
    score < 0.35f -> "critical"
    score < 0.65f -> "tight"
    else -> "strong"
}

private fun rosterLabel(world: SimWorld): String {
    val artists = world.artists.values
    if (artists.isEmpty()) return "no roster"
    val genres = artists.map { it.genre }
    val distinct = genres.toSet()
    return when {
        distinct.size == 1 -> "one-genre"
        LabelNeedEvaluator.genreDiversity(world) < 0.40f -> {
            val dominant = genres.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "unknown"
            "skewing $dominant"
        }
        else -> "diversified"
    }
}

private fun capabilityLabel(type: CapabilityType): String = when (type) {
    CapabilityType.PUBLICIST -> "Publicist"
    CapabilityType.IN_HOUSE_BOOKING -> "In-House Booking"
    CapabilityType.VIDEO_PRODUCTION -> "Video Production"
}

private fun gateText(type: CapabilityType, world: SimWorld): String = when (type) {
    CapabilityType.PUBLICIST -> {
        val rep = world.label.reputation[ReputationCommunity.PRESS] ?: 0f
        if (rep >= 0.4f) "ready to unlock via inbox" else "press rep needed"
    }
    CapabilityType.IN_HOUSE_BOOKING -> {
        val rep = world.label.reputation[ReputationCommunity.VENUE_BOOKERS] ?: 0f
        if (rep >= 0.4f) "ready to unlock via inbox" else "venue rep needed"
    }
    CapabilityType.VIDEO_PRODUCTION -> {
        if (world.label.funds >= 5_000_000L) "ready to unlock via inbox" else "\$50k needed"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun LabelOfficePreview() {
    RetroTheme {
        LabelOfficeContent(
            onOpenIdentity = {},
            world = SimWorld(
                seed = 1L,
                currentDay = 42,
                artists = mapOf(
                    "a1" to com.github.maskedkunisquat.musicmanager.logic.model.ArtistState(
                        id = "a1", name = "Wild Stars", genre = "indie-rock",
                        dimensions = com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions(0.5f, 0.5f, 0.5f, 0.7f),
                        needs = emptyMap(), activeWants = emptyList(), contractId = null
                    ),
                    "a2" to com.github.maskedkunisquat.musicmanager.logic.model.ArtistState(
                        id = "a2", name = "Bright Tides", genre = "pop",
                        dimensions = com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions(0.5f, 0.5f, 0.5f, 0.6f),
                        needs = emptyMap(), activeWants = emptyList(), contractId = null
                    )
                ),
                label = LabelState(
                    funds = 3_200_000L,
                    reputation = ReputationCommunity.entries.associateWith { 0.35f },
                    rosterIds = setOf("a1", "a2"),
                    capabilities = setOf(CapabilityType.PUBLICIST)
                ),
                market = com.github.maskedkunisquat.musicmanager.logic.model.MarketState(emptyMap()),
                contracts = emptyMap()
            ),
            onBack = {}
        )
    }
}
