package com.github.maskedkunisquat.musicmanager.ui.email

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel

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

    // Trigger async options load when the item is available.
    LaunchedEffect(item) {
        item?.let { viewModel.requestOptionsFor(it) }
    }

    // Guard against navigating back during the cold-start emptyList() window.
    val hasLoaded = remember { mutableStateOf(false) }
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

    val artistName = world.artists[item.event.artistId]?.name ?: item.event.artistId
    val options = allOptions[item.id]

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
                        if (index == 0) {
                            Button(
                                onClick = {
                                    viewModel.resolveEvent(eventId, option)
                                    onBack()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) { Text(option.text) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    viewModel.resolveEvent(eventId, option)
                                    onBack()
                                },
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
}
