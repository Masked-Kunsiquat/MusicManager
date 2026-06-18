package com.github.maskedkunisquat.musicmanager.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.maskedkunisquat.musicmanager.logic.ai.ModelLoadState

@Composable
fun DeviceScreen(
    labelName: String = "Unnamed Label",
    modelLoadState: ModelLoadState = ModelLoadState.IDLE,
    onDownloadModel: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DeviceStatusBar(labelName = labelName)
        ModelStateBanner(state = modelLoadState, onDownload = onDownloadModel)
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun DeviceStatusBar(labelName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = labelName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "●●●",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModelStateBanner(state: ModelLoadState, onDownload: () -> Unit) {
    val (message, showButton) = when (state) {
        ModelLoadState.IDLE -> "AI model not downloaded" to true
        ModelLoadState.DOWNLOADING -> "Downloading ${com.github.maskedkunisquat.musicmanager.ai.GemmaModelConfig.modelFilename()}…" to false
        ModelLoadState.LOADING -> "Loading AI model…" to false
        ModelLoadState.READY -> return  // nothing to show
        ModelLoadState.ERROR -> "Model error — tap to retry" to true
    }
    val buttonText = if (state == ModelLoadState.ERROR) "Retry" else "Download"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (showButton) {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(buttonText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
