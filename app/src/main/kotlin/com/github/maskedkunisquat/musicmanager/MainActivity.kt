package com.github.maskedkunisquat.musicmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.github.maskedkunisquat.musicmanager.navigation.AppNavGraph
import com.github.maskedkunisquat.musicmanager.ui.device.DeviceScreen
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModelFactory
import com.github.maskedkunisquat.musicmanager.ui.press.PressViewModel
import com.github.maskedkunisquat.musicmanager.ui.press.PressViewModelFactory
import com.github.maskedkunisquat.musicmanager.ui.theme.RetroTheme
import android.util.Log
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val inboxViewModel: InboxViewModel by viewModels {
        val app = application as AppApplication
        InboxViewModelFactory(app.simRepository, app.aiProvider.modelLoadState)
    }

    private val pressViewModel: PressViewModel by viewModels {
        val app = application as AppApplication
        PressViewModelFactory(app.dao)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RetroTheme {
                val app = application as AppApplication
                val modelLoadState by app.aiProvider.modelLoadState.collectAsStateWithLifecycle()
                val world by inboxViewModel.world.collectAsStateWithLifecycle()
                val navController = rememberNavController()
                DeviceScreen(
                    labelName = world.label.name,
                    modelLoadState = modelLoadState,
                    onDownloadModel = { app.aiProvider.downloadModel(app.modelDownloader) }
                ) {
                    AppNavGraph(
                        navController = navController,
                        viewModel = inboxViewModel,
                        pressViewModel = pressViewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as AppApplication
        lifecycleScope.launch {
            try {
                app.runCatchupIfDue()
            } catch (e: Exception) {
                Log.w(TAG, "Tick catchup failed on resume", e)
            } finally {
                inboxViewModel.refreshWorld()
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
