package com.github.maskedkunisquat.musicmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.github.maskedkunisquat.musicmanager.navigation.AppNavGraph
import com.github.maskedkunisquat.musicmanager.ui.device.DeviceScreen
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModelFactory
import com.github.maskedkunisquat.musicmanager.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val inboxViewModel: InboxViewModel by viewModels {
        InboxViewModelFactory((application as AppApplication).simRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val app = application as AppApplication
                val modelLoadState by app.aiProvider.modelLoadState.collectAsStateWithLifecycle()
                val navController = rememberNavController()
                DeviceScreen(
                    labelName = "Untitled Label",
                    modelLoadState = modelLoadState,
                    onDownloadModel = { app.aiProvider.downloadModel(app.modelDownloader) }
                ) {
                    AppNavGraph(navController = navController, viewModel = inboxViewModel)
                }
            }
        }
    }
}
