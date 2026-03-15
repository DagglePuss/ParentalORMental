package com.parentalormente.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.parentalormente.data.prefs.AppPreferences
import com.parentalormente.ui.screens.DashboardScreen
import com.parentalormente.ui.screens.IncidentDetailScreen
import com.parentalormente.ui.screens.SetupScreen
import com.parentalormente.ui.screens.SettingsScreen

@Composable
fun ParentalNavHost() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    // null = still loading from DataStore, false/true = resolved
    val setupComplete by prefs.setupComplete.collectAsState(initial = null)

    val resolved = setupComplete
    if (resolved == null) {
        // Brief loading while DataStore reads from disk
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (resolved) "dashboard" else "setup"
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable("setup") {
            SetupScreen(onSetupComplete = {
                navController.navigate("dashboard") {
                    popUpTo("setup") { inclusive = true }
                }
            })
        }
        composable("dashboard") {
            DashboardScreen(
                onIncidentClick = { id -> navController.navigate("incident/$id") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("incident/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            IncidentDetailScreen(incidentId = id, onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
