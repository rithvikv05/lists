package com.lists.app.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.lists.app.PullToRefreshBox
import com.lists.app.data.db.ListWithStats
import com.lists.app.data.db.UserList
import com.lists.app.data.model.Category
import com.lists.app.data.model.HomeLayoutMode
import com.lists.app.data.repository.AppRepository
import com.lists.app.data.sync.DriveSyncManager
import com.lists.app.data.sync.GoogleSignInHelper
import com.lists.app.navigationBarsPadding
import com.lists.app.statusBarsPadding
import com.lists.app.triggerAsmrTick
import com.lists.app.triggerStrongTick
import com.lists.app.ui.components.*
import com.lists.app.ui.theme.*

enum class ListsSortOption(val displayName: String) {
    RECENT("Recent"),
    ALPHABETICAL("A – Z"),
    ITEM_COUNT("Item Count")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    repository: AppRepository,
    onNavigateToListDetail: (Long) -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    searchTrigger: Int = 0,
    searchRequested: Boolean = false,
    onSearchHandled: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val uiPrefs = remember { context.getSharedPreferences("lists_ui_prefs", Context.MODE_PRIVATE) }

    var homeLayoutMode by remember {
        val saved = uiPrefs.getString("home_layout_mode", HomeLayoutMode.DISCRETE.name)
        mutableStateOf(
            try { HomeLayoutMode.valueOf(saved ?: HomeLayoutMode.DISCRETE.name) }
            catch (_: Exception) { HomeLayoutMode.DISCRETE }
        )
    }

    val listsWithStats by repository.allListsWithStats.collectAsState(initial = emptyList())
    var selectedCategoryFilter by remember { mutableStateOf<Category?>(null) }
    var isArchiveSelected by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(ListsSortOption.RECENT) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

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

    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            showSearchBar = !showSearchBar
            if (!showSearchBar) {
                focusManager.clearFocus()
                searchQuery = ""
            }
            onSearchHandled?.invoke()
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val syncManager = remember { DriveSyncManager(context) }
    val signInHelper = remember { GoogleSignInHelper(context) }
    val syncStatus by DriveSyncManager.syncStatus.collectAsState()
    var signedInAccount by remember { mutableStateOf(signInHelper.getSignedInAccount()) }

    val onPullRefresh: () -> Unit = {
        isRefreshing = true
        triggerStrongTick(context)
        scope.launch {
            val account = signInHelper.getSignedInAccount()
            if (account != null) {
                syncManager.syncNow(account, repository)
            } else {
                kotlinx.coroutines.delay(500)
            }
            isRefreshing = false
        }
    }

