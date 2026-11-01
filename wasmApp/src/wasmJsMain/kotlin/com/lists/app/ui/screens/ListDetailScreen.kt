package com.lists.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lists.app.R
import com.lists.app.data.db.ListItem
import com.lists.app.data.model.Category
import com.lists.app.data.model.FilterOption
import com.lists.app.data.model.ItemStatus
import com.lists.app.data.model.RatingConverter
import com.lists.app.data.model.RatingScaleOption
import com.lists.app.data.model.SortOption
import com.lists.app.data.model.ViewMode
import com.lists.app.data.repository.AppRepository
import com.lists.app.ui.components.ItemGridCard
import com.lists.app.ui.components.ItemListRow
import com.lists.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: Long,
    repository: AppRepository,
    onNavigateBack: () -> Unit,
    onNavigateToItemDetail: (Long) -> Unit,
    onNavigateToAddItem: (Long) -> Unit
) {
    val list by repository.getList(listId).collectAsState(initial = null)
    val rawItems by repository.getItems(listId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        val newPrefs = context.getSharedPreferences("lists_view_prefs", android.content.Context.MODE_PRIVATE)
        val oldPrefs = context.getSharedPreferences("listy_view_prefs", android.content.Context.MODE_PRIVATE)
        val legacyGlobal = oldPrefs.getString("global_view_mode", null)
        if (newPrefs.getString("global_view_mode", null) == null && legacyGlobal != null) {
            newPrefs.edit().putString("global_view_mode", legacyGlobal).apply()
        }
        val legacyListMode = oldPrefs.getString("view_mode_$listId", null)
        if (newPrefs.getString("view_mode_$listId", null) == null && legacyListMode != null) {
            newPrefs.edit().putString("view_mode_$listId", legacyListMode).apply()
        }
        newPrefs
    }
    var viewMode by remember {
        val saved = prefs.getString("view_mode_$listId", null) ?: prefs.getString("global_view_mode", ViewMode.GRID_2.name)
        mutableStateOf(try { ViewMode.valueOf(saved!!) } catch (_: Exception) { ViewMode.GRID_2 })
    }
    var selectedFilter by remember { mutableStateOf(FilterOption.ALL) }
    var selectedSort by remember { mutableStateOf(SortOption.DATE_ADDED_DESC) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var showFilterSortSheet by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Automatically place cursor and show keyboard when search is opened
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(120)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val category = list?.let { Category.fromId(it.category) } ?: Category.MOVIE

    val ratingPrefs = remember { context.getSharedPreferences("lists_rating_prefs", android.content.Context.MODE_PRIVATE) }
    var ratingPrefsVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(ratingPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("rating_scale_") == true) {
                ratingPrefsVersion++
            }
        }
        ratingPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            ratingPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val activeScale = remember(category, ratingPrefsVersion) {
        val defaultScale = RatingScaleOption.getDefault(category)
        val saved = ratingPrefs.getString("rating_scale_${category.id}", defaultScale.id)
        RatingScaleOption.fromId(saved, defaultScale)
    }

    val isProgressSupported = category == Category.TV_SHOW ||
            (list?.name?.contains("manga", ignoreCase = true) == true) ||
            (list?.name?.contains("comic", ignoreCase = true) == true)

    val grid2State = rememberLazyGridState()
    val grid3State = rememberLazyGridState()
    val listState = rememberLazyListState()

    // Track scroll offset to physically drive the single title transition
    val scrollOffset by remember(viewMode) {
        derivedStateOf {
            when (viewMode) {
                ViewMode.GRID_2 -> if (grid2State.firstVisibleItemIndex > 0) 500 else grid2State.firstVisibleItemScrollOffset
                ViewMode.GRID_3 -> if (grid3State.firstVisibleItemIndex > 0) 500 else grid3State.firstVisibleItemScrollOffset
                ViewMode.LIST -> if (listState.firstVisibleItemIndex > 0) 500 else listState.firstVisibleItemScrollOffset
            }
        }
    }

    // Single title Apple-style collapsing kinematics
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxScrollPx = with(density) { 68.dp.toPx() }
    val scrollProgress = (scrollOffset.toFloat() / maxScrollPx).coerceIn(0f, 1f)
    val easedProgress = FastOutSlowInEasing.transform(scrollProgress)

    val titleX = androidx.compose.ui.unit.lerp(16.dp, 56.dp, easedProgress)
    val titleY = androidx.compose.ui.unit.lerp(statusBarTop + 56.dp + 4.dp, statusBarTop + 16.dp, easedProgress)
    val titleFontSize = (30f - (30f - 18f) * easedProgress).sp
    val titleLetterSpacing = ((-0.5f) * (1f - easedProgress)).sp
    val titleMaxWidth = androidx.compose.ui.unit.lerp((screenWidthDp - 40).dp, (screenWidthDp - 200).coerceAtMost(400).dp, easedProgress)
    val subtitleAlpha = (1f - scrollProgress * 1.6f).coerceIn(0f, 1f)

    val titleAlpha by animateFloatAsState(
        targetValue = if (isSearchActive) 0f else 1f,
        label = "titleAlpha"
    )

    // Filter & Sort items
    val filteredItems = remember(rawItems, selectedFilter, selectedSort, searchQuery) {
        var items = rawItems

        // Search query filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            items = items.filter {
                it.title.lowercase().contains(q) ||
                        it.creator.lowercase().contains(q) ||
                        it.releaseYear.contains(q)
            }
        }

        // Status Filter
        items = when (selectedFilter) {
            FilterOption.ALL -> items
            FilterOption.IN_PROGRESS -> items.filter { !it.marked && (it.currentEpisode > 0 || it.currentPage > 0 || it.checkedEpisodesJson.isNotBlank()) }
            FilterOption.COMPLETED -> items.filter { it.marked }
            FilterOption.BACKLOG -> items.filter { !it.marked && it.currentEpisode == 0 && it.currentPage == 0 && it.checkedEpisodesJson.isBlank() }
            FilterOption.PINNED -> items.filter { it.pinned }
        }

        // Sorting:
        // By default sort by NOT DONE first, and then DONE.
        // And then the other sorts apply on them.
        // Also in sort by rating use our rating not from the database.
        val comparator = Comparator<ListItem> { a, b ->
            val aDone = a.marked || a.status == ItemStatus.COMPLETED
            val bDone = b.marked || b.status == ItemStatus.COMPLETED
            if (aDone != bDone) {
                if (!aDone && bDone) -1 else 1
            } else if (a.pinned != b.pinned) {
                if (a.pinned && !b.pinned) -1 else 1
            } else {
                when (selectedSort) {
                    SortOption.DATE_ADDED_DESC -> b.id.compareTo(a.id)
                    SortOption.DATE_ADDED_ASC -> a.id.compareTo(b.id)
                    SortOption.TITLE_ASC -> a.title.lowercase().compareTo(b.title.lowercase())
                    SortOption.TITLE_DESC -> b.title.lowercase().compareTo(a.title.lowercase())
                    SortOption.RATING_DESC -> {
                        val rA = RatingConverter.getUserRatingScore(a)
                        val rB = RatingConverter.getUserRatingScore(b)
                        rB.compareTo(rA)
                    }
                    SortOption.RELEASE_YEAR_DESC -> b.releaseYear.compareTo(a.releaseYear)
                }
            }
        }
        items.sortedWith(comparator)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PureBlack,
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search items...", color = TextGrayMedium, fontSize = 14.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = TextWhite),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = TextGrayMedium,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    cursorColor = TextWhite,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                        // Note: When not searching, title slot is deliberately empty so the single animated title seamlessly occupies this position!
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextWhite
                            )
                        }
                    },
                actions = {
                    // 1. Search Icon
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = "Search",
                            tint = if (isSearchActive) TextWhite else TextGrayMedium,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 2. Filter & Sort Combined Icon
                    IconButton(onClick = { showFilterSortSheet = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_filter),
                            contentDescription = "Filter and Sort",
                            tint = if (selectedFilter != FilterOption.ALL || selectedSort != SortOption.DATE_ADDED_DESC) TextWhite else TextGrayMedium,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // 3. View Mode Toggle Icon
                    IconButton(onClick = {
                        val nextMode = when (viewMode) {
                            ViewMode.GRID_2 -> ViewMode.GRID_3
                            ViewMode.GRID_3 -> ViewMode.LIST
                            ViewMode.LIST -> ViewMode.GRID_2
                        }
                        viewMode = nextMode
                        prefs.edit()
                            .putString("view_mode_$listId", nextMode.name)
                            .putString("global_view_mode", nextMode.name)
                            .apply()
                    }) {
                        Icon(
                            imageVector = when (viewMode) {
                                ViewMode.GRID_2 -> Icons.Default.GridView
                                ViewMode.GRID_3 -> Icons.Default.GridOn
                                ViewMode.LIST -> Icons.Default.ViewAgenda
                            },
                            contentDescription = "View Mode",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddItem(listId) },
                containerColor = TextWhite,
                contentColor = PureBlack,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 8.dp, end = 8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = "Add Item",
                    tint = PureBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val completedCount = rawItems.count { it.marked }

            val grid2Cols = when {
                screenWidthDp >= 1400 -> 8
                screenWidthDp >= 1100 -> 6
                screenWidthDp >= 840 -> 5
                screenWidthDp >= 680 -> 4
                screenWidthDp >= 520 -> 3
                else -> 2
            }

            val grid3Cols = when {
                screenWidthDp >= 1400 -> 12
                screenWidthDp >= 1100 -> 9
                screenWidthDp >= 840 -> 7
                screenWidthDp >= 680 -> 5
                screenWidthDp >= 520 -> 4
                else -> 3
            }

            val gridHorizontalPadding = if (screenWidthDp >= 1200) 24.dp else 16.dp

            when (viewMode) {
                ViewMode.GRID_2 -> {
                    LazyVerticalGrid(
                        state = grid2State,
                        columns = GridCells.Fixed(grid2Cols),
                        contentPadding = PaddingValues(start = gridHorizontalPadding, end = gridHorizontalPadding, top = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ListBodyHeader(
                                itemCount = rawItems.size,
                                completedCount = completedCount,
                                subtitleAlpha = subtitleAlpha
                            )
                        }

                        if (filteredItems.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ListEmptyState(searchQuery = searchQuery, category = category)
                            }
                        } else {
                            items(filteredItems, key = { it.id }) { item ->
                                ItemGridCard(
                                    item = item,
                                    category = category,
                                    activeScale = activeScale,
                                    onClick = { onNavigateToItemDetail(item.id) },
                                    onTogglePin = {
                                        scope.launch { repository.toggleItemPinned(item) }
                                    },
                                    onToggleMarked = {
                                        scope.launch { repository.toggleItemMarked(item) }
                                    },
                                    onDelete = {
                                        scope.launch { repository.deleteItem(item) }
                                    }
                                )
                            }
                        }
                    }
                }
                ViewMode.GRID_3 -> {
                    LazyVerticalGrid(
                        state = grid3State,
                        columns = GridCells.Fixed(grid3Cols),
                        contentPadding = PaddingValues(start = gridHorizontalPadding, end = gridHorizontalPadding, top = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ListBodyHeader(
                                itemCount = rawItems.size,
                                completedCount = completedCount,
                                subtitleAlpha = subtitleAlpha
                            )
                        }

                        if (filteredItems.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ListEmptyState(searchQuery = searchQuery, category = category)
                            }
                        } else {
                            items(filteredItems, key = { it.id }) { item ->
                                ItemGridCard(
                                    item = item,
                                    category = category,
                                    activeScale = activeScale,
                                    onClick = { onNavigateToItemDetail(item.id) },
                                    onTogglePin = {
                                        scope.launch { repository.toggleItemPinned(item) }
                                    },
                                    onToggleMarked = {
                                        scope.launch { repository.toggleItemMarked(item) }
                                    },
                                    onDelete = {
                                        scope.launch { repository.deleteItem(item) }
                                    }
                                )
                            }
                        }
                    }
                }
                ViewMode.LIST -> {
                    val listHorizontalPadding = 16.dp


                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = listHorizontalPadding, end = listHorizontalPadding, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            ListBodyHeader(
                                itemCount = rawItems.size,
                                completedCount = completedCount,
                                subtitleAlpha = subtitleAlpha
                            )
                        }

                        if (filteredItems.isEmpty()) {
                            item {
                                ListEmptyState(searchQuery = searchQuery, category = category)
                            }
                        } else {
                            items(filteredItems, key = { it.id }) { item ->
                                ItemListRow(
                                    item = item,
                                    category = category,
                                    activeScale = activeScale,
                                    onClick = { onNavigateToItemDetail(item.id) },
                                    onTogglePin = {
                                        scope.launch { repository.toggleItemPinned(item) }
                                    },
                                    onToggleMarked = {
                                        scope.launch { repository.toggleItemMarked(item) }
                                    },
                                    onDelete = {
                                        scope.launch { repository.deleteItem(item) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ONE SINGLE TITLE THAT PHYSICALLY FLOWS AND SHRINKS/EXPANDS BETWEEN BODY AND HEADER
    if (titleAlpha > 0f) {
        Text(
            text = list?.name ?: "List",
            fontSize = titleFontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = titleLetterSpacing,
            color = TextWhite.copy(alpha = titleAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = titleX, y = titleY)
                .widthIn(max = titleMaxWidth)
        )
    }

    // Perfectly Centered & Balanced Filter & Sort Bottom Sheet
    if (showFilterSortSheet) {
        BalancedFilterSortBottomSheet(
            currentFilter = selectedFilter,
            currentSort = selectedSort,
            isProgressSupported = isProgressSupported,
            hasCompletedItems = rawItems.any { it.marked },
            onFilterSelected = { selectedFilter = it },
            onSortSelected = { selectedSort = it },
            onClearCompleted = {
                scope.launch { repository.clearCompleted(listId) }
            },
            onDismiss = { showFilterSortSheet = false }
        )
    }
}
}

// Large Title reserved space in Body matching Apple iOS Large Title
@Composable
fun ListBodyHeader(
    itemCount: Int,
    completedCount: Int,
    subtitleAlpha: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 14.dp)
    ) {
        // Reserved space where the single large title rests at scrollOffset == 0
        Spacer(modifier = Modifier.height(38.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$itemCount items" + if (completedCount > 0) " • $completedCount done" else "",
            fontSize = 13.sp,
            color = TextGrayMedium.copy(alpha = subtitleAlpha),
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun ListEmptyState(
    searchQuery: String,
    category: Category
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = category.iconRes),
                contentDescription = null,
                tint = TextGrayDark,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (searchQuery.isNotEmpty()) "No items matching \"$searchQuery\"" else "This list is empty",
                color = TextWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap + to add items",
                color = TextGrayMedium,
                fontSize = 13.sp
            )
        }
    }
}

// Perfectly centered, 2x2 symmetrical Filter & Sort Bottom Sheet (Zero text clipping, zero unequal gaps)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalancedFilterSortBottomSheet(
    currentFilter: FilterOption,
    currentSort: SortOption,
    isProgressSupported: Boolean,
    hasCompletedItems: Boolean,
    onFilterSelected: (FilterOption) -> Unit,
    onSortSelected: (SortOption) -> Unit,
    onClearCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val (row1, row2) = remember(isProgressSupported) {
        if (isProgressSupported) {
            listOf(FilterOption.ALL, FilterOption.IN_PROGRESS) to listOf(FilterOption.COMPLETED, FilterOption.PINNED)
        } else {
            listOf(FilterOption.ALL, FilterOption.BACKLOG) to listOf(FilterOption.COMPLETED, FilterOption.PINNED)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextGrayDark) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Filter & Sort",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "FILTER",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextGrayMedium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 2x2 Grid: perfectly centered, equal width, zero text cut off
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row1.forEach { option ->
                        val isSelected = currentFilter == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) TextWhite else DarkCardElevated)
                                .border(0.5.dp, if (isSelected) TextWhite else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    onFilterSelected(option)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.displayName,
                                color = if (isSelected) PureBlack else TextGrayMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row2.forEach { option ->
                        val isSelected = currentFilter == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) TextWhite else DarkCardElevated)
                                .border(0.5.dp, if (isSelected) TextWhite else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    onFilterSelected(option)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.displayName,
                                color = if (isSelected) PureBlack else TextGrayMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SORT BY",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextGrayMedium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Sort options in full width rows with checkmark
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SortOption.DATE_ADDED_DESC to "Date Added",
                    SortOption.TITLE_ASC to "Title (A–Z)",
                    SortOption.RATING_DESC to "Rating (Highest)",
                    SortOption.RELEASE_YEAR_DESC to "Release Year"
                ).forEach { (option, label) ->
                    val isSelected = currentSort == option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) DarkCardElevated else Color.Transparent)
                            .border(0.5.dp, if (isSelected) TextWhite else DarkBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                onSortSelected(option)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) TextWhite else TextGrayMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (hasCompletedItems) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onClearCompleted()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkCardElevated,
                        contentColor = TextWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Clear Completed Items", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
