package com.lists.app.ui.screens

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.lists.app.data.model.Category
import com.lists.app.data.model.HomeLayoutMode
import com.lists.app.data.repository.AppRepository
import com.lists.app.data.store.WebStore
import com.lists.app.data.sync.DriveSyncManager
import com.lists.app.data.sync.GoogleDriveSync
import com.lists.app.statusBarsPadding
import com.lists.app.navigationBarsPadding
import com.lists.app.PullToRefreshBox
import com.lists.app.triggerAsmrTick
import com.lists.app.triggerStrongTick
import com.lists.app.ui.components.ListsSearchBar
import com.lists.app.ui.components.NewListModal

@Composable
fun CategoriesHomeScreen(
    repository: AppRepository,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    searchTrigger: Int = 0,
    onSearchActiveChange: ((Boolean) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listsWithStats by repository.allListsWithStats.collectAsState(initial = emptyList())
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWideLayout = screenWidthDp >= 600

    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("lists_ui_prefs", Context.MODE_PRIVATE) }
    var homeLayoutMode by remember {
        val saved = uiPrefs.getString("home_layout_mode", HomeLayoutMode.BENTO.name)
        mutableStateOf(
            try { HomeLayoutMode.valueOf(saved ?: HomeLayoutMode.BENTO.name) }
            catch (_: Exception) { HomeLayoutMode.BENTO }
        )
    }
    val isGridView = homeLayoutMode == HomeLayoutMode.BENTO

    DisposableEffect(uiPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "home_layout_mode") {
                val saved = prefs?.getString("home_layout_mode", HomeLayoutMode.BENTO.name)
                homeLayoutMode = try { HomeLayoutMode.valueOf(saved ?: HomeLayoutMode.BENTO.name) }
                catch (_: Exception) { HomeLayoutMode.BENTO }
            }
        }
        uiPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            uiPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var showCreateSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var lastTrigger by remember { mutableStateOf(searchTrigger) }
    LaunchedEffect(searchTrigger) {
        if (searchTrigger != lastTrigger) {
            lastTrigger = searchTrigger
            showSearchBar = !showSearchBar
            if (!showSearchBar) {
                focusManager.clearFocus()
                searchQuery = ""
            }
        }
    }

    LaunchedEffect(showSearchBar) {
        onSearchActiveChange?.invoke(showSearchBar)
    }

    // Google Drive Sync
    var isRefreshing by remember { mutableStateOf(false) }
    val syncing by WebStore.syncing.collectAsState()
    val syncStatus by DriveSyncManager.syncStatus.collectAsState()
    val isSyncing = syncing || syncStatus is DriveSyncManager.SyncStatus.Syncing
    val userEmail by WebStore.userEmail.collectAsState()
    val lastSyncTime by WebStore.lastSyncTime.collectAsState()

    val onPullRefresh: () -> Unit = {
        isRefreshing = true
        triggerStrongTick()
        scope.launch {
            GoogleDriveSync.syncNow()
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        val saved = uiPrefs.getString("home_layout_mode", HomeLayoutMode.BENTO.name)
        homeLayoutMode = try { HomeLayoutMode.valueOf(saved ?: HomeLayoutMode.BENTO.name) }
        catch (_: Exception) { HomeLayoutMode.BENTO }
    }

    // Counts per category (only non-archived lists)
    val categoryCounts = remember(listsWithStats) {
        val map = mutableMapOf<Category, Int>()
        for (item in listsWithStats) {
            if (!item.list.isArchived) {
                val cat = Category.fromId(item.list.category)
                map[cat] = (map[cat] ?: 0) + 1
            }
        }
        map
    }

    val archiveCount = remember(listsWithStats) {
        listsWithStats.count { it.list.isArchived }
    }

    // Only categories with > 0 lists, matching search query if typed
    val visibleCategories = remember(categoryCounts, searchQuery) {
        Category.entries.filter { cat ->
            val count = categoryCounts[cat] ?: 0
            val matchesQuery = searchQuery.isBlank() || cat.displayName.contains(searchQuery.trim(), ignoreCase = true)
            count > 0 && matchesQuery
        }
    }

    val horizontalPadding = if (isWideLayout) 24.dp else 16.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (showSearchBar) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        showSearchBar = false
                        searchQuery = ""
                    }
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
        ) {
            // Header with Lists title, sync, search, and settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 8.dp)
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lists",
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isWideLayout) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Cloud Sync Button
                        IconButton(
                            onClick = {
                                val email = userEmail
                                if (email != null) {
                                    scope.launch {
                                        triggerAsmrTick()
                                        GoogleDriveSync.syncNow()
                                    }
                                } else {
                                    onNavigateToSettings?.invoke()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    val isConnected = userEmail != null
                                    val isSynced = (lastSyncTime ?: 0L) > 0 || (GoogleDriveSync.getLastSyncTime() ?: 0L) > 0
                                    Icon(
                                        imageVector = if (isConnected) Icons.Default.Cloud else Icons.Outlined.Cloud,
                                        contentDescription = "Sync",
                                        tint = if (isConnected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (isConnected && isSynced) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676))
                                                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        )
                                    }
                                }
                            }
                        }

                        // Search Button
                        IconButton(
                            onClick = {
                                triggerAsmrTick()
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) {
                                    focusManager.clearFocus()
                                    searchQuery = ""
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (showSearchBar || searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Settings Button
                        if (onNavigateToSettings != null) {
                            IconButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Desktop/Tablet Grid vs List view toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Surface(
                                onClick = {
                                    homeLayoutMode = HomeLayoutMode.BENTO
                                    uiPrefs.edit().putString("home_layout_mode", HomeLayoutMode.BENTO.name).apply()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isGridView) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Grid View",
                                        tint = if (isGridView) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    homeLayoutMode = HomeLayoutMode.DISCRETE
                                    uiPrefs.edit().putString("home_layout_mode", HomeLayoutMode.DISCRETE.name).apply()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (!isGridView) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ViewAgenda,
                                        contentDescription = "List View",
                                        tint = if (!isGridView) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Search Bar
            AnimatedVisibility(
                visible = showSearchBar,
                enter = expandVertically(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(durationMillis = 120)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(durationMillis = 90))
            ) {
                ListsSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = "Search categories...",
                    onClose = {
                        focusManager.clearFocus()
                        showSearchBar = false
                        searchQuery = ""
                    },
                    autoFocus = true,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onPullRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (visibleCategories.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No categories match \"$searchQuery\"" else "No lists yet\nTap New List below to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (isGridView) {
                    // Mobile 2-column or Tablet Adaptive Grid with matching card grey color & compact spacing
                    LazyVerticalGrid(
                        columns = if (!isWideLayout) GridCells.Fixed(2) else GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visibleCategories, key = { it.id }) { category ->
                            val count = categoryCounts[category] ?: 0
                            Surface(
                                onClick = {
                                    triggerStrongTick()
                                    onNavigateToCategory(category)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shadowElevation = 0.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Start
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (count == 1) "1 list" else "$count lists",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Start
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // List View
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visibleCategories, key = { it.id }) { category ->
                            val count = categoryCounts[category] ?: 0
                            Surface(
                                onClick = {
                                    triggerStrongTick()
                                    onNavigateToCategory(category)
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shadowElevation = 0.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (count == 1) "1 list" else "$count lists",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Row
        // Mobile: Equal gap on left (squircle archive) & right (New List) matching cards (16dp)
        // Tablet/Desktop: Archive is in sidebar, New List aligned with cards' right margin (24dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (!isWideLayout) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isWideLayout) {
                    // Small Squircle Archive Icon Button on mobile (accent colour)
                    Surface(
                        onClick = {
                            triggerStrongTick()
                            onNavigateToArchive()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 0.dp,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = "Archive",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            if (archiveCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 7.dp, end = 7.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer)
                                )
                            }
                        }
                    }
                }

                // New List Button - simple "+" in squircle box
                Surface(
                    onClick = {
                        triggerStrongTick()
                        showCreateSheet = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New List",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    // New List Modal
    if (showCreateSheet) {
        NewListModal(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, category ->
                triggerStrongTick()
                scope.launch {
                    repository.createList(name = name, category = category.id)
                    showCreateSheet = false
                    onNavigateToCategory(category)
                }
            }
        )
    }
}
