package com.lists.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lists.app.data.model.*
import com.lists.app.data.store.WebStore
import com.lists.app.ui.components.CategoryIcon
import com.lists.app.ui.icons.ListyIcons
import com.lists.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    item: ListItem,
    onBack: () -> Unit
) {
    var currentItem by remember(item) { mutableStateOf(item) }
    val category = remember(item.category) { Category.fromId(item.category) }
    val ratingPrefs by WebStore.ratingPrefs.collectAsState()
    val activeScale = remember(ratingPrefs, category) { WebStore.getRatingScale(category) }

    var userNotes by remember(item.userNotes) { mutableStateOf(item.userNotes) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
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
                    IconButton(onClick = {
                        val newPinned = !currentItem.pinned
                        val updated = currentItem.copy(
                            pinned = newPinned,
                            pinnedAt = if (newPinned) currentTimeMillis() else null
                        )
                        currentItem = updated
                        WebStore.updateItem(updated)
                    }) {
                        Icon(
                            imageVector = ListyIcons.Pin,
                            contentDescription = "Pin",
                            tint = if (currentItem.pinned) AccentWhite else TextGrayMedium
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF453A)
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkCardElevated)
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentItem.coverUrl.isNotEmpty()) {
                        AsyncImage(
                            model = currentItem.coverUrl,
                            contentDescription = currentItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CategoryIcon(category = category, modifier = Modifier.size(36.dp), tint = TextGrayDark)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentItem.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    if (currentItem.releaseYear.isNotEmpty() || currentItem.creator.isNotEmpty()) {
                        Text(
                            text = listOf(currentItem.releaseYear, currentItem.creator)
                                .filter { it.isNotEmpty() }
                                .joinToString(" • "),
                            fontSize = 13.sp,
                            color = TextGrayMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mark as Done toggle button
                    Button(
                        onClick = {
                            val newMarked = !currentItem.marked
                            val newStatus = if (newMarked) ItemStatus.COMPLETED else ItemStatus.BACKLOG
                            val updated = currentItem.copy(
                                marked = newMarked,
                                markedAt = if (newMarked) (kotlinx.browser.window.performance.now() + 1700000000000.0).toLong() else null,
                                status = newStatus
                            )
                            currentItem = updated
                            WebStore.updateItem(updated)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentItem.marked) AccentWhite else DarkCardElevated,
                            contentColor = if (currentItem.marked) PureBlack else TextWhite
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = ListyIcons.Marked,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (currentItem.marked) "Completed" else "Mark as Done")
                    }
                }
            }

            // Rating Section
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Your Rating",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite
                    )

                    // 4-word rating pills
                    val reviewOptions = listOf(
                        RatingConverter.REVIEW_SKIP to "Skip",
                        RatingConverter.REVIEW_TIMEPASS to "Timepass",
                        RatingConverter.REVIEW_GO_FOR_IT to "Go for It",
                        RatingConverter.REVIEW_PERFECTION to "Perfection"
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        reviewOptions.forEach { (revId, label) ->
                            val isSelected = currentItem.reviewRating == revId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AccentWhite else DarkCardElevated)
                                    .clickable {
                                        val newRev = if (isSelected) RatingConverter.REVIEW_NONE else revId
                                        val newScore = if (newRev != RatingConverter.REVIEW_NONE) RatingConverter.fourWordToFivePoint(newRev) else 0f
                                        val updated = currentItem.copy(reviewRating = newRev, userRating = newScore)
                                        currentItem = updated
                                        WebStore.updateItem(updated)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) PureBlack else TextWhite
                                )
                            }
                        }
                    }
                }
            }

            // Overview Section
            if (currentItem.overview.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Overview",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = currentItem.overview,
                            fontSize = 13.sp,
                            color = TextGrayLight,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Notes Section
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Personal Notes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite
                    )

                    OutlinedTextField(
                        value = userNotes,
                        onValueChange = {
                            userNotes = it
                            val updated = currentItem.copy(userNotes = it)
                            currentItem = updated
                            WebStore.updateItem(updated)
                        },
                        placeholder = { Text("Add personal thoughts, quotes, or notes...", color = TextGrayMedium) },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Item?", color = TextWhite) },
            text = { Text("Are you sure you want to remove \"${currentItem.title}\"?", color = TextGrayLight) },
            confirmButton = {
                Button(
                    onClick = {
                        WebStore.deleteItem(currentItem)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A), contentColor = TextWhite)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextGrayMedium)
                }
            },
            containerColor = DarkCardBg
        )
    }
}
