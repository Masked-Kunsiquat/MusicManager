package com.github.maskedkunisquat.musicmanager.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.maskedkunisquat.musicmanager.ui.charts.ChartsScreen
import com.github.maskedkunisquat.musicmanager.ui.rivalintel.RivalIntelScreen
import com.github.maskedkunisquat.musicmanager.ui.contacts.ContactsScreen
import com.github.maskedkunisquat.musicmanager.ui.email.EmailDetailScreen
import com.github.maskedkunisquat.musicmanager.ui.home.HomeScreen
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxScreen
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel
import com.github.maskedkunisquat.musicmanager.ui.labeloffice.LabelIdentityScreen
import com.github.maskedkunisquat.musicmanager.ui.labeloffice.LabelOfficeScreen
import com.github.maskedkunisquat.musicmanager.ui.press.PressScreen
import com.github.maskedkunisquat.musicmanager.ui.recap.SeasonRecapScreen
import com.github.maskedkunisquat.musicmanager.ui.tapedeck.TapeDeckScreen
import com.github.maskedkunisquat.musicmanager.ui.press.PressViewModel

object Route {
    const val HOME = "home"
    const val INBOX = "inbox"
    const val CHARTS = "charts"
    const val PRESS = "press"
    const val LABEL_OFFICE = "label_office"
    const val TAPE_DECK = "tape_deck"
    const val CONTACTS = "contacts"
    const val RIVAL_INTEL = "rival_intel"
    const val SEASON_RECAP = "season_recap"
    const val LABEL_IDENTITY = "label_identity"
    const val EMAIL_DETAIL = "email/{eventId}"
    fun emailDetail(eventId: String) = "email/$eventId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: InboxViewModel,
    pressViewModel: PressViewModel
) {
    val showRecap by viewModel.showRecap.collectAsStateWithLifecycle()
    LaunchedEffect(showRecap) {
        if (showRecap) navController.navigate(Route.SEASON_RECAP) { launchSingleTop = true }
    }

    NavHost(navController = navController, startDestination = Route.HOME) {
        composable(Route.HOME) {
            HomeScreen(
                onOpenInbox = { navController.navigate(Route.INBOX) },
                onOpenCharts = { navController.navigate(Route.CHARTS) },
                onOpenPress = { navController.navigate(Route.PRESS) },
                onOpenLabelOffice = { navController.navigate(Route.LABEL_OFFICE) },
                onOpenTapeDeck = { navController.navigate(Route.TAPE_DECK) },
                onOpenContacts = { navController.navigate(Route.CONTACTS) },
                onOpenRivalIntel = { navController.navigate(Route.RIVAL_INTEL) },
                viewModel = viewModel
            )
        }
        composable(Route.CONTACTS) {
            ContactsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Route.RIVAL_INTEL) {
            RivalIntelScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Route.TAPE_DECK) {
            TapeDeckScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Route.LABEL_OFFICE) {
            LabelOfficeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenIdentity = { navController.navigate(Route.LABEL_IDENTITY) }
            )
        }
        composable(Route.LABEL_IDENTITY) {
            LabelIdentityScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Route.CHARTS) {
            ChartsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Route.PRESS) {
            PressScreen(viewModel = pressViewModel, onBack = { navController.popBackStack() })
        }
        composable(Route.INBOX) {
            InboxScreen(
                viewModel = viewModel,
                onOpenEmail = { eventId -> navController.navigate(Route.emailDetail(eventId)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.SEASON_RECAP) {
            SeasonRecapScreen(
                viewModel = viewModel,
                onStartNewSeason = {
                    viewModel.startNewSeason()
                    navController.navigate(Route.HOME) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = Route.EMAIL_DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            EmailDetailScreen(
                eventId = eventId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
