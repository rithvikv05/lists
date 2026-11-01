package com.lists.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lists.app.data.model.*
import com.lists.app.data.store.WebStore
import com.lists.app.ui.components.ItemGridCard
import com.lists.app.ui.components.ItemListRow
import com.lists.app.ui.icons.ListyIcons
import com.lists.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    userList: UserList,
    onBack: () -> Unit,
    onAddItem: () -> Unit,
    onOpenItem: (ListItem) -> Unit
) {
    val allItems by WebStore.items.collectAsState()
    val ratingPrefs by WebStore.ratingPrefs.collectAsState()

    val category = remember(userList.category) { Category.fromId(userList.category) }
    val activeScale = remember(ratingPrefs, category) { WebStore.getRatingScale(category) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    var currentSort by remember {
        mutableStateOf(
            try { SortOption.valueOf(userList.sortingOption) } catch (e: Exception) { SortOption.DATE_ADDED_DESC }
        )
    }
    var currentFilter by remember {
        mutableStateOf(
            try { FilterOption.valueOf(userList.filterOption) } catch (e: Exception) { FilterOption.ALL }
        )
    }
    var currentViewMode by remember {
        mutableStateOf(
            try { ViewMode.valueOf(userList.viewMode) } catch (e: Exception) { ViewMode.GRID_2 }
        )
    }

    var showFilterSortSheet by remember { mutableStateOf(false) }

    // Filter and Sort Items
    val filteredItems = remember(allItems, userList, searchQuery, currentFilter, currentSort) {
        val listItems = allItems.filter { it.listId == userList.id || it.listUuid == userList.uuid }

        val searchFiltered = if (searchQuery.isBlank()) {
            listItems
        } else {
            val q = searchQuery.trim().lowercase()
            listItems.filter { it.title.lowercase().contains(q) || it.creator.lowercase().contains(q) }
        }

        val statusFiltered = when (currentFilter) {
            FilterOption.ALL -> searchFiltered
            FilterOption.IN_PROGRESS -> searchFiltered.filter { it.status == ItemStatus.IN_PROGRESS }
            FilterOption.COMPLETED -> searchFiltered.filter { it.marked || it.status == ItemStatus.COMPLETED }
            FilterOption.BACKLOG -> searchFiltered.filter { !it.marked && it.status == ItemStatus.BACKLOG }
            FilterOption.PINNED -> searchFiltered.filter { it.pinned }
        }

        statusFiltered.sortedWith { a, b ->
            if (a.pinned != b.pinned) {
                b.pinned.compareTo(a.pinned)
            } else {
                when (currentSort) {
                    SortOption.DATE_ADDED_DESC -> b.createdAt.compareTo(a.createdAt)
                    SortOption.DATE_ADDED_ASC -> a.createdAt.compareTo(b.createdAt)
                    SortOption.TITLE_ASC -> a.title.compareTo(b.title, ignoreCase = true)
                    SortOption.TITLE_DESC -> b.title.compareTo(a.title, ignoreCase = true)
                    SortOption.RATING_DESC -> {
                        val scoreA = RatingConverter.getUserRatingScore(a)
                        val scoreB = RatingConverter.getUserRatingScore(b)
                        scoreB.compareTo(scoreA)
                    }
                    SortOption.RELEASE_YEAR_DESC -> b.releaseYear.compareTo(a.releaseYear)
                }
            }
        }
    }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter in ${userList.name}...", color = TextGrayMedium) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = userList.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = ""
                        } else {
                            onBack()
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
                    // View Mode Switcher
                    IconButton(onClick = {
                        val nextMode = when (currentViewMode) {
                            ViewMode.GRID_2 -> ViewMode.GRID_3
                            ViewMode.GRID_3 -> ViewMode.LIST
                            ViewMode.LIST -> ViewMode.GRID_2
                        }
                        currentViewMode = nextMode
                        WebStore.updateList(userList.copy(viewMode = nextMode.name))
                    }) {
                        Icon(
                            imageVector = when (currentViewMode) {
                                ViewMode.GRID_2 -> Icons.Default.GridView
                                ViewMode.GRID_3 -> Icons.Default.GridOn
                                ViewMode.LIST -> Icons.Default.ViewAgenda
                            },
                            contentDescription = "View Mode",
                            tint = TextWhite
                        )
                    }

                    // Search button
                    IconButton(onClick = { isSearching = !isSearching }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearching) AccentWhite else TextGrayMedium
                        )
                    }

                    // Filter & Sort button
                    IconButton(onClick = { showFilterSortSheet = true }) {
                        Icon(
                            imageVector = ListyIcons.Filter,
                            contentDescription = "Filter & Sort",
                            tint = if (currentFilter != FilterOption.ALL) AccentWhite else TextGrayMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddItem,
                containerColor = TextWhite,
                contentColor = PureBlack,
                shape = CircleShape,
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Item",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isTablet = maxWidth >= 800.dp
            val isDesktop = maxWidth >= 1200.dp

            val columns = when (currentViewMode) {
                ViewMode.GRID_2 -> when {
                    isDesktop -> 5
                    isTablet -> 4
                    else -> 2
                }
                ViewMode.GRID_3 -> when {
                    isDesktop -> 7
                    isTablet -> 5
                    else -> 3
                }
                ViewMode.LIST -> 1
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No items match \"$searchQuery\"" else "No items yet. Tap + to add items.",
                        color = TextGrayMedium,
                        fontSize = 15.sp
                    )
                }
            } else if (currentViewMode == ViewMode.LIST) {
                val horizontalPadding = if (maxWidth >= 760.dp) {
                    ((maxWidth - 720.dp) / 2).coerceAtLeast(16.dp)
                } else {
                    16.dp
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.uuid }) { item ->
                        ItemListRow(
                            item = item,
                            category = category,
                            activeScale = activeScale,
                            onClick = { onOpenItem(item) },
                            onToggleMarked = {
                                val newMarked = !item.marked
                                val newStatus = if (newMarked) ItemStatus.COMPLETED else ItemStatus.BACKLOG
                                WebStore.updateItem(
                                    item.copy(
                                        marked = newMarked,
                                        markedAt = if (newMarked) currentTimeMillis() else null,
                                        status = newStatus
                                    )
                                )
                            }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.uuid }) { item ->
                        ItemGridCard(
                            item = item,
                            category = category,
                            activeScale = activeScale,
                            onClick = { onOpenItem(item) },
                            onToggleMarked = {
                                val newMarked = !item.marked
                                val newStatus = if (newMarked) ItemStatus.COMPLETED else ItemStatus.BACKLOG
                                WebStore.updateItem(
                                    item.copy(
                                        marked = newMarked,
                                        markedAt = if (newMarked) currentTimeMillis() else null,
                                        status = newStatus
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Filter & Sort Bottom Sheet / Modal matching com.lists.app
    if (showFilterSortSheet) {
        AlertDialog(
            onDismissRequest = { showFilterSortSheet = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter & Sort", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    TextButton(onClick = {
                        WebStore.clearCompletedItems(userList.id, userList.uuid)
                        showFilterSortSheet = false
                    }) {
                        Text("Clear Completed", color = Color(0xFFFF453A), fontSize = 13.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Filter Status", color = TextGrayMedium, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    // 2x2 symmetrical filter pills
                    val filters = listOf(
                        FilterOption.ALL to "All",
                        FilterOption.IN_PROGRESS to "In Progress",
                        FilterOption.COMPLETED to "Done",
                        FilterOption.BACKLOG to "Backlog"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            filters.take(2).forEach { (opt, label) ->
                                val isSelected = currentFilter == opt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) TextWhite else DarkCardElevated)
                                        .clickable {
                                            currentFilter = opt
                                            WebStore.updateList(userList.copy(filterOption = opt.name))
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) PureBlack else TextWhite
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            filters.drop(2).forEach { (opt, label) ->
                                val isSelected = currentFilter == opt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) TextWhite else DarkCardElevated)
                                        .clickable {
                                            currentFilter = opt
                                            WebStore.updateList(userList.copy(filterOption = opt.name))
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) PureBlack else TextWhite
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sort By", color = TextGrayMedium, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    val sortOptions = listOf(
                        SortOption.DATE_ADDED_DESC to "Date Added (Newest)",
                        SortOption.DATE_ADDED_ASC to "Date Added (Oldest)",
                        SortOption.TITLE_ASC to "Title (A-Z)",
                        SortOption.TITLE_DESC to "Title (Z-A)",
                        SortOption.RATING_DESC to "Highest Rating",
                        SortOption.RELEASE_YEAR_DESC to "Release Year"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sortOptions.forEach { (opt, label) ->
                            val isSelected = currentSort == opt
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        currentSort = opt
                                        WebStore.updateList(userList.copy(sortingOption = opt.name))
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    color = if (isSelected) TextWhite else TextGrayLight
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFilterSortSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PureBlack)
                ) {
                    Text("Apply")
                }
            },
            containerColor = DarkCardBg
        )
    }
}
