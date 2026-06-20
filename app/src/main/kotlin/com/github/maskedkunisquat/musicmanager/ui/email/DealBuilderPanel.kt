package com.github.maskedkunisquat.musicmanager.ui.email

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.CreativeControl
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.ui.components.RetroButton

private enum class SplitStep(
    val artistPercent: Int,
    val chipLabel: String,
    val upfrontCents: Long,
    val rcDelta: Float
) {
    A80(80, "80/20", 0L, 0f),
    A70(70, "70/30", 50_000L, 0f),
    A60(60, "60/40", 100_000L, -0.05f),
    A50(50, "50/50", 150_000L, -0.10f)
}

private enum class DealPriority(
    val chipLabel: String,
    val needType: NeedType,
    val needDelta: Float,
    val extraCostCents: Long,
    val forceFullArtistControl: Boolean
) {
    CREATIVE_FREEDOM("CREATIVE", NeedType.CREATIVE_FULFILLMENT, +0.15f, 0L, true),
    TOURING_BUDGET("TOURING", NeedType.RECOGNITION, +0.15f, 80_000L, false),
    MARKETING_SPEND("MARKETING", NeedType.FINANCIAL_SECURITY, +0.10f, 50_000L, false)
}

private fun buildDealOption(
    event: SimEvent.RenewalOpened,
    split: SplitStep,
    priority: DealPriority?
): ResponseOption {
    val creativeControl = if (priority?.forceFullArtistControl == true) CreativeControl.FULL_ARTIST else CreativeControl.SHARED
    val effects = buildList<StateEffect> {
        add(StateEffect.RenewContract(
            artistId = event.artistId,
            newExpiryTicks = 180,
            revenueSplit = RevenueSplit(split.artistPercent),
            creativeControl = creativeControl
        ))
        priority?.let { add(StateEffect.NeedChange(event.artistId, it.needType, it.needDelta)) }
        if (split.rcDelta != 0f) add(StateEffect.RelationshipChange(event.artistId, split.rcDelta))
    }
    val label = split.chipLabel + (priority?.let { " + ${it.chipLabel.lowercase()}" } ?: "")
    return ResponseOption(
        id = "deal_builder:${event.artistId}:${event.round}",
        text = "Offer $label split, 180-tick term",
        effects = effects,
        costFunds = split.upfrontCents + (priority?.extraCostCents ?: 0L)
    )
}

private fun buildWalkOption(event: SimEvent.RenewalOpened): ResponseOption = ResponseOption(
    id = "renewal_walk:${event.artistId}:${event.round}",
    text = "Walk away from talks",
    effects = listOf(StateEffect.RenewalWalked(event.artistId)),
    costFunds = 0L
)

@Composable
fun DealBuilderPanel(
    event: SimEvent.RenewalOpened,
    labelFunds: Long,
    onResolve: (ResponseOption) -> Unit
) {
    var selectedSplit by remember { mutableStateOf(SplitStep.A70) }
    var selectedPriority by remember { mutableStateOf<DealPriority?>(null) }

    val totalCostCents = selectedSplit.upfrontCents + (selectedPriority?.extraCostCents ?: 0L)
    val canAfford = labelFunds >= totalCostCents

    Column {
        Text(
            text = "REVENUE SPLIT (ARTIST / LABEL)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SplitStep.entries.forEach { step ->
                RetroButton(
                    onClick = { selectedSplit = step },
                    filled = selectedSplit == step,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(step.chipLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "DEAL PRIORITY (OPTIONAL)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DealPriority.entries.forEach { priority ->
                RetroButton(
                    onClick = {
                        selectedPriority = if (selectedPriority == priority) null else priority
                    },
                    filled = selectedPriority == priority,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(priority.chipLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (totalCostCents > 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "UPFRONT: \$${totalCostCents / 100}",
                style = MaterialTheme.typography.labelSmall,
                color = if (canAfford) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        RetroButton(
            onClick = { onResolve(buildDealOption(event, selectedSplit, selectedPriority)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            enabled = canAfford
        ) {
            Text("CONFIRM DEAL")
        }

        RetroButton(
            onClick = { onResolve(buildWalkOption(event)) },
            filled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("WALK AWAY FROM TALKS")
        }
    }
}
