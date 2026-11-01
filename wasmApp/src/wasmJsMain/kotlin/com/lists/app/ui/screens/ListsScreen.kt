package com.lists.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lists.app.data.model.Category
import com.lists.app.data.model.UserList
import com.lists.app.data.store.WebStore
import com.lists.app.ui.components.CategoryIcon
import com.lists.app.ui.icons.ListyIcons
import com.lists.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onOpenList: (UserList) -> Unit,
    onOpenSettings: () -> Unit,
    onSyncClick: () -> Unit
) {
    val lists by WebStore.lists.collectAsState()
    val items by WebStore.items.collectAsState()
    val isSyncing by WebStore.syncing.collectAsState()
    val userEmail by WebStore.userEmail.collectAsState()
    val lastSyncTime by WebStore.lastSyncTime.collectAsState()

    var showCreateSheet by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<UserList?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<UserList?>(null) }

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
                    IconButton(onClick = onSyncClick) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(28.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = TextWhite
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync",
                                    tint = if (userEmail != null) TextWhite else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (userEmail != null && lastSyncTime != null && lastSyncTime!! > 0L) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 1.dp, y = (-1).dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF34C759))
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
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
                onClick = { showCreateSheet = true },
                containerColor = TextWhite,
                contentColor = PureBlack,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 8.dp, end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New List",
                    tint = PureBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val folderCols = when {
                maxWidth >= 1400.dp -> 6
                maxWidth >= 1100.dp -> 5
                maxWidth >= 840.dp -> 4
                maxWidth >= 560.dp -> 3
                else -> 2
            }
            val horizontalPadding = if (maxWidth >= 1200.dp) 24.dp else 16.dp

            if (lists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = ListyIcons.Movie,
                            contentDescription = null,
                            tint = TextGrayDark,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No lists yet",
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap + to create a list",
                            color = TextGrayMedium,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(folderCols),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 8.dp,
                        bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(lists, key = { it.uuid }) { userList ->
                        val count = items.count { it.listId == userList.id || it.listUuid == userList.uuid }
                        ListyGridTile(
                            userList = userList,
                            itemCount = count,
                            onClick = { onOpenList(userList) },
                            onEdit = { listToEdit = userList },
                            onDelete = { showDeleteConfirm = userList }
                        )
                    }
                }
            }
        }
    }

    // Exact ListyGridTile
    if (showCreateSheet) {
        CompactCreateOrEditListDialog(
            list = null,
            onDismiss = { showCreateSheet = false },
            onSave = { name, category ->
                WebStore.addList(name, category)
                showCreateSheet = false
            }
        )
    }

    listToEdit?.let { list ->
        CompactCreateOrEditListDialog(
            list = list,
            onDismiss = { listToEdit = null },
            onSave = { name, category ->
                WebStore.updateList(list.copy(name = name, category = category.id))
                listToEdit = null
            }
        )
    }

    showDeleteConfirm?.let { list ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete List?", color = TextWhite) },
            text = { Text("Are you sure you want to delete \"${list.name}\" and all items in it?", color = TextGrayLight) },
            confirmButton = {
                Button(
                    onClick = {
                        WebStore.deleteList(list)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A), contentColor = TextWhite)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel", color = TextGrayMedium)
                }
            },
            containerColor = DarkCardBg
        )
    }
}

@Composable
fun ListyGridTile(
    userList: UserList,
    itemCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = Category.fromId(userList.category)
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCardBg)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // Top Row: Category Icon & Item Count / 3-dots Menu
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
                    text = "$itemCount",
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
                            text = { Text("Delete", color = Color(0xFFFF453A)) },
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
            text = userList.name,
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

@Composable
fun CompactCreateOrEditListDialog(
    list: UserList?,
    onDismiss: () -> Unit,
    onSave: (String, Category) -> Unit
) {
    var name by remember { mutableStateOf(list?.name ?: "") }
    var selectedCategory by remember {
        mutableStateOf(list?.let { Category.fromId(it.category) } ?: Category.MOVIE)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (list == null) "New List" else "Edit List",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                                CategoryIcon(
                                    category = cat,
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
            }
        },
        confirmButton = {
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
        },
        containerColor = DarkCardBg
    )
}
