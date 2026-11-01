package com.lists.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import com.lists.app.data.api.SearchApi
import com.lists.app.data.model.*
import com.lists.app.data.store.WebStore
import com.lists.app.ui.components.PosterImage
import com.lists.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    userList: UserList,
    onBack: () -> Unit,
    onItemAdded: () -> Unit
) {
    val initialCategory = remember(userList.category) { Category.fromId(userList.category) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var showCustomItemDialog by remember { mutableStateOf(false) }

    // Live debounced search
    LaunchedEffect(searchQuery, selectedCategory) {
        if (searchQuery.trim().length >= 2) {
            isSearching = true
            delay(400) // 400ms debounce
            val results = SearchApi.search(searchQuery, selectedCategory)
            searchResults = results
            isSearching = false
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add to ${userList.name}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showCustomItemDialog = true }) {
                        Text("Custom +", color = AccentWhite, fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        },
        containerColor = PureBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Input Capsule
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(selectedCategory.searchHint, color = TextGrayMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextGrayMedium
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextGrayMedium
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = DarkCardBg,
                    unfocusedContainerColor = DarkCardBg,
                    focusedBorderColor = DarkCardElevated,
                    unfocusedBorderColor = DarkBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            // Category filter pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Category.entries.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) TextWhite else DarkCardBg)
                            .clickable { selectedCategory = cat }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) PureBlack else TextGrayLight
                        )
                    }
                }
            }

            // Results list or loader
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentWhite)
                }
            } else if (searchResults.isEmpty() && searchQuery.trim().length >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No results found. Tap \"Custom +\" to add manually.", color = TextGrayMedium)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchResults) { res ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    WebStore.addItem(
                                        ListItem(
                                            listId = userList.id,
                                            listUuid = userList.uuid,
                                            title = res.title,
                                            overview = res.overview,
                                            creator = res.creator,
                                            coverUrl = res.coverUrl,
                                            releaseYear = res.releaseYear,
                                            releaseDate = res.releaseDate,
                                            category = userList.category,
                                            totalPages = res.totalPages,
                                            totalEpisodes = res.totalEpisodes
                                        )
                                    )
                                    onItemAdded()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PosterImage(
                                    url = res.coverUrl,
                                    category = selectedCategory,
                                    modifier = Modifier
                                        .width(46.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = res.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (res.releaseYear.isNotEmpty() || res.creator.isNotEmpty()) {
                                        Text(
                                            text = listOf(res.releaseYear, res.creator)
                                                .filter { it.isNotEmpty() }
                                                .joinToString(" • "),
                                            fontSize = 12.sp,
                                            color = TextGrayMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        WebStore.addItem(
                                            ListItem(
                                                listId = userList.id,
                                                listUuid = userList.uuid,
                                                title = res.title,
                                                overview = res.overview,
                                                creator = res.creator,
                                                coverUrl = res.coverUrl,
                                                releaseYear = res.releaseYear,
                                                releaseDate = res.releaseDate,
                                                category = userList.category,
                                                totalPages = res.totalPages,
                                                totalEpisodes = res.totalEpisodes
                                            )
                                        )
                                        onItemAdded()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = AccentWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Item Dialog
    if (showCustomItemDialog) {
        var customTitle by remember { mutableStateOf("") }
        var customCreator by remember { mutableStateOf("") }
        var customYear by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCustomItemDialog = false },
            title = { Text("Add Custom Item", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customCreator,
                        onValueChange = { customCreator = it },
                        label = { Text("Creator / Author / Studio") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customYear,
                        onValueChange = { customYear = it },
                        label = { Text("Release Year") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customTitle.isNotBlank()) {
                            WebStore.addItem(
                                ListItem(
                                    listId = userList.id,
                                    listUuid = userList.uuid,
                                    title = customTitle.trim(),
                                    creator = customCreator.trim(),
                                    releaseYear = customYear.trim(),
                                    category = userList.category
                                )
                            )
                            showCustomItemDialog = false
                            onItemAdded()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PureBlack)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomItemDialog = false }) {
                    Text("Cancel", color = TextGrayMedium)
                }
            },
            containerColor = DarkCardBg
        )
    }
}
