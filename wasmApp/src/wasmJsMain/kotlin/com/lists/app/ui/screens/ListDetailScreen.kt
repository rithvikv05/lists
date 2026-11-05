package com.lists.app.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.lists.app.statusBarsPadding
import com.lists.app.triggerAsmrTick
import com.lists.app.triggerStrongTick
import com.lists.app.data.db.ListItem
import com.lists.app.data.model.Category
import com.lists.app.data.model.RatingScaleOption
import com.lists.app.data.repository.AppRepository
import com.lists.app.ui.components.*
import com.lists.app.ui.theme.*

enum class ViewDisplayMode {
    GRID,
    LIST
}

enum class ItemStatusFilter(val displayName: String) {
    ALL("All"),
    REMAINING("In Progress"),
    DONE("Done")
}

enum class ItemSortOption(val label: String) {
    DATE_ADDED_DESC("Date Added (Newest)"),
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

    LaunchedEffect(searchTrigger) {
        if (searchTrigger > 0) {
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
    var itemForOptions by remember { mutableStateOf<ListItem?>(null) }
    var itemToEdit by remember { mutableStateOf<ListItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ListItem?>(null) }

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

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWideLayout = screenWidthDp >= 600
    val gridColumns = if (isWideLayout) 5 else 3

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val density = LocalDensity.current
    val statusBarTop = 0.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Official Material 3 Segmented Button Row (All | Done | Remaining)
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.height(34.dp)
                    ) {
                        ItemStatusFilter.entries.forEachIndexed { index, filter ->
                            val isSelected = statusFilter == filter
                            SegmentedButton(
                                selected = isSelected,
                                onClick = {
                                    statusFilter = filter
                                    triggerAsmrTick(context)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ItemStatusFilter.entries.size
                                ),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.primary,
                                    inactiveContainerColor = Color.Transparent,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    activeBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                icon = {},
                                label = {
                                    Text(
                                        text = filter.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }

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
                        if (sortedPinned.isNotEmpty()) {
                            items(sortedPinned, key = { it.id }) { item ->
                                GridMediaCard(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    onClick = { onNavigateToItemDetail(item.id) },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        itemForOptions = item
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

                        if (sortedPinned.isNotEmpty() && sortedRemaining.isNotEmpty()) {
                            item(key = "divider_pinned", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                SectionAccentDivider()
                            }
                        }

                        if (sortedRemaining.isNotEmpty()) {
                            items(sortedRemaining, key = { it.id }) { item ->
                                GridMediaCard(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    onClick = { onNavigateToItemDetail(item.id) },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        itemForOptions = item
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

                        if (sortedCompleted.isNotEmpty() && (sortedPinned.isNotEmpty() || sortedRemaining.isNotEmpty())) {
                            item(key = "divider_completed", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                SectionAccentDivider(label = completedLabel)
                            }
                        }

                        if (sortedCompleted.isNotEmpty()) {
                            items(sortedCompleted, key = { it.id }) { item ->
                                GridMediaCard(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    onClick = { onNavigateToItemDetail(item.id) },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        itemForOptions = item
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
                        if (sortedPinned.isNotEmpty()) {
                            items(sortedPinned, key = { it.id }) { item ->
                                ListMediaRow(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    onClick = { onNavigateToItemDetail(item.id) },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        itemForOptions = item
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

                        if (sortedPinned.isNotEmpty() && sortedRemaining.isNotEmpty()) {
                            item(key = "divider_pinned") {
                                SectionAccentDivider()
                            }
                        }

                        if (sortedRemaining.isNotEmpty()) {
                            items(sortedRemaining, key = { it.id }) { item ->
                                ListMediaRow(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    onClick = { onNavigateToItemDetail(item.id) },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        itemForOptions = item
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

                        if (sortedCompleted.isNotEmpty() && (sortedPinned.isNotEmpty() || sortedRemaining.isNotEmpty())) {
                            item(key = "divider_completed") {
                                SectionAccentDivider(label = completedLabel)
                            }
                        }

                        if (sortedCompleted.isNotEmpty()) {
                            items(sortedCompleted, key = { it.id }) { item ->
                                ListMediaRow(
                                    item = item,
                                    category = category,
                                    scale = activeScale,
                                    onClick = { onNavigateToItemDetail(item.id) },
                                    onLongClick = {
                                        triggerStrongTick(context)
                                        itemForOptions = item
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
        }
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
                            style = MaterialTheme.typography.labelLarge,
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

    itemForOptions?.let { selectedItem ->
        val itemCat = Category.fromId(selectedItem.category)
        val markLabel = when (itemCat) {
            Category.MOVIE, Category.TV_SHOW, Category.ANIME -> if (selectedItem.marked) "Mark as Unwatched" else "Mark as Watched"
            Category.BOOK, Category.COMIC, Category.MANGA -> if (selectedItem.marked) "Mark as Unread" else "Mark as Read"
            Category.GAME, Category.BOARD_GAME -> if (selectedItem.marked) "Mark as Unplayed" else "Mark as Played"
            else -> if (selectedItem.marked) "Mark as Incomplete" else "Mark as Completed"
        }

        ModalBottomSheet(
            onDismissRequest = { itemForOptions = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.surfaceContainerHighest) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = selectedItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Surface(
                    onClick = {
                        val current = selectedItem
                        itemForOptions = null
                        triggerStrongTick(context)
                        scope.launch {
                            repository.toggleItemMarked(current)
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
                            imageVector = if (selectedItem.marked) Icons.Default.CheckCircle else Icons.Default.Check,
                            contentDescription = null,
                            tint = if (selectedItem.marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(markLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                Surface(
                    onClick = {
                        val current = selectedItem
                        itemForOptions = null
                        triggerAsmrTick(context)
                        scope.launch {
                            repository.toggleItemPinned(current)
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
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = if (selectedItem.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (selectedItem.pinned) "Unpin from Top" else "Pin to Top",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    onClick = {
                        val current = selectedItem
                        itemForOptions = null
                        itemToEdit = current
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
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Edit Item", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                Surface(
                    onClick = {
                        val current = selectedItem
                        itemForOptions = null
                        itemToDelete = current
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
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("Delete Item", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

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
