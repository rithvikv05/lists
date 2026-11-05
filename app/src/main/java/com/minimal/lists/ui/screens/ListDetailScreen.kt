package com.minimal.lists.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.minimal.lists.data.db.ListItem
import com.minimal.lists.data.model.Category
import com.minimal.lists.data.model.RatingScaleOption
import com.minimal.lists.data.repository.AppRepository
import com.minimal.lists.ui.components.*
import com.minimal.lists.ui.theme.*

enum class ViewDisplayMode {
    GRID,
    LIST
}

enum class ItemStatusFilter(val displayName: String) {
    ALL("All"),
    DONE("Done"),
    REMAINING("Remaining")
}

enum class ItemSortOption(val label: String) {
    DATE_ADDED_DESC("Recently Added"),
    TITLE_ASC("Title (A – Z)"),
    RATING_DESC("Highest Rated"),
    YEAR_DESC("Release Year")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: Long,
    repository: AppRepository,
    onNavigateBack: () -> Unit,
    onNavigateToItemDetail: (Long) -> Unit,
    onNavigateToAddItem: (Long, String?) -> Unit,
    searchTrigger: Int = 0,
    searchRequested: Boolean = false,
    onSearchHandled: (() -> Unit)? = null
) {
    val list by repository.getList(listId).collectAsState(initial = null)
    val rawItems by repository.getItems(listId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val prefs = remember { context.getSharedPreferences("lists_detail_prefs", Context.MODE_PRIVATE) }
    var displayMode by remember {
        val saved = prefs.getString("view_mode_$listId", ViewDisplayMode.GRID.name)
        mutableStateOf(try { ViewDisplayMode.valueOf(saved!!) } catch (_: Exception) { ViewDisplayMode.GRID })
    }
    var statusFilter by remember { mutableStateOf(ItemStatusFilter.ALL) }
    var sortOption by remember { mutableStateOf(ItemSortOption.DATE_ADDED_DESC) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var lastTrigger by remember { mutableStateOf(searchTrigger) }
    LaunchedEffect(searchTrigger) {
        if (searchTrigger != lastTrigger) {
            lastTrigger = searchTrigger
            isSearchActive = !isSearchActive
            if (!isSearchActive) {
                focusManager.clearFocus()
                searchQuery = ""
            }
        }
    }

    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            isSearchActive = !isSearchActive
            if (!isSearchActive) {
                focusManager.clearFocus()
                searchQuery = ""
            }
            onSearchHandled?.invoke()
        }
    }
    var showSortSheet by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showEditListModal by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ListItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedItemIds.isNotEmpty()) {
        selectedItemIds = emptySet()
    }

    val category = list?.let { Category.fromId(it.category) } ?: Category.MOVIE

    val completedLabel = remember(category) {
        when (category) {
            Category.MOVIE, Category.TV_SHOW, Category.ANIME -> "Watched"
            Category.BOOK, Category.COMIC, Category.MANGA -> "Read"
            Category.GAME, Category.BOARD_GAME -> "Played"
            else -> "Completed"
        }
    }

    val ratingPrefs = remember { context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE) }
    val activeScale = remember(category) {
        val defaultScale = RatingScaleOption.getDefault(category)
        val saved = ratingPrefs.getString("rating_scale_${category.id}", defaultScale.id)
        RatingScaleOption.fromId(saved, defaultScale)
    }

    val filteredItems = remember(rawItems, statusFilter, searchQuery) {
        var items = rawItems
        if (searchQuery.isNotBlank()) {
            items = items.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.creator.contains(searchQuery, ignoreCase = true)
            }
        }
        when (statusFilter) {
            ItemStatusFilter.ALL -> items
            ItemStatusFilter.DONE -> items.filter { it.marked }
            ItemStatusFilter.REMAINING -> items.filter { !it.marked }
        }
    }

    val (sortedPinned, sortedRemaining, sortedCompleted) = remember(filteredItems, sortOption) {
        fun sortGroup(group: List<ListItem>): List<ListItem> {
            return when (sortOption) {
                ItemSortOption.DATE_ADDED_DESC -> group.sortedByDescending { it.createdAt }
                ItemSortOption.TITLE_ASC -> group.sortedBy { it.title.lowercase() }
                ItemSortOption.RATING_DESC -> group.sortedByDescending { it.rating }
                ItemSortOption.YEAR_DESC -> group.sortedByDescending { it.releaseYear }
            }
        }
        val pinned = sortGroup(filteredItems.filter { it.pinned })
        val remaining = sortGroup(filteredItems.filter { !it.pinned && !it.marked })
        val completed = sortGroup(filteredItems.filter { !it.pinned && it.marked })
        Triple(pinned, remaining, completed)
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isWideLayout = isTablet
    val screenWidthDp = configuration.screenWidthDp
    val gridColumns = if (isWideLayout) 5 else if (screenWidthDp >= 600) 4 else 3

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val scrollOffset by remember(displayMode) {
        derivedStateOf {
            when (displayMode) {
                ViewDisplayMode.GRID -> if (gridState.firstVisibleItemIndex > 0) 500 else gridState.firstVisibleItemScrollOffset
                ViewDisplayMode.LIST -> if (listState.firstVisibleItemIndex > 0) 500 else listState.firstVisibleItemScrollOffset
            }
        }
    }

    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxScrollPx = with(density) { 56.dp.toPx() }
    val scrollProgress = (scrollOffset.toFloat() / maxScrollPx).coerceIn(0f, 1f)
    val easedProgress = FastOutSlowInEasing.transform(scrollProgress)

    val searchProgress by animateFloatAsState(
        targetValue = if (isSearchActive) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "searchProgress"
    )
    val effectiveProgress = maxOf(searchProgress, easedProgress)
    val titleX = if (isWideLayout) 20.dp else androidx.compose.ui.unit.lerp(16.dp, 56.dp, effectiveProgress)
    val titleY = androidx.compose.ui.unit.lerp(statusBarTop + 68.dp, statusBarTop + 26.dp, effectiveProgress)
    val titleFontSize = (28f - (28f - 18f) * effectiveProgress).sp
    val titleLetterSpacing = ((-0.5f) * (1f - effectiveProgress)).sp
    val titleMaxWidth = androidx.compose.ui.unit.lerp((screenWidthDp - 32).dp, (screenWidthDp - (if (isWideLayout) 120 else 170)).coerceAtLeast(140).dp, effectiveProgress)
    val subtitleAlpha = (1f - effectiveProgress * 1.8f).coerceIn(0f, 1f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (selectedItemIds.isEmpty()) {
                // 2D Minimal, Flush "Add Item" Box (No shadows, No + plus icon, centered text, matching New List and Create List)
                Surface(
                    onClick = {
                        triggerStrongTick(context)
                        onNavigateToAddItem(listId, null)
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
                        text = "Add Item",
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isSearchActive) {
                        focusManager.clearFocus()
                        isSearchActive = false
                        searchQuery = ""
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .padding(horizontal = if (isWideLayout) 24.dp else 16.dp)
            ) {
                // Top Header Row matching Stitch Screen #17 (Zero extra black gap)
                if (selectedItemIds.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 16.dp, bottom = 8.dp)
                            .height(44.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                triggerStrongTick(context)
                                selectedItemIds = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${selectedItemIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val selectedItems = filteredItems.filter { selectedItemIds.contains(it.id) }
                            val anyUnmarked = selectedItems.any { !it.marked }
                            val anyUnpinned = selectedItems.any { !it.pinned }

                            // Edit Item (visible when exactly 1 item is selected)
                            if (selectedItemIds.size == 1) {
                                IconButton(onClick = {
                                    triggerStrongTick(context)
                                    val item = selectedItems.firstOrNull()
                                    if (item != null) {
                                        itemToEdit = item
                                        selectedItemIds = emptySet()
                                    }
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Item", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // Select All
                            IconButton(onClick = {
                                triggerAsmrTick(context)
                                selectedItemIds = if (selectedItemIds.size == filteredItems.size) emptySet() else filteredItems.map { it.id }.toSet()
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = MaterialTheme.colorScheme.onSurface)
                            }

                            // Bulk Mark / Unmark
                            IconButton(onClick = {
                                triggerStrongTick(context)
                                scope.launch {
                                    repository.setItemsMarked(selectedItems, anyUnmarked)
                                    selectedItemIds = emptySet()
                                }
                            }) {
                                Icon(
                                    imageVector = if (anyUnmarked) Icons.Default.CheckCircle else Icons.Default.Check,
                                    contentDescription = if (anyUnmarked) "Mark $completedLabel" else "Unmark $completedLabel",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Bulk Pin / Unpin
                            IconButton(onClick = {
                                triggerStrongTick(context)
                                scope.launch {
                                    repository.setItemsPinned(selectedItems, anyUnpinned)
                                    selectedItemIds = emptySet()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = if (anyUnpinned) "Pin" else "Unpin",
                                    tint = if (anyUnpinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }

                            // Bulk Delete
                            IconButton(onClick = {
                                triggerStrongTick(context)
                                showBulkDeleteDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
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
                        if (isWideLayout) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = list?.name ?: "List",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .offset(y = 2.dp)
                                ) {
                                    Text(
                                        text = "${rawItems.size} Items • ${rawItems.count { it.marked }} Done",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (!isWideLayout) {
                                IconButton(
                                    onClick = {
                                        isSearchActive = !isSearchActive
                                        if (!isSearchActive) {
                                            focusManager.clearFocus()
                                            searchQuery = ""
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showSortSheet = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showOptionsSheet = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Collapsible Search Bar using unified ListsSearchBar with tap-outside dismissal
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = expandVertically(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(durationMillis = 120)),
                    exit = shrinkVertically(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(durationMillis = 90))
                ) {
                    ListsSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = "Search in this list...",
                        onClose = {
                            focusManager.clearFocus()
                            isSearchActive = false
                            searchQuery = ""
                        },
                        autoFocus = true,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (!isWideLayout && effectiveProgress < 0.99f) {
                    val reservedHeight = androidx.compose.ui.unit.lerp(34.dp, 0.dp, effectiveProgress)
                    val subHeight = androidx.compose.ui.unit.lerp(18.dp, 0.dp, effectiveProgress)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 0.dp,
                                bottom = (4 * (1f - effectiveProgress)).dp
                            )
                    ) {
                        Spacer(modifier = Modifier.height(reservedHeight))
                        if (effectiveProgress < 0.95f) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier.height(subHeight),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "${rawItems.size} Items • ${rawItems.count { it.marked }} Done",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = subtitleAlpha)
                                )
                            }
                        }
                    }
                }

                // View Switcher & Quick Filter Row (Left: All, Done, Remaining tags; Right: Grid/List slider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Quick Filter Pills (All | Done | Remaining)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ItemStatusFilter.entries.forEach { filter ->
                            val isSelected = statusFilter == filter
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        statusFilter = filter
                                        triggerAsmrTick(context)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Simple Slider for Grid/List without names (Icons only)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            val isGrid = displayMode == ViewDisplayMode.GRID
                            Surface(
                                onClick = {
                                    displayMode = ViewDisplayMode.GRID
                                    prefs.edit().putString("view_mode_$listId", ViewDisplayMode.GRID.name).apply()
                                    triggerAsmrTick(context)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isGrid) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Grid",
                                        tint = if (isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    displayMode = ViewDisplayMode.LIST
                                    prefs.edit().putString("view_mode_$listId", ViewDisplayMode.LIST.name).apply()
                                    triggerAsmrTick(context)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (!isGrid) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ViewAgenda,
                                        contentDescription = "List",
                                        tint = if (!isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Media Items Display with completion circle toggle & automatic completed-to-bottom sorting
                if (searchQuery.isNotBlank() && filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = "No items found for “$searchQuery”",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    triggerStrongTick(context)
                                    onNavigateToAddItem(listId, searchQuery.trim())
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add “${searchQuery.trim()}”", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (displayMode == ViewDisplayMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. Pinned Items
                        if (sortedPinned.isNotEmpty()) {
                            items(sortedPinned, key = { "pinned_${it.id}" }) { item ->
                                val isSelected = selectedItemIds.contains(item.id)
                                val inSelectionMode = selectedItemIds.isNotEmpty()
                                GridMediaCard(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    selected = isSelected,
                                    selectionMode = inSelectionMode,
                                    onClick = {
                                        if (inSelectionMode) {
                                            triggerAsmrTick(context)
                                            selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                        } else {
                                            onNavigateToItemDetail(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onToggleComplete = {
                                        focusManager.clearFocus()
                                        triggerStrongTick(context)
                                        scope.launch {
                                            repository.updateItem(item.copy(marked = !item.marked))
                                        }
                                    }
                                )
                            }
                        }

                        // Subtle accented divider between Pinned and To-Be-Done
                        if (sortedPinned.isNotEmpty() && sortedRemaining.isNotEmpty()) {
                            item(key = "divider_pinned", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                SectionAccentDivider()
                            }
                        }

                        // 2. To-Be-Done / Remaining Items
                        if (sortedRemaining.isNotEmpty()) {
                            items(sortedRemaining, key = { "remaining_${it.id}" }) { item ->
                                val isSelected = selectedItemIds.contains(item.id)
                                val inSelectionMode = selectedItemIds.isNotEmpty()
                                GridMediaCard(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    selected = isSelected,
                                    selectionMode = inSelectionMode,
                                    onClick = {
                                        if (inSelectionMode) {
                                            triggerAsmrTick(context)
                                            selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                        } else {
                                            onNavigateToItemDetail(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onToggleComplete = {
                                        focusManager.clearFocus()
                                        triggerStrongTick(context)
                                        scope.launch {
                                            repository.updateItem(item.copy(marked = !item.marked))
                                        }
                                    }
                                )
                            }
                        }

                        // Subtle accented divider before Completed Items
                        if (sortedCompleted.isNotEmpty() && (sortedPinned.isNotEmpty() || sortedRemaining.isNotEmpty())) {
                            item(key = "divider_completed", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                SectionAccentDivider(label = completedLabel)
                            }
                        }

                        // 3. Completed Items (Default sorted to the bottom)
                        if (sortedCompleted.isNotEmpty()) {
                            items(sortedCompleted, key = { "completed_${it.id}" }) { item ->
                                val isSelected = selectedItemIds.contains(item.id)
                                val inSelectionMode = selectedItemIds.isNotEmpty()
                                GridMediaCard(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    selected = isSelected,
                                    selectionMode = inSelectionMode,
                                    onClick = {
                                        if (inSelectionMode) {
                                            triggerAsmrTick(context)
                                            selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                        } else {
                                            onNavigateToItemDetail(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onToggleComplete = {
                                        focusManager.clearFocus()
                                        triggerStrongTick(context)
                                        scope.launch {
                                            repository.updateItem(item.copy(marked = !item.marked))
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. Pinned Items
                        if (sortedPinned.isNotEmpty()) {
                            items(sortedPinned, key = { "pinned_${it.id}" }) { item ->
                                val isSelected = selectedItemIds.contains(item.id)
                                val inSelectionMode = selectedItemIds.isNotEmpty()
                                ListMediaRow(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    selected = isSelected,
                                    selectionMode = inSelectionMode,
                                    onClick = {
                                        if (inSelectionMode) {
                                            triggerAsmrTick(context)
                                            selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                        } else {
                                            onNavigateToItemDetail(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onToggleComplete = {
                                        focusManager.clearFocus()
                                        triggerStrongTick(context)
                                        scope.launch {
                                            repository.updateItem(item.copy(marked = !item.marked))
                                        }
                                    }
                                )
                            }
                        }

                        // Subtle accented divider between Pinned and To-Be-Done
                        if (sortedPinned.isNotEmpty() && sortedRemaining.isNotEmpty()) {
                            item(key = "divider_pinned") {
                                SectionAccentDivider()
                            }
                        }

                        // 2. To-Be-Done / Remaining Items
                        if (sortedRemaining.isNotEmpty()) {
                            items(sortedRemaining, key = { "remaining_${it.id}" }) { item ->
                                val isSelected = selectedItemIds.contains(item.id)
                                val inSelectionMode = selectedItemIds.isNotEmpty()
                                ListMediaRow(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    selected = isSelected,
                                    selectionMode = inSelectionMode,
                                    onClick = {
                                        if (inSelectionMode) {
                                            triggerAsmrTick(context)
                                            selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                        } else {
                                            onNavigateToItemDetail(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onToggleComplete = {
                                        focusManager.clearFocus()
                                        triggerStrongTick(context)
                                        scope.launch {
                                            repository.updateItem(item.copy(marked = !item.marked))
                                        }
                                    }
                                )
                            }
                        }

                        // Subtle accented divider before Completed Items
                        if (sortedCompleted.isNotEmpty() && (sortedPinned.isNotEmpty() || sortedRemaining.isNotEmpty())) {
                            item(key = "divider_completed") {
                                SectionAccentDivider(label = completedLabel)
                            }
                        }

                        // 3. Completed Items (Default sorted to the bottom)
                        if (sortedCompleted.isNotEmpty()) {
                            items(sortedCompleted, key = { "completed_${it.id}" }) { item ->
                                val isSelected = selectedItemIds.contains(item.id)
                                val inSelectionMode = selectedItemIds.isNotEmpty()
                                ListMediaRow(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    selected = isSelected,
                                    selectionMode = inSelectionMode,
                                    onClick = {
                                        if (inSelectionMode) {
                                            triggerAsmrTick(context)
                                            selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                        } else {
                                            onNavigateToItemDetail(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onToggleComplete = {
                                        focusManager.clearFocus()
                                        triggerStrongTick(context)
                                        scope.launch {
                                            repository.updateItem(item.copy(marked = !item.marked))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // SINGLE TITLE THAT PHYSICALLY FLOWS AND SHRINKS/EXPANDS BETWEEN BODY AND HEADER (Phone / Portrait only)
            if (!isWideLayout && selectedItemIds.isEmpty()) {
                Text(
                    text = list?.name ?: "List",
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = titleLetterSpacing,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = titleX, y = titleY)
                        .widthIn(max = titleMaxWidth)
                )
            }
        }
    }

    // Sort Bottom Sheet
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
                Text(
                    text = "Sort Items",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                ItemSortOption.entries.forEach { option ->
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
                                text = option.label,
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

    // Collection Options Bottom Sheet
    if (showOptionsSheet) {
        var selectedScale by remember {
            mutableStateOf(ratingPrefs.getString("list_rating_scale_$listId", "STARS_5") ?: "STARS_5")
        }

        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.surfaceContainerHighest) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = list?.name ?: "List Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    list?.let { l ->
                        val cat = Category.fromId(l.category)
                        Text(
                            text = cat.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }

                // Rating Scale Option (No header, clean equal spacing, 10-scale included)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scales = listOf(
                        "STARS_5" to "5-Star",
                        "STARS_10" to "10-Scale",
                        "TIER_4" to "Skip to Perfection"
                    )
                    scales.forEach { (id, label) ->
                        val isSelected = selectedScale == id
                        Surface(
                            onClick = {
                                triggerAsmrTick(context)
                                selectedScale = id
                                ratingPrefs.edit().putString("list_rating_scale_$listId", id).apply()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Option: Pin / Unpin
                Surface(
                    onClick = {
                        triggerAsmrTick(context)
                        list?.let { l ->
                            scope.launch {
                                repository.updateList(l.copy(pinned = !l.pinned))
                            }
                        }
                        showOptionsSheet = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isPinned = list?.pinned == true
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isPinned) "Unpin List" else "Pin to Top",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Option: Archive / Unarchive
                Surface(
                    onClick = {
                        triggerAsmrTick(context)
                        list?.let { l ->
                            scope.launch {
                                repository.updateList(l.copy(isArchived = !l.isArchived))
                            }
                        }
                        showOptionsSheet = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isArchived = list?.isArchived == true
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isArchived) "Unarchive List" else "Archive List",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Option: Edit List
                Surface(
                    onClick = {
                        triggerAsmrTick(context)
                        showEditListModal = true
                        showOptionsSheet = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Edit List",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Option: Clear Completed Items
                Surface(
                    onClick = {
                        triggerStrongTick(context)
                        scope.launch {
                            repository.clearCompleted(listId)
                            showOptionsSheet = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Clear Completed Items",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Option: Delete List
                Surface(
                    onClick = {
                        triggerStrongTick(context)
                        scope.launch {
                            list?.let { repository.deleteList(it) }
                            showOptionsSheet = false
                            onNavigateBack()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Delete List",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Edit List Modal
    if (showEditListModal) {
        list?.let { currentList ->
            EditListModal(
                initialName = currentList.name,
                initialCategory = Category.fromId(currentList.category),
                onDismiss = { showEditListModal = false },
                onSave = { updatedName, updatedCategory ->
                    triggerStrongTick(context)
                    scope.launch {
                        repository.updateList(currentList.copy(name = updatedName, category = updatedCategory.id))
                        showEditListModal = false
                    }
                }
            )
        }
    }



    // Modal: Edit Item Dialog
    itemToEdit?.let { targetItem ->
        val targetCat = targetItem.category.takeIf { it.isNotBlank() }?.let { Category.fromId(it) } ?: category
        var editTitle by remember { mutableStateOf(targetItem.title) }
        var editCreator by remember { mutableStateOf(targetItem.creator) }
        var editYear by remember { mutableStateOf(targetItem.releaseYear) }
        var editCoverUrl by remember { mutableStateOf(targetItem.coverUrl) }
        var editTotalPages by remember { mutableStateOf(if (targetItem.totalPages > 0) targetItem.totalPages.toString() else "") }
        var editOverview by remember { mutableStateOf(targetItem.overview) }

        AlertDialog(
            onDismissRequest = { itemToEdit = null },
            title = {
                Text("Edit Item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCreator,
                        onValueChange = { editCreator = it },
                        label = { Text(if (targetCat == Category.BOOK || targetCat == Category.COMIC || targetCat == Category.MANGA) "Author / Writer" else "Creator / Studio / Director") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editYear,
                        onValueChange = { editYear = it },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCoverUrl,
                        onValueChange = { editCoverUrl = it },
                        label = { Text("Cover Image URL (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (targetCat == Category.BOOK) {
                        OutlinedTextField(
                            value = editTotalPages,
                            onValueChange = { editTotalPages = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Total Pages") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (targetCat == Category.MANGA || targetCat == Category.COMIC) {
                        OutlinedTextField(
                            value = editTotalPages,
                            onValueChange = { editTotalPages = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Total Chapters") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = editOverview,
                        onValueChange = { editOverview = it },
                        label = { Text(if (targetCat == Category.BOOK || targetCat == Category.GAME) "Synopsis" else "Overview") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = targetItem
                        itemToEdit = null
                        triggerStrongTick(context)
                        scope.launch {
                            val parsedPages = if (targetCat == Category.BOOK || targetCat == Category.MANGA || targetCat == Category.COMIC) {
                                editTotalPages.toIntOrNull() ?: current.totalPages
                            } else {
                                current.totalPages
                            }
                            repository.updateItem(
                                current.copy(
                                    title = editTitle.trim(),
                                    creator = editCreator.trim(),
                                    releaseYear = editYear.trim(),
                                    coverUrl = editCoverUrl.trim(),
                                    totalPages = parsedPages,
                                    overview = editOverview.trim()
                                )
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEdit = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal: Delete Item Confirmation
    itemToDelete?.let { targetItem ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(text = "Delete Item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "Remove “${targetItem.title}” from this list? This action cannot be undone.", style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = targetItem
                        itemToDelete = null
                        triggerStrongTick(context)
                        scope.launch {
                            repository.deleteItem(current)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Bulk Delete Items Confirmation Modal
    if (showBulkDeleteDialog) {
        val count = selectedItemIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = {
                Text("Delete $count items?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete these $count items? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedItemIds
                        showBulkDeleteDialog = false
                        selectedItemIds = emptySet()
                        triggerStrongTick(context)
                        scope.launch {
                            val targets = rawItems.filter { idsToDelete.contains(it.id) }
                            repository.deleteItems(targets)
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

@Composable
fun SectionAccentDivider(label: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            thickness = 1.dp
        )
        if (!label.isNullOrBlank()) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                letterSpacing = 0.5.sp
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                thickness = 1.dp
            )
        }
    }
}
