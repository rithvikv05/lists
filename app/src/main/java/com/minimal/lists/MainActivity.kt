package com.minimal.lists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.minimal.lists.data.repository.AppRepository
import com.minimal.lists.data.sync.DriveSyncManager
import com.minimal.lists.data.sync.GoogleSignInHelper
import com.minimal.lists.data.sync.SyncWorker
import com.minimal.lists.ui.screens.*
import com.minimal.lists.ui.theme.AppAccentColor
import com.minimal.lists.ui.theme.AppThemeMode
import com.minimal.lists.ui.theme.ListsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // High-performance image loading pipeline: 25% RAM cache, 150MB disk cache, connection pool & browser User-Agent
        try {
            val coilClient = OkHttpClient.Builder()
                .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
                .dispatcher(Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 16
                })
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val imageLoader = ImageLoader.Builder(applicationContext)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(applicationContext.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(150L * 1024 * 1024)
                        .build()
                }
                .okHttpClient(coilClient)
                .respectCacheHeaders(false)
                .allowHardware(true)
                .crossfade(true)
                .build()

            Coil.setImageLoader(imageLoader)
        } catch (_: Exception) {}

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
            val systemDarkTheme = isSystemInDarkTheme()
            val themePrefs = remember { getSharedPreferences("lists_theme_prefs", android.content.Context.MODE_PRIVATE) }
            var themeMode by remember {
                val saved = themePrefs.getString("app_theme_mode", AppThemeMode.AUTO.id)
                mutableStateOf(AppThemeMode.fromId(saved))
            }
            val darkTheme = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.AUTO -> systemDarkTheme
            }

            var accentColorId by remember { mutableStateOf(themePrefs.getString("app_accent_color", "lavender") ?: "lavender") }
            val accentColor = remember(accentColorId) { AppAccentColor.fromId(accentColorId) }

            DisposableEffect(Unit) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (key == "app_accent_color") {
                        accentColorId = prefs.getString("app_accent_color", "lavender") ?: "lavender"
                    } else if (key == "app_theme_mode") {
                        val saved = prefs.getString("app_theme_mode", AppThemeMode.AUTO.id)
                        themeMode = AppThemeMode.fromId(saved)
                    }
                }
                themePrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { themePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            ListsTheme(darkTheme = darkTheme, accentColor = accentColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val repository = remember { AppRepository(applicationContext) }

                    AppNavigation(
                        repository = repository,
                        darkTheme = darkTheme,
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            themePrefs.edit().putString("app_theme_mode", mode.id).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    repository: AppRepository,
    darkTheme: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val wideLayout = isTablet
    var searchTriggerHome by remember { mutableStateOf(0) }
    var searchTriggerDetail by remember { mutableStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncManager = remember { DriveSyncManager(context) }
    val signInHelper = remember { GoogleSignInHelper(context) }
    val syncStatus by DriveSyncManager.syncStatus.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        if (wideLayout) {
            val showBack = currentRoute?.startsWith("itemDetail") == true || currentRoute?.startsWith("addItem") == true
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                header = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(top = 16.dp, bottom = 4.dp)
                            .size(44.dp)
                    ) {
                        if (showBack) {
                            Surface(
                                onClick = { navController.popBackStack() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = {
                                    if (currentRoute == "lists") {
                                        searchTriggerHome++
                                    } else if (currentRoute?.startsWith("listDetail") == true) {
                                        searchTriggerDetail++
                                    } else {
                                        navController.navigate("lists") {
                                            popUpTo("lists") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                        searchTriggerHome++
                                    }
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            ) {
                NavigationRailItem(
                    selected = currentRoute == "lists",
                    onClick = {
                        if (currentRoute != "lists") {
                            navController.navigate("lists") {
                                popUpTo("lists") {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == "lists") Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Outlined.List,
                            contentDescription = "Lists"
                        )
                    },
                    label = { Text("Lists") }
                )
                NavigationRailItem(
                    selected = false,
                    onClick = {
                        val account = signInHelper.getSignedInAccount()
                        if (account != null) {
                            coroutineScope.launch {
                                com.minimal.lists.ui.components.triggerAsmrTick(context)
                                syncManager.syncNow(account, repository)
                            }
                        } else {
                            navController.navigate("settings")
                        }
                    },
                    icon = {
                        if (syncStatus is DriveSyncManager.SyncStatus.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            val isConnected = signInHelper.getSignedInAccount() != null
                            val isSynced = syncManager.lastSyncTimestamp > 0 || syncStatus is DriveSyncManager.SyncStatus.Success
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "Sync",
                                    tint = if (isConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                )
                                if (isConnected && isSynced) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-2).dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676))
                                    )
                                }
                            }
                        }
                    },
                    label = { Text("Sync") }
                )
                NavigationRailItem(
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == "settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") }
                )

                // Align Light/Dark mode button to the bottom of the sidebar
                Spacer(modifier = Modifier.weight(1f))

                NavigationRailItem(
                    selected = false,
                    onClick = {
                        val nextMode = when (themeMode) {
                            AppThemeMode.LIGHT -> AppThemeMode.DARK
                            AppThemeMode.DARK -> AppThemeMode.LIGHT
                            AppThemeMode.AUTO -> if (darkTheme) AppThemeMode.LIGHT else AppThemeMode.DARK
                        }
                        onThemeModeChange(nextMode)
                    },
                    icon = {
                        Icon(
                            imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme"
                        )
                    },
                    label = { Text(if (darkTheme) "Light" else "Dark") },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        NavHost(
            navController = navController,
            startDestination = "lists",
            modifier = Modifier.weight(1f)
        ) {
            // Main collections overview
            composable("lists") {
                ListsScreen(
                    repository = repository,
                    onNavigateToListDetail = { listId ->
                        navController.navigate("listDetail/$listId")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
                    searchTrigger = searchTriggerHome
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
                    onNavigateToAddItem = { id, query ->
                        if (query.isNullOrBlank()) {
                            navController.navigate("addItem/$id")
                        } else {
                            navController.navigate("addItem/$id?query=${android.net.Uri.encode(query)}")
                        }
                    },
                    searchTrigger = searchTriggerDetail
                )
            }

            // Add item search & manual entry
            composable(
                route = "addItem/{listId}?query={query}",
                arguments = listOf(
                    navArgument("listId") { type = NavType.LongType },
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val listId = backStackEntry.arguments?.getLong("listId") ?: return@composable
                val query = backStackEntry.arguments?.getString("query")
                AddItemScreen(
                    listId = listId,
                    repository = repository,
                    initialQuery = query,
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
                    darkTheme = darkTheme,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
