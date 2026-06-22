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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.logic.model.LabelAesthetic
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel
import java.util.Locale

@Composable
fun LabelIdentityScreen(viewModel: InboxViewModel, onBack: () -> Unit) {
    val identity by viewModel.labelIdentity.collectAsStateWithLifecycle()
    val prevPrimary by viewModel.prevSeasonPrimaryGenre.collectAsStateWithLifecycle()
    val world by viewModel.world.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadLabelIdentity() }

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
                text = "IDENTITY",
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
            when {
                identity == null -> {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                identity!!.genreWeights.isEmpty() -> {
                    Text(
                        text = "No identity formed yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    IdentityContent(
                        identity = identity!!,
                        prevSeasonPrimary = prevPrimary,
                        seasonNumber = world.season.seasonNumber
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityContent(
    identity: LabelIdentity,
    prevSeasonPrimary: String?,
    seasonNumber: Int
) {
    // Genre affinity — top 3 by weight
    val topGenres = identity.genreWeights.entries
        .sortedByDescending { it.value }
        .take(3)

    topGenres.forEachIndexed { i, (genre, weight) ->
        Text(
            text = "${i + 1}. $genre ${weightBar(weight)} ${String.format(Locale.US, "%.2f", weight)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (i < topGenres.lastIndex) Spacer(modifier = Modifier.height(4.dp))
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(modifier = Modifier.height(12.dp))

    // Focus descriptor
    val focusLabel = when {
        identity.focusScore > 0.70f -> "FOCUSED"
        identity.focusScore > 0.40f -> "DEVELOPING"
        else -> "SCATTERED"
    }
    Text(
        text = "Focus: $focusLabel",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))

    // Aesthetic
    Text(
        text = "Aesthetic: ${identity.aesthetic.name}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )

    // Season-over-season note
    if (seasonNumber > 1 && prevSeasonPrimary != null) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Last season primary: $prevSeasonPrimary",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun weightBar(weight: Float, blocks: Int = 5): String {
    val filled = (weight * blocks + 0.5f).toInt().coerceIn(0, blocks)
    return "█".repeat(filled) + "░".repeat(blocks - filled)
}