    var showCategoryModal by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<UserList?>(null) }
    var listToDelete by remember { mutableStateOf<UserList?>(null) }
    var selectedListIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val openList: (Long) -> Unit = { listId ->
        if (showSearchBar) {
            focusManager.clearFocus()
            showSearchBar = false
            searchQuery = ""
        }
        onNavigateToListDetail(listId)
    }

    LaunchedEffect(Unit) {
        signedInAccount = signInHelper.getSignedInAccount()
        val saved = uiPrefs.getString("home_layout_mode", HomeLayoutMode.DISCRETE.name)
        homeLayoutMode = try { HomeLayoutMode.valueOf(saved ?: HomeLayoutMode.DISCRETE.name) }
        catch (_: Exception) { HomeLayoutMode.DISCRETE }
    }

    val categoryCounts = remember(listsWithStats) {
        val counts = mutableMapOf<String, Int>()
        listsWithStats.filter { !it.list.isArchived }.forEach {
            val cat = it.list.category.uppercase()
            counts[cat] = (counts[cat] ?: 0) + it.itemCount
        }
        counts
    }

    val archivedCount = remember(listsWithStats) {
        listsWithStats.count { it.list.isArchived }
    }

    val displayedLists = remember(listsWithStats, selectedCategoryFilter, isArchiveSelected, sortOption, searchQuery) {
        var result = if (isArchiveSelected) {
            listsWithStats.filter { it.list.isArchived }
        } else {
            val unarchived = listsWithStats.filter { !it.list.isArchived }
            if (selectedCategoryFilter != null) {
                unarchived.filter { it.list.category.equals(selectedCategoryFilter!!.id, ignoreCase = true) }
            } else {
                unarchived
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter {
                it.list.name.lowercase().contains(q) ||
                it.list.category.lowercase().contains(q)
            }
        }
        val isCompleted = { it: ListWithStats -> it.itemCount > 0 && it.completedCount >= it.itemCount }
        when (sortOption) {
            ListsSortOption.RECENT -> result.sortedWith(
                compareByDescending<ListWithStats> { it.list.pinned }
                    .thenBy { isCompleted(it) }
                    .thenByDescending { it.list.updatedAt }
            )
            ListsSortOption.ALPHABETICAL -> result.sortedWith(
                compareByDescending<ListWithStats> { it.list.pinned }
                    .thenBy { isCompleted(it) }
                    .thenBy { it.list.name.lowercase() }
            )
            ListsSortOption.ITEM_COUNT -> result.sortedWith(
                compareByDescending<ListWithStats> { it.list.pinned }
                    .thenBy { isCompleted(it) }
                    .thenByDescending { it.itemCount }
            )
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWideLayout = screenWidthDp >= 600
    val gridColumns = if (isWideLayout) 4 else 2

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (selectedListIds.isEmpty()) {
                Surface(
                    onClick = {
                        triggerStrongTick(context)
                        showCreateSheet = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .height(40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "New List",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .padding(horizontal = if (isWideLayout) 24.dp else 16.dp)
            ) {
                if (selectedListIds.isNotEmpty()) {
                    val selectedLists = displayedLists.filter { selectedListIds.contains(it.list.id) }.map { it.list }
                    val anyUnpinned = selectedLists.any { !it.pinned }
                    val anyUnarchived = selectedLists.any { !it.isArchived }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 16.dp, bottom = 8.dp)
                            .height(44.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            IconButton(
                                onClick = {
                                    triggerStrongTick(context)
                                    selectedListIds = emptySet()
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${selectedListIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Edit List (visible when exactly 1 list is selected)
                            if (selectedListIds.size == 1) {
                                IconButton(
                                    onClick = {
                                        triggerStrongTick(context)
                                        val list = selectedLists.firstOrNull()
                                        if (list != null) {
                                            listToEdit = list
                                            selectedListIds = emptySet()
                                        }
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit List", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }

                            IconButton(
                                onClick = {
                                    triggerAsmrTick(context)
                                    selectedListIds = if (selectedListIds.size == displayedLists.size) emptySet() else displayedLists.map { it.list.id }.toSet()
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            }

                            IconButton(
                                onClick = {
                                    triggerStrongTick(context)
                                    scope.launch {
                                        repository.setListsPinned(selectedLists, anyUnpinned)
                                        selectedListIds = emptySet()
                                    }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = if (anyUnpinned) "Pin" else "Unpin",
                                    tint = if (anyUnpinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    triggerStrongTick(context)
                                    scope.launch {
                                        repository.setListsArchived(selectedLists, anyUnarchived)
                                        selectedListIds = emptySet()
                                    }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (isArchiveSelected) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = if (isArchiveSelected) "Unarchive" else "Archive",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    triggerStrongTick(context)
                                    showBulkDeleteDialog = true
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                } else {
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
                                letterSpacing = (-0.5).sp
                            )

                            if (!isWideLayout) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val account = signInHelper.getSignedInAccount()
                                            if (account != null) {
                                                scope.launch {
                                                    triggerAsmrTick(context)
                                                    syncManager.syncNow(account, repository)
                                                }
                                            } else {
                                                onNavigateToSettings?.invoke()
                                            }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (syncStatus is DriveSyncManager.SyncStatus.Syncing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (!isSystemInDarkTheme()) Icons.Outlined.Cloud else Icons.Default.Cloud,
                                                    contentDescription = "Sync",
                                                    tint = if (signedInAccount != null) {
                                                        if (!isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                                    } else MaterialTheme.colorScheme.outlineVariant,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                if (signedInAccount != null && syncManager.lastSyncTimestamp > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF00E676))
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            triggerAsmrTick(context)
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
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showSearchBar,
                        enter = expandVertically(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(durationMillis = 120)),
                        exit = shrinkVertically(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(durationMillis = 90))
                    ) {
                        ListsSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholderText = "Search lists...",
                            onClose = {
                                focusManager.clearFocus()
                                showSearchBar = false
                                searchQuery = ""
                            },
                            autoFocus = true,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerAsmrTick(context)
                                    showCategoryModal = true
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isArchiveSelected) "Archived" else selectedCategoryFilter?.displayName ?: "All Categories",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedCategoryFilter != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerAsmrTick(context)
                                    showSortSheet = true
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Filter",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = sortOption.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (displayedLists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching lists found" else "No lists yet. Tap 'New List' to create one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else if (homeLayoutMode == HomeLayoutMode.DISCRETE) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(displayedLists, key = { it.list.id }) { listWithStats ->
                                val cat = Category.fromId(listWithStats.list.category)
                                val isSelected = selectedListIds.contains(listWithStats.list.id)
                                val inSelectionMode = selectedListIds.isNotEmpty()
                                DiscreteListCard(
                                    title = listWithStats.list.name,
                                    category = cat,
                                    itemCount = listWithStats.itemCount,
                                    completedCount = listWithStats.completedCount,
                                    isPinned = listWithStats.list.pinned,
                                    selected = isSelected,
                                    selectionMode = inSelectionMode,
                                    onClick = {
                                        if (inSelectionMode) {
                                            triggerAsmrTick(context)
                                            selectedListIds = if (isSelected) selectedListIds - listWithStats.list.id else selectedListIds + listWithStats.list.id
                                        } else {
                                            openList(listWithStats.list.id)
                                        }
                                    },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        selectedListIds = if (isSelected) selectedListIds - listWithStats.list.id else selectedListIds + listWithStats.list.id
                                    }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(displayedLists, key = { it.list.id }) { listWithStats ->
                                val cat = Category.fromId(listWithStats.list.category)
                                val isSelected = selectedListIds.contains(listWithStats.list.id)
                                val inSelectionMode = selectedListIds.isNotEmpty()
                                BentoCollectionCard(
                                    title = listWithStats.list.name,
                                    category = cat,
                                    itemCount = listWithStats.itemCount,
                                    completedCount = listWithStats.completedCount,
                                    isPinned = listWithStats.list.pinned,
                                    selected = isSelected,
                                    selectionMode = inSelectionMode,
                                    onClick = {
                                        if (inSelectionMode) {
                                            triggerAsmrTick(context)
                                            selectedListIds = if (isSelected) selectedListIds - listWithStats.list.id else selectedListIds + listWithStats.list.id
                                        } else {
                                            openList(listWithStats.list.id)
                                        }
                                    },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        selectedListIds = if (isSelected) selectedListIds - listWithStats.list.id else selectedListIds + listWithStats.list.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

    if (showCategoryModal) {
        ScalableCategoryModal(
            categories = Category.activeCategories,
            selectedCategory = selectedCategoryFilter,
            showAllMedia = true,
            showArchive = true,
            isArchiveSelected = isArchiveSelected,
            categoryCounts = categoryCounts,
            archivedCount = archivedCount,
            onSelectCategory = { cat ->
                isArchiveSelected = false
                selectedCategoryFilter = cat
            },
            onSelectArchive = {
                isArchiveSelected = true
                selectedCategoryFilter = null
            },
            onDismiss = { showCategoryModal = false }
        )
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.surfaceContainerHighest) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Layout View",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isList = homeLayoutMode == HomeLayoutMode.DISCRETE
                            Surface(
                                onClick = {
                                    if (!isList) {
                                        triggerAsmrTick(context)
                                        homeLayoutMode = HomeLayoutMode.DISCRETE
                                        uiPrefs.edit().putString("home_layout_mode", HomeLayoutMode.DISCRETE.name).apply()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isList) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ViewAgenda,
                                        contentDescription = null,
                                        tint = if (isList) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "List",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isList) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isList) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    if (isList) {
                                        triggerAsmrTick(context)
                                        homeLayoutMode = HomeLayoutMode.BENTO
                                        uiPrefs.edit().putString("home_layout_mode", HomeLayoutMode.BENTO.name).apply()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (!isList) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = null,
                                        tint = if (!isList) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Grid",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (!isList) FontWeight.Bold else FontWeight.Medium,
                                        color = if (!isList) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "Sort By",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ListsSortOption.entries.forEach { option ->
                    val isSelected = option == sortOption
                    Surface(
                        onClick = {
                            sortOption = option
                            showSortSheet = false
                            triggerAsmrTick(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        NewListModal(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, category ->
                triggerStrongTick(context)
                scope.launch {
                    repository.createList(name = name, category = category.id)
                    showCreateSheet = false
                }
            }
        )
    }



    listToEdit?.let { list ->
        EditListModal(
            initialName = list.name,
            initialCategory = Category.fromId(list.category),
            onDismiss = { listToEdit = null },
            onSave = { updatedName, updatedCategory ->
                triggerStrongTick(context)
                scope.launch {
                    repository.updateList(list.copy(name = updatedName, category = updatedCategory.id))
                    listToEdit = null
                }
            }
        )
    }

    listToDelete?.let { targetList ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = {
                Text("Delete List?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete “${targetList.name}” and all of its items? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = targetList
                        listToDelete = null
                        triggerStrongTick(context)
                        scope.launch {
                            repository.deleteList(toDelete)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Bulk Delete Lists Confirmation Modal
    if (showBulkDeleteDialog) {
        val count = selectedListIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = {
                Text("Delete $count lists?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete these $count lists and all of their items? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedListIds
                        showBulkDeleteDialog = false
                        selectedListIds = emptySet()
                        triggerStrongTick(context)
                        scope.launch {
                            val targets = displayedLists.filter { idsToDelete.contains(it.list.id) }.map { it.list }
                            repository.deleteLists(targets)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
