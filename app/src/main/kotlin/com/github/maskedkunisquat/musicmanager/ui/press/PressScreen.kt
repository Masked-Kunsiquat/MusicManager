package com.github.maskedkunisquat.musicmanager.ui.press

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.musicmanager.ui.theme.RetroTheme

@Composable
fun PressScreen(viewModel: PressViewModel, onBack: () -> Unit) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    PressContent(feed = feed, onBack = onBack)
}

@Composable
private fun PressContent(feed: List<PressItem>, onBack: () -> Unit) {
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
                text = "PRESS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (feed.isEmpty()) {
            Text(
                text = "no intel yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn {
                items(feed, key = { it.id }) { item ->
                    PressRow(item)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun PressRow(item: PressItem) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = item.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "day ${item.dayOfGame}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PressContentPreview() {
    RetroTheme {
        PressContent(
            feed = listOf(
                PressItem("1", "// industry intel: indie-folk momentum stalling", "Sources close to the scene suggest the indie-folk wave is losing steam after an 18-month run. Labels chasing the trend may find themselves overexposed.", 10),
                PressItem("2", "// industry intel: hyperpop crossover moment building", "Early streaming data points to a hyperpop breakout in mainstream markets. The window is narrow — history suggests 6-8 weeks before saturation.", 8),
                PressItem("3", "// industry intel: regional rap scene fragmenting", "What looked like a unified regional sound is splintering into micro-genres. Harder to market, but artists with a clear lane are outperforming the pack.", 6),
            ),
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PressContentEmptyPreview() {
    RetroTheme {
        PressContent(feed = emptyList(), onBack = {})
    }
}
