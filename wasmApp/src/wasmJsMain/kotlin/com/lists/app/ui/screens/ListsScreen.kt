package com.lists.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.lists.app.R
import com.lists.app.data.db.ListWithStats
import com.lists.app.data.db.UserList
import com.lists.app.data.model.Category
import com.lists.app.data.repository.AppRepository
import com.lists.app.ui.components.CategoryIcon
import com.lists.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    repository: AppRepository,
    onNavigateToListDetail: (Long) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val listsWithStats by repository.allListsWithStats.collectAsState(initial = emptyList())
    var localLists by remember { mutableStateOf<List<ListWithStats>>(emptyList()) }

    var draggingListId by remember { mutableStateOf<Long?>(null) }
    var hoverTargetListId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var fingerRootPos by remember { mutableStateOf(Offset.Zero) }

    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<UserList?>(null) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val syncManager = remember { com.lists.app.data.sync.DriveSyncManager(context) }
    val signInHelper = remember { com.lists.app.data.sync.GoogleSignInHelper(context) }
    val syncStatus by com.lists.app.data.sync.DriveSyncManager.syncStatus.collectAsState()
    val userEmail by com.lists.app.data.store.WebStore.userEmail.collectAsState()
    val signedInAccount = userEmail?.let { com.lists.app.data.sync.GoogleSignInAccount(it, it) }

    // Auto-sync on screen load or whenever user account becomes available
    LaunchedEffect(signedInAccount) {
        val account = signedInAccount ?: return@LaunchedEffect
        println("Auto-sync: Account connected, triggering sync...")
        syncManager.syncNow(account, repository)
    }

    // Auto-sync when window gains focus (user switches back to tab)
    LaunchedEffect(signedInAccount) {
        val account = signedInAccount ?: return@LaunchedEffect
        kotlinx.browser.window.onfocus = {
            if (syncStatus !is com.lists.app.data.sync.DriveSyncManager.SyncStatus.Syncing) {
                scope.launch {
                    println("Auto-sync: Window focused, syncing...")
                    syncManager.syncNow(account, repository)
                }
            }
            Unit
        }
    }

    // Periodic sync every 30 seconds when signed in
    LaunchedEffect(signedInAccount) {
        val account = signedInAccount ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            if (syncStatus !is com.lists.app.data.sync.DriveSyncManager.SyncStatus.Syncing) {
                println("Auto-sync: Periodic sync triggered")
                syncManager.syncNow(account, repository)
            }
        }
    }

    LaunchedEffect(listsWithStats) {
        if (draggingListId == null) {
            localLists = listsWithStats
        }
    }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lists",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                },
                actions = {
                    // Sync icon button beside Settings
                    IconButton(
                        onClick = {
                            val account = signInHelper.getSignedInAccount()
                            if (account != null) {
                                if (syncStatus !is com.lists.app.data.sync.DriveSyncManager.SyncStatus.Syncing) {
                                    scope.launch {
                                        triggerAsmrTick(context)
                                        syncManager.syncNow(account, repository)
                                    }
                                }
                            } else {
                                onNavigateToSettings()
                            }
                        }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(28.dp)
                        ) {
                            if (syncStatus is com.lists.app.data.sync.DriveSyncManager.SyncStatus.Syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = TextWhite
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync",
                                    tint = if (signedInAccount != null) TextWhite else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                // Green dot indicating sync connected & completed
                                if (signedInAccount != null && syncManager.lastSyncTimestamp > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 1.dp, y = (-1).dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(androidx.compose.ui.graphics.Color(0xFF34C759))
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Settings",
                            tint = TextWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = TextWhite,
                contentColor = PureBlack,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 8.dp, end = 8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = "New List",
                    tint = PureBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { padding ->
        if (localLists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_type_movie),
                        contentDescription = null,
                        tint = TextGrayMedium,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No collections yet",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create your first collection to start organizing your movies, shows, books, games, and comics.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 400.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextWhite,
                            contentColor = PureBlack
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Collection",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp

            val folderCols = when {
                screenWidthDp >= 1400 -> 6
                screenWidthDp >= 1100 -> 5
                screenWidthDp >= 840 -> 4
                screenWidthDp >= 560 -> 3
                else -> 2
            }
            val horizontalPadding = if (screenWidthDp >= 1200) 24.dp else 16.dp

            // Adaptive Multi-Column Grid with ID-based, 100% accurate drag-and-drop
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(folderCols),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(localLists, key = { it.list.uuid.ifEmpty { "${it.list.name}_${it.list.id}" } }) { item ->
                    val isDraggingThis = draggingListId == item.list.id
                    val isHoverTarget = hoverTargetListId == item.list.id && draggingListId != item.list.id

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                itemBounds[item.list.id] = coords.boundsInRoot()
                            }
                            .zIndex(if (isDraggingThis) 20f else 1f)
                            .graphicsLayer {
                                if (isDraggingThis) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    shadowElevation = 24f
                                }
                            }
                            .pointerInput(item.list.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        draggingListId = item.list.id
                                        hoverTargetListId = item.list.id
                                        dragOffset = Offset.Zero
                                        val rect = itemBounds[item.list.id]
                                        fingerRootPos = if (rect != null) rect.topLeft + localOffset else Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                        fingerRootPos += dragAmount

                                        // Accurately find the folder directly beneath the finger
                                        val target = localLists.firstOrNull { other ->
                                            itemBounds[other.list.id]?.contains(fingerRootPos) == true
                                        }
                                        if (target != null) {
                                            hoverTargetListId = target.list.id
                                        }
                                    },
                                    onDragEnd = {
                                        val fromId = draggingListId
                                        val toId = hoverTargetListId
                                        if (fromId != null && toId != null && fromId != toId) {
                                            val fromIdx = localLists.indexOfFirst { it.list.id == fromId }
                                            val toIdx = localLists.indexOfFirst { it.list.id == toId }
                                            if (fromIdx != -1 && toIdx != -1) {
                                                val mutable = localLists.toMutableList()
                                                val moved = mutable.removeAt(fromIdx)
                                                mutable.add(toIdx, moved)
                                                localLists = mutable
                                                scope.launch {
                                                    repository.reorderLists(mutable.map { it.list.id })
                                                }
                                            }
                                        }
                                        draggingListId = null
                                        hoverTargetListId = null
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggingListId = null
                                        hoverTargetListId = null
                                        dragOffset = Offset.Zero
                                    }
                                )
                            }
                    ) {
                        ListsGridTile(
                            listWithStats = item,
                            isDragging = isDraggingThis,
                            isHoverTarget = isHoverTarget,
                            onClick = {
                                if (draggingListId == null) {
                                    onNavigateToListDetail(item.list.id)
                                }
                            },
                            onEdit = { listToEdit = item.list },
                            onDelete = {
                                scope.launch { repository.deleteList(item.list) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CompactCreateOrEditListBottomSheet(
            list = null,
            onDismiss = { showCreateDialog = false },
            onSave = { name, category ->
                scope.launch {
                    repository.createList(name, "", category.id)
                    showCreateDialog = false
                    gridState.animateScrollToItem(0)
                }
            }
        )
    }

    listToEdit?.let { list ->
        CompactCreateOrEditListBottomSheet(
            list = list,
            onDismiss = { listToEdit = null },
            onSave = { name, category ->
                scope.launch {
                    repository.updateList(
                        list.copy(
                            name = name,
                            category = category.id
                        )
                    )
                    listToEdit = null
                }
            }
        )
    }
}

// Exactly reproduces item_list.xml with 3-dots menu & clean hover target border
@Composable
fun ListsGridTile(
    listWithStats: ListWithStats,
    isDragging: Boolean = false,
    isHoverTarget: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val list = listWithStats.list
    val category = Category.fromId(list.category)
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDragging) DarkCardElevated else DarkCardBg)
            .border(
                width = if (isDragging || isHoverTarget) 1.5.dp else 0.5.dp,
                color = when {
                    isDragging -> TextWhite
                    isHoverTarget -> TextWhite
                    else -> DarkBorder
                },
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // Top Row: Category Icon (Left) & Item Count / 3-dots Menu (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            CategoryIcon(
                category = category,
                tint = TextWhite,
                modifier = Modifier.size(24.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${listWithStats.itemCount}",
                    color = TextGrayMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(24.dp)
                            .padding(start = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextGrayDark,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(DarkCardElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }

        // Bottom: List Title
        Text(
            text = list.name,
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

// Compact bottom sheet that fits completely in the initial view with ZERO scrolling
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactCreateOrEditListBottomSheet(
    list: UserList?,
    onDismiss: () -> Unit,
    onSave: (String, Category) -> Unit
) {
    var name by remember { mutableStateOf(list?.name ?: "") }
    var selectedCategory by remember {
        mutableStateOf(list?.let { Category.fromId(it.category) } ?: Category.MOVIE)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextGrayDark) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = if (list == null) "New List" else "Edit List",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Custom input field: vertically centered, never cut off
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TextWhite),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCardElevated)
                            .border(0.5.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (name.isEmpty()) {
                            Text(
                                text = "List Name",
                                color = TextGrayDark,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Category pills in a single compact row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(Category.MOVIE, Category.TV_SHOW, Category.BOOK, Category.GAME).forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) TextWhite else DarkCardElevated)
                            .border(0.5.dp, if (isSelected) TextWhite else DarkBorder, RoundedCornerShape(6.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = cat.iconRes),
                                contentDescription = null,
                                tint = if (isSelected) PureBlack else TextWhite,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (cat) {
                                    Category.MOVIE -> "Movie"
                                    Category.TV_SHOW -> "TV"
                                    Category.BOOK -> "Book"
                                    Category.GAME -> "Game"
                                },
                                color = if (isSelected) PureBlack else TextWhite,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clean, compact Create button (Fits without scroll)
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), selectedCategory)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TextWhite,
                    contentColor = PureBlack,
                    disabledContainerColor = DarkCardElevated,
                    disabledContentColor = TextGrayDark
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text(
                    text = if (list == null) "Create List" else "Save",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
