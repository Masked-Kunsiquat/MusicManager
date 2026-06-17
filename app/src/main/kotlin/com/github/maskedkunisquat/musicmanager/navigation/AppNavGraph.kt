package com.github.maskedkunisquat.musicmanager.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.maskedkunisquat.musicmanager.ui.email.EmailDetailScreen
import com.github.maskedkunisquat.musicmanager.ui.home.HomeScreen
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxScreen
import com.github.maskedkunisquat.musicmanager.ui.inbox.InboxViewModel

object Route {
    const val HOME = "home"
    const val INBOX = "inbox"
    const val EMAIL_DETAIL = "email/{eventId}"
    fun emailDetail(eventId: String) = "email/$eventId"
}

@Composable
fun AppNavGraph(navController: NavHostController, viewModel: InboxViewModel) {
    NavHost(navController = navController, startDestination = Route.HOME) {
        composable(Route.HOME) {
            HomeScreen(onOpenInbox = { navController.navigate(Route.INBOX) })
        }
        composable(Route.INBOX) {
            InboxScreen(
                viewModel = viewModel,
                onOpenEmail = { eventId -> navController.navigate(Route.emailDetail(eventId)) },
                onBack = { navController.popBackStack() }
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
