package com.github.maskedkunisquat.musicmanager.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem

@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onOpenEmail: (String) -> Unit,
    onBack: () -> Unit
) {
    val items by viewModel.inbox.collectAsStateWithLifecycle()
    val world by viewModel.world.collectAsStateWithLifecycle()

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
                text = "INBOX",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            val criticalCount = items.count { urgencyLevel(it.event) == UrgencyLevel.CRITICAL }
            val urgentCount = items.count { urgencyLevel(it.event) == UrgencyLevel.URGENT }
            if (criticalCount > 0) {
                Text(
                    text = "$criticalCount!!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (urgentCount > 0) {
                Text(
                    text = "$urgentCount!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = "${items.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (items.isEmpty()) {
            Text(
                text = "No messages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            val grouped = items
                .sortedByDescending { it.dayOfGame }
                .groupBy { urgencyLevel(it.event) }
            LazyColumn {
                UrgencyLevel.entries.forEach { level ->
                    val group = grouped[level] ?: return@forEach
                    item(key = "header_${level.name}") {
                        InboxSectionHeader(level = level, count = group.size)
                    }
                    items(group, key = { it.id }) { item ->
                        InboxRow(
                            subject = item.email.subject,
                            artistName = when (val e = item.event) {
                                is SimEvent.MarketShift -> e.genre
                                is SimEvent.IntelDrop -> "industry intel"
                                is SimEvent.ScoutReport -> world.scouts[e.scoutId]?.name ?: "scout"
                                is SimEvent.NegotiationRound -> world.prospects[e.prospectId]?.name ?: "prospect"
                                else -> world.artists[e.artistId]?.name ?: e.artistId.orEmpty()
                            },
                            dayOfGame = item.dayOfGame,
                            isRead = item.isRead,
                            urgency = level,
                            onClick = {
                                viewModel.markViewed(item.id)
                                onOpenEmail(item.id)
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

private enum class UrgencyLevel { CRITICAL, URGENT, ROUTINE }

@Composable
private fun InboxSectionHeader(level: UrgencyLevel, count: Int) {
    val color = when (level) {
        UrgencyLevel.CRITICAL -> MaterialTheme.colorScheme.error
        UrgencyLevel.URGENT   -> MaterialTheme.colorScheme.secondary
        UrgencyLevel.ROUTINE  -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = level.name,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "· $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun InboxRow(
    subject: String,
    artistName: String,
    dayOfGame: Int,
    isRead: Boolean,
    urgency: UrgencyLevel,
    onClick: () -> Unit
) {
    val urgencyColor: Color = when (urgency) {
        UrgencyLevel.CRITICAL -> MaterialTheme.colorScheme.error
        UrgencyLevel.URGENT   -> MaterialTheme.colorScheme.secondary
        UrgencyLevel.ROUTINE  -> Color.Unspecified
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isRead) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (urgency != UrgencyLevel.ROUTINE) urgencyColor
                            else MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = subject,
                style = MaterialTheme.typography.bodyMedium,
                color = if (urgency != UrgencyLevel.ROUTINE) urgencyColor
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "day $dayOfGame",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = artistName,
                style = MaterialTheme.typography.labelSmall,
                color = if (urgency != UrgencyLevel.ROUTINE) urgencyColor
                        else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp)
            )
            if (urgency != UrgencyLevel.ROUTINE) {
                Text(
                    text = if (urgency == UrgencyLevel.CRITICAL) "!!" else "!",
                    style = MaterialTheme.typography.labelSmall,
                    color = urgencyColor
                )
            }
        }
    }
}

private fun urgencyLevel(event: SimEvent): UrgencyLevel = when (event) {
    is SimEvent.NeedUrgent -> when {
        event.currentValue < 0.20f -> UrgencyLevel.CRITICAL
        else                       -> UrgencyLevel.URGENT
    }
    is SimEvent.DeadlineApproaching -> when {
        event.ticksRemaining <= 5  -> UrgencyLevel.CRITICAL
        event.ticksRemaining <= 10 -> UrgencyLevel.URGENT
        else                       -> UrgencyLevel.ROUTINE
    }
    is SimEvent.DeadlineMissed   -> UrgencyLevel.CRITICAL
    is SimEvent.ContractExpiring -> if (event.daysRemaining <= 7) UrgencyLevel.URGENT else UrgencyLevel.ROUTINE
    is SimEvent.LabelNeedUrgent  -> UrgencyLevel.URGENT
    is SimEvent.RivalPoach       -> UrgencyLevel.URGENT
    else                         -> UrgencyLevel.ROUTINE
}
