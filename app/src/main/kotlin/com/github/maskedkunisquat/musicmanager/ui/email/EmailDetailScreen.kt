package com.github.maskedkunisquat.musicmanager.ui.email

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.ui.components.RetroButton
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel

private fun ResponseOption.needsPartnerPick(): Boolean =
    effects.any { it is StateEffect.PairedNeedChange && it.partnerId.isBlank() }

private fun ResponseOption.withPartner(partnerId: String): ResponseOption = copy(
    effects = effects.map {
        if (it is StateEffect.PairedNeedChange && it.partnerId.isBlank()) it.copy(partnerId = partnerId)
        else it
    }
)

@Composable
fun EmailDetailScreen(
    eventId: String,
    viewModel: InboxViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.inbox.collectAsStateWithLifecycle()
    val world by viewModel.world.collectAsStateWithLifecycle()
    val allOptions by viewModel.options.collectAsStateWithLifecycle()
    val item = items.find { it.id == eventId }

    LaunchedEffect(item) {
        // RenewalOpened events are handled by DealBuilderPanel — no AI options needed.
        item?.let { if (it.event !is SimEvent.RenewalOpened) viewModel.requestOptionsFor(it) }
    }

    val hasLoaded = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty()) hasLoaded.value = true }
    LaunchedEffect(item) {
        if (item == null && hasLoaded.value) onBack()
    }

    if (item == null) {
        Text(
            text = "Loading…",
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val artistName = when (val e = item.event) {
        is SimEvent.MarketShift -> e.genre
        is SimEvent.IntelDrop -> "industry intel"
        is SimEvent.ScoutReport -> world.scouts[e.scoutId]?.name ?: "scout"
        is SimEvent.NegotiationRound -> world.prospects[e.prospectId]?.name ?: "prospect"
        else -> world.artists[e.artistId]?.name ?: e.artistId.orEmpty()
    }
    val eventArtistId: String? = item.event.artistId
    val options = allOptions[item.id]

    var pickerFor by remember { mutableStateOf<ResponseOption?>(null) }
    BackHandler(enabled = pickerFor != null) { pickerFor = null }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    text = artistName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = item.email.subject,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = item.email.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Column(modifier = Modifier.padding(12.dp)) {
                val renewalEvent = item.event as? SimEvent.RenewalOpened
                if (renewalEvent != null) {
                    DealBuilderPanel(
                        event = renewalEvent,
                        labelFunds = world.label.funds,
                        onResolve = { option ->
                            viewModel.resolveEvent(eventId, option)
                            // Navigation is handled by the LaunchedEffect that fires when item
                            // disappears from the inbox after the DB write propagates.
                        }
                    )
                } else {
                    when {
                        options == null -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(8.dp)
                            )
                        }
                        options.isEmpty() -> Unit
                        else -> {
                            options.forEachIndexed { index, option ->
                                RetroButton(
                                    onClick = {
                                        if (option.needsPartnerPick()) {
                                            pickerFor = option
                                        } else {
                                            viewModel.resolveEvent(eventId, option)
                                            // Navigation handled by LaunchedEffect on item disappearing.
                                        }
                                    },
                                    filled = index == 0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) { Text(option.text) }
                            }
                        }
                    }
                }
            }
        }

        if (pickerFor != null) {
            PartnerPicker(
                artists = world.artists.filter { it.key != eventArtistId },
                onPick = { artistId ->
                    val final = pickerFor!!.withPartner(artistId)
                    viewModel.resolveEvent(eventId, final)
                    pickerFor = null
                    // Navigation handled by LaunchedEffect(item) when the item disappears.
                },
                onDismiss = { pickerFor = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PartnerPicker(
    artists: Map<String, ArtistState>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "SELECT PARTNER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (artists.isEmpty()) {
            Text(
                text = "no other artists on the roster",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn {
                items(artists.entries.toList(), key = { it.key }) { (id, artist) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = artist.genre,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
