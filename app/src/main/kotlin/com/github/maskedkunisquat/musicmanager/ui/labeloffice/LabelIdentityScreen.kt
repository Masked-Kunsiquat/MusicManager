package com.github.maskedkunisquat.musicmanager.ui.labeloffice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
            val identityVal = identity
            when {
                identityVal == null -> {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                identityVal.primaryGenre == null -> {
                    Text(
                        text = "No identity formed yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sign artists to anchor your roster, pursue or pass leads in the tape deck, and respond to market events — your sound takes shape from all of it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    IdentityContent(
                        identity = identityVal,
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
    // Aesthetic headline
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = identity.aesthetic.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        identity.primaryGenre?.let { genre ->
            Text(
                text = "  ·  $genre",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = aestheticDescription(identity.aesthetic),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(modifier = Modifier.height(12.dp))

    // Genre affinity — top 3 positive-weight genres
    Text(
        text = "GENRE AFFINITY",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    val topGenres = identity.genreWeights.entries
        .filter { it.value > 0f }
        .sortedByDescending { it.value }
        .take(3)
    topGenres.forEachIndexed { i, (genre, weight) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyMedium,
                color = if (i == 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = weightBar(weight),
                style = MaterialTheme.typography.bodyMedium,
                color = if (i == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = weightLabel(weight),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp)
            )
        }
        if (i < topGenres.lastIndex) Spacer(modifier = Modifier.height(6.dp))
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(modifier = Modifier.height(12.dp))

    // Focus
    val focusLabel = when {
        identity.focusScore > 0.70f -> "FOCUSED"
        identity.focusScore > 0.40f -> "DEVELOPING"
        else -> "SCATTERED"
    }
    Text(
        text = "FOCUS: $focusLabel",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = focusImplication(focusLabel, identity.primaryGenre),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Season-over-season note
    if (seasonNumber > 1 && prevSeasonPrimary != null) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(12.dp))
        val arrow = if (prevSeasonPrimary == identity.primaryGenre) "→" else "↗"
        Text(
            text = "Last season: $prevSeasonPrimary  $arrow  ${identity.primaryGenre ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun aestheticDescription(aesthetic: LabelAesthetic): String = when (aesthetic) {
    LabelAesthetic.UNDERGROUND   -> "Distinct niche, loyal audience — not chasing mainstream placement."
    LabelAesthetic.MAINSTREAM    -> "Broad commercial appeal — high visibility, high competition."
    LabelAesthetic.EXPERIMENTAL  -> "Volatile and unpredictable — high creative risk, high reward ceiling."
    LabelAesthetic.ECLECTIC      -> "Genre-fluid roster — versatile but without a defining identity yet."
}

private fun weightLabel(weight: Float): String = when {
    weight > 0.70f -> "dominant"
    weight > 0.50f -> "strong"
    weight > 0.30f -> "developing"
    else           -> "exploring"
}

private fun focusImplication(focusLabel: String, primaryGenre: String?): String = when (focusLabel) {
    "FOCUSED"    -> "Scouts and intel are biasing toward ${primaryGenre ?: "your primary genre"} this season."
    "DEVELOPING" -> "Identity is forming — more actions in the same genre will sharpen your focus."
    else         -> "No clear direction yet — every genre competes for the same attention."
}

private fun weightBar(weight: Float, blocks: Int = 5): String {
    val filled = (weight * blocks + 0.5f).toInt().coerceIn(0, blocks)
    return "█".repeat(filled) + "░".repeat(blocks - filled)
}
