package com.lists.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lists.app.data.repository.AppRepository
import com.lists.app.data.sync.DriveSyncManager
import com.lists.app.data.sync.GoogleSignInHelper
import com.lists.app.data.sync.SyncWorker
import com.lists.app.ui.screens.*
import com.lists.app.ui.theme.PureBlack
import com.lists.app.ui.theme.ListyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Background Google Drive sync check on app launch
        try {
            val syncManager = DriveSyncManager(applicationContext)
            val signInHelper = GoogleSignInHelper(applicationContext)
            if (signInHelper.getSignedInAccount() != null && syncManager.autoSyncEnabled) {
                SyncWorker.schedulePeriodicSync(applicationContext, syncManager.wifiOnlySync)
                SyncWorker.triggerOneTimeSync(applicationContext)
            }
        } catch (_: Exception) {}

        setContent {
            ListyTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PureBlack
                ) {
                    val repository = remember { AppRepository(applicationContext) }
                    AppNavigation(repository = repository)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(repository: AppRepository) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "lists"
    ) {
        // Main lists overview
        composable("lists") {
            ListsScreen(
                repository = repository,
                onNavigateToListDetail = { listId ->
                    navController.navigate("listDetail/$listId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        // List detail view
        composable(
            route = "listDetail/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.LongType })
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getLong("listId") ?: return@composable
            ListDetailScreen(
                listId = listId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToItemDetail = { itemId ->
                    navController.navigate("itemDetail/$itemId")
                },
                onNavigateToAddItem = { id ->
                    navController.navigate("addItem/$id")
                }
            )
        }

        // Add item search & manual entry
        composable(
            route = "addItem/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.LongType })
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getLong("listId") ?: return@composable
            AddItemScreen(
                listId = listId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Item detail view
        composable(
            route = "itemDetail/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            ItemDetailScreen(
                itemId = itemId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Settings view
        composable("settings") {
            SettingsScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
