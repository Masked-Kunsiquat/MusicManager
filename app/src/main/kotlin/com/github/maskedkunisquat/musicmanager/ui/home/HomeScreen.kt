package com.github.maskedkunisquat.musicmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.musicmanager.ui.theme.RetroTheme

@Composable
fun HomeScreen(
    onOpenInbox: () -> Unit,
    onOpenCharts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DASHBOARD",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onOpenInbox, modifier = Modifier.fillMaxWidth()) {
            Text("INBOX")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onOpenCharts, modifier = Modifier.fillMaxWidth()) {
            Text("CHARTS")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RetroTheme {
        HomeScreen(onOpenInbox = {}, onOpenCharts = {})
    }
}
