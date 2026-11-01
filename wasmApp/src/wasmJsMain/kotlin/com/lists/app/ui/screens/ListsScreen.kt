package com.lists.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lists.app.data.model.Category
import com.lists.app.data.model.UserList
import com.lists.app.data.store.WebStore
import com.lists.app.ui.components.CategoryIcon
import com.lists.app.ui.components.ListyGridTile
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

    var showCreateDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<UserList?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<UserList?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lists",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                },
                actions = {
                    IconButton(onClick = onSyncClick) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = if (isSyncing) AccentWhite else TextGrayMedium
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureBlack
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = TextWhite,
                contentColor = PureBlack,
                shape = CircleShape,
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create List",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = PureBlack
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columns = when {
                maxWidth >= 1200.dp -> 4
                maxWidth >= 800.dp -> 3
                else -> 2
            }

            if (lists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No lists yet. Tap + to create your first list.",
                        color = TextGrayMedium,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(16.dp),
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
                            onMoreOptionsClick = { listToEdit = userList }
                        )
                    }
                }
            }
        }
    }

    // Create List Dialog
    if (showCreateDialog) {
        var newListName by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf(Category.MOVIE) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "New List",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        label = { Text("List Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder,
                            cursorColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Category",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextGrayMedium
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Category.entries.forEach { cat ->
                            val isSelected = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) TextWhite else DarkCardElevated)
                                    .clickable { selectedCategory = cat }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CategoryIcon(
                                        category = cat,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) PureBlack else TextWhite
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = cat.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) PureBlack else TextGrayLight
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
                        if (newListName.isNotBlank()) {
                            WebStore.addList(newListName, selectedCategory)
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PureBlack)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextGrayMedium)
                }
            },
            containerColor = DarkCardBg
        )
    }

    // Edit / Delete List Options
    listToEdit?.let { list ->
        var editedName by remember { mutableStateOf(list.name) }
        var isEditing by remember { mutableStateOf(false) }

        if (!isEditing) {
            AlertDialog(
                onDismissRequest = { listToEdit = null },
                title = { Text(list.name, color = TextWhite) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rename List", color = TextWhite, modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(
                            onClick = {
                                val target = list
                                listToEdit = null
                                showDeleteConfirm = target
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete List", color = Color(0xFFFF453A), modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { listToEdit = null }) {
                        Text("Close", color = TextGrayMedium)
                    }
                },
                containerColor = DarkCardBg
            )
        } else {
            AlertDialog(
                onDismissRequest = { listToEdit = null },
                title = { Text("Rename List", color = TextWhite) },
                text = {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editedName.isNotBlank()) {
                                WebStore.updateList(list.copy(name = editedName.trim()))
                                listToEdit = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PureBlack)
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { listToEdit = null }) {
                        Text("Cancel", color = TextGrayMedium)
                    }
                },
                containerColor = DarkCardBg
            )
        }
    }

    // Delete Confirmation
    showDeleteConfirm?.let { list ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete List?", color = TextWhite) },
            text = {
                Text(
                    "Are you sure you want to delete \"${list.name}\" and all items in it?",
                    color = TextGrayLight
                )
            },
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
