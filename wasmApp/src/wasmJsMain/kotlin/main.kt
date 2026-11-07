import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.lists.app.LocalWebContext
import com.lists.app.WebContext
import com.lists.app.data.model.Category
import com.lists.app.data.repository.AppRepository
import com.lists.app.data.store.WebStore
import com.lists.app.data.sync.DriveSyncManager
import com.lists.app.data.sync.GoogleDriveSync
import com.lists.app.triggerAsmrTick
import com.lists.app.ui.screens.*
import com.lists.app.ui.theme.AppAccentColor
import com.lists.app.ui.theme.AppThemeMode
import com.lists.app.ui.theme.ListsTheme
import kotlinx.browser.document
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    data class Lists(val categoryId: String? = null, val isArchive: Boolean = false) : Screen()
    data class ListDetail(val listId: Long) : Screen()
    data class AddItem(val listId: Long, val query: String? = null) : Screen()
    data class ItemDetail(val itemId: Long) : Screen()
    object Settings : Screen()
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    try {
        WebStore.init()
    } catch (e: Throwable) {
        println("WebStore.init error: ${e.message}")
    }
    try {
        GoogleDriveSync.init()
    } catch (e: Throwable) {
        println("GoogleDriveSync.init error: ${e.message}")
    }

    val body = document.body ?: run {
        println("ERROR: document.body is null")
        return
    }

    val webContext = WebContext()
    val repository = AppRepository(webContext)

    ComposeViewport(body) {
        CompositionLocalProvider(LocalWebContext provides webContext) {
            val themePrefs = remember { webContext.getSharedPreferences("lists_theme_prefs") }
            var themeMode by remember {
                val saved = themePrefs.getString("app_theme_mode", AppThemeMode.DARK.id)
                mutableStateOf(AppThemeMode.fromId(saved))
            }
            var accentColorId by remember {
                mutableStateOf(themePrefs.getString("app_accent_color", "lavender") ?: "lavender")
            }
            val accentColor = remember(accentColorId) { AppAccentColor.fromId(accentColorId) }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (key == "app_accent_color") {
                        accentColorId = prefs?.getString("app_accent_color", "lavender") ?: "lavender"
                    } else if (key == "app_theme_mode") {
                        val saved = prefs?.getString("app_theme_mode", AppThemeMode.DARK.id)
                        themeMode = AppThemeMode.fromId(saved)
                    }
                }
                themePrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { themePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val darkTheme = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.AUTO -> true
            }

            ListsTheme(darkTheme = darkTheme, accentColor = accentColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
                    val currentScreen = backStack.lastOrNull() ?: Screen.Home
                    val coroutineScope = rememberCoroutineScope()

                    var searchTriggerHome by remember { mutableStateOf(0) }
                    var searchTriggerDetail by remember { mutableStateOf(0) }
                    var isSearchActive by remember { mutableStateOf(false) }

                    LaunchedEffect(currentScreen) {
                        isSearchActive = false
                    }

                    val syncing by WebStore.syncing.collectAsState()
                    val syncStatus by DriveSyncManager.syncStatus.collectAsState()
                    val isSyncing = syncing || syncStatus is DriveSyncManager.SyncStatus.Syncing
                    val userEmail by WebStore.userEmail.collectAsState()
                    val lastSyncTime by WebStore.lastSyncTime.collectAsState()

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val wideLayout = maxWidth >= 600.dp
                        val screenWidth = maxWidth.value.toInt()
                        val screenHeight = maxHeight.value.toInt()

                        CompositionLocalProvider(
                            LocalConfiguration provides Configuration(screenWidth, screenHeight)
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                if (wideLayout) {
                                    val showBack = currentScreen is Screen.ItemDetail || currentScreen is Screen.AddItem

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
                                                        onClick = {
                                                            if (backStack.size > 1) {
                                                                backStack.removeAt(backStack.size - 1)
                                                            }
                                                        },
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
                                                            if (currentScreen is Screen.Home || currentScreen is Screen.Lists) {
                                                                searchTriggerHome++
                                                            } else if (currentScreen is Screen.ListDetail) {
                                                                searchTriggerDetail++
                                                            } else {
                                                                backStack.removeAll { it !is Screen.Home }
                                                                if (backStack.isEmpty()) {
                                                                    backStack.add(Screen.Home)
                                                                }
                                                                searchTriggerHome++
                                                            }
                                                        },
                                                        shape = CircleShape,
                                                        color = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                        modifier = Modifier.size(44.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.Search,
                                                                contentDescription = "Search",
                                                                tint = if (isSearchActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        NavigationRailItem(
                                            selected = currentScreen is Screen.Home,
                                            onClick = {
                                                if (currentScreen !is Screen.Home) {
                                                    backStack.removeAll { it !is Screen.Home }
                                                    if (backStack.isEmpty()) {
                                                        backStack.add(Screen.Home)
                                                    }
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (currentScreen is Screen.Home) Icons.Filled.Home else Icons.Outlined.Home,
                                                    contentDescription = "Home"
                                                )
                                            },
                                            label = { Text("Home") }
                                        )

                                        NavigationRailItem(
                                            selected = false,
                                            onClick = {
                                                val email = userEmail
                                                if (email != null) {
                                                    coroutineScope.launch {
                                                        triggerAsmrTick()
                                                        GoogleDriveSync.syncNow()
                                                    }
                                                } else {
                                                    if (currentScreen !is Screen.Settings) {
                                                        backStack.add(Screen.Settings)
                                                    }
                                                }
                                            },
                                            icon = {
                                                if (isSyncing) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    val isConnected = userEmail != null
                                                    val isSynced = (lastSyncTime ?: 0L) > 0 || (GoogleDriveSync.getLastSyncTime() ?: 0L) > 0
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = if (isConnected) Icons.Default.Cloud else Icons.Outlined.Cloud,
                                                            contentDescription = "Sync",
                                                            tint = if (isConnected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
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
                                            selected = currentScreen is Screen.Settings,
                                            onClick = {
                                                if (currentScreen !is Screen.Settings) {
                                                    backStack.add(Screen.Settings)
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (currentScreen is Screen.Settings) Icons.Filled.Settings else Icons.Outlined.Settings,
                                                    contentDescription = "Settings"
                                                )
                                            },
                                            label = { Text("Settings") }
                                        )

                                        Spacer(modifier = Modifier.weight(1f))

                                        val isArchive = currentScreen is Screen.Lists && currentScreen.isArchive
                                        NavigationRailItem(
                                            selected = isArchive,
                                            onClick = {
                                                backStack.add(Screen.Lists(isArchive = true))
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (isArchive) Icons.Filled.Archive else Icons.Outlined.Archive,
                                                    contentDescription = "Archive"
                                                )
                                            },
                                            label = { Text("Archive") }
                                        )

                                        NavigationRailItem(
                                            selected = false,
                                            onClick = {
                                                val nextMode = when (themeMode) {
                                                    AppThemeMode.LIGHT -> AppThemeMode.DARK
                                                    AppThemeMode.DARK -> AppThemeMode.LIGHT
                                                    AppThemeMode.AUTO -> if (darkTheme) AppThemeMode.LIGHT else AppThemeMode.DARK
                                                }
                                                themeMode = nextMode
                                                themePrefs.edit().putString("app_theme_mode", nextMode.id).apply()
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

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    when (currentScreen) {
                                        is Screen.Home -> {
                                            CategoriesHomeScreen(
                                                repository = repository,
                                                onNavigateToCategory = { cat ->
                                                    backStack.add(Screen.Lists(categoryId = cat.id))
                                                },
                                                onNavigateToArchive = {
                                                    backStack.add(Screen.Lists(isArchive = true))
                                                },
                                                onNavigateToSettings = {
                                                    backStack.add(Screen.Settings)
                                                },
                                                searchTrigger = searchTriggerHome,
                                                onSearchActiveChange = { isSearchActive = it }
                                            )
                                        }

                                        is Screen.Lists -> {
                                            ListsScreen(
                                                repository = repository,
                                                initialCategory = currentScreen.categoryId?.let { Category.fromId(it) },
                                                initialArchive = currentScreen.isArchive,
                                                onNavigateBack = {
                                                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                                },
                                                onNavigateToListDetail = { listId ->
                                                    searchTriggerDetail = 0
                                                    backStack.add(Screen.ListDetail(listId))
                                                },
                                                onNavigateToSettings = {
                                                    backStack.add(Screen.Settings)
                                                },
                                                searchTrigger = searchTriggerHome,
                                                onSearchActiveChange = { isSearchActive = it }
                                            )
                                        }

                                        is Screen.ListDetail -> {
                                            ListDetailScreen(
                                                listId = currentScreen.listId,
                                                repository = repository,
                                                onNavigateBack = {
                                                    searchTriggerDetail = 0
                                                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                                },
                                                onNavigateToItemDetail = { itemId ->
                                                    backStack.add(Screen.ItemDetail(itemId))
                                                },
                                                onNavigateToAddItem = { listId, query ->
                                                    backStack.add(Screen.AddItem(listId, query))
                                                },
                                                searchTrigger = searchTriggerDetail,
                                                onSearchActiveChange = { isSearchActive = it }
                                            )
                                        }

                                        is Screen.AddItem -> {
                                            AddItemScreen(
                                                listId = currentScreen.listId,
                                                repository = repository,
                                                initialQuery = currentScreen.query,
                                                onNavigateBack = {
                                                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                                }
                                            )
                                        }

                                        is Screen.ItemDetail -> {
                                            ItemDetailScreen(
                                                itemId = currentScreen.itemId,
                                                repository = repository,
                                                onNavigateBack = {
                                                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                                }
                                            )
                                        }

                                        is Screen.Settings -> {
                                            SettingsScreen(
                                                repository = repository,
                                                darkTheme = darkTheme,
                                                themeMode = themeMode,
                                                onThemeChange = { isDark ->
                                                    val mode = if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT
                                                    themeMode = mode
                                                    themePrefs.edit().putString("app_theme_mode", mode.id).apply()
                                                },
                                                onThemeModeChange = { mode ->
                                                    themeMode = mode
                                                    themePrefs.edit().putString("app_theme_mode", mode.id).apply()
                                                },
                                                onNavigateBack = {
                                                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
