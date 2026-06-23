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
                label = "Balance",
                value = formatDollars(world.label.funds)
            )
            Spacer(modifier = Modifier.height(6.dp))
            val cashScore = LabelNeedEvaluator.cashFlow(world)
            StatusLine(
                label = "Cash flow",
                value = cashFlowLabel(cashScore),
                critical = cashScore < 0.35f
            )
            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("ROSTER")
            StatusLine(
                label = "Size",
                value = "${world.label.rosterIds.size} artist${if (world.label.rosterIds.size == 1) "" else "s"}"
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatusLine(
                label = "Diversity",
                value = rosterLabel(world)
            )
            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("CAPABILITIES")
            CapabilityType.entries.forEach { cap ->
                CapabilityRow(type = cap, world = world)
                Spacer(modifier = Modifier.height(12.dp))
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
private fun CapabilityRow(type: CapabilityType, world: SimWorld) {
    val unlocked = type in world.label.capabilities
    val gateInfo = if (!unlocked) gateInfo(type, world) else null
    val ready = gateInfo?.second ?: false

    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = capabilityLabel(type),
                style = MaterialTheme.typography.titleSmall,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = capabilityDescription(type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!unlocked && gateInfo != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = gateInfo.first,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ready) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 8.dp))
        Text(
            text = if (unlocked) "ACTIVE" else if (ready) "READY" else "LOCKED",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                unlocked -> MaterialTheme.colorScheme.primary
                ready    -> MaterialTheme.colorScheme.secondary
                else     -> MaterialTheme.colorScheme.outline
            }
        )
    }
}

private fun formatDollars(cents: Long): String {
    val dollars = cents / 100L
    return "\$${String.format(java.util.Locale.US, "%,d", dollars)}"
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
    CapabilityType.PUBLICIST        -> "Publicist"
    CapabilityType.IN_HOUSE_BOOKING -> "In-House Booking"
    CapabilityType.VIDEO_PRODUCTION -> "Video Production"
}

private fun capabilityDescription(type: CapabilityType): String = when (type) {
    CapabilityType.PUBLICIST ->
        "Press pitches on intel + market events. Extra options when artists need recognition or face a press-cycle deadline."
    CapabilityType.IN_HOUSE_BOOKING ->
        "Lock in dates when trends peak. Extra options for recognition needs and tour-booking deadlines."
    CapabilityType.VIDEO_PRODUCTION ->
        "In-house visuals on demand. Extra option when artists need creative fulfilment."
}

// Returns (display text, isReady) — used for lock status and gate progress.
private fun gateInfo(type: CapabilityType, world: SimWorld): Pair<String, Boolean> = when (type) {
    CapabilityType.PUBLICIST -> {
        val rep = world.label.reputation[ReputationCommunity.PRESS] ?: 0f
        val ready = rep >= 0.4f
        val text = if (ready) "ready to unlock — watch for inbox offer"
                   else "press rep: ${"%.2f".format(rep)} / 0.40 needed"
        text to ready
    }
    CapabilityType.IN_HOUSE_BOOKING -> {
        val rep = world.label.reputation[ReputationCommunity.VENUE_BOOKERS] ?: 0f
        val ready = rep >= 0.4f
        val text = if (ready) "ready to unlock — watch for inbox offer"
                   else "venue rep: ${"%.2f".format(rep)} / 0.40 needed"
        text to ready
    }
    CapabilityType.VIDEO_PRODUCTION -> {
        val ready = world.label.funds >= 5_000_000L
        val dollars = world.label.funds / 100L
        val text = if (ready) "ready to unlock — watch for inbox offer"
                   else "funds: \$${dollars / 1000}k / \$50k needed"
        text to ready
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
