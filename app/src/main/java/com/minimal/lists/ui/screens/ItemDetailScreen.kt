package com.minimal.lists.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.minimal.lists.data.api.EpisodeItem
import com.minimal.lists.data.api.OttPlatform
import com.minimal.lists.data.api.SearchRepository
import com.minimal.lists.data.db.ListItem
import com.minimal.lists.data.model.Category
import com.minimal.lists.data.model.RatingConverter
import com.minimal.lists.data.reminder.EpisodeReminderManager
import com.minimal.lists.data.repository.AppRepository
import com.minimal.lists.ui.components.CoverImage
import com.minimal.lists.ui.components.triggerAsmrTick
import com.minimal.lists.ui.components.triggerStrongTick
import com.minimal.lists.ui.theme.*

fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0 && m > 0) "${h}h ${m}m" else if (h > 0) "${h}h" else "${m}m"
}

fun isEpisodeLocked(airdate: String): Boolean {
    if (airdate.isBlank()) return false
    if (airdate.contains("Upcoming", ignoreCase = true)) return true
    val cleanDate = airdate.trim()
    try {
        if (cleanDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val date = java.time.LocalDate.parse(cleanDate)
            return date.isAfter(java.time.LocalDate.now())
        }
    } catch (_: Exception) {}
    try {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH)
        val date = java.time.LocalDate.parse(cleanDate, formatter)
        return date.isAfter(java.time.LocalDate.now())
    } catch (_: Exception) {}
    try {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy", java.util.Locale.ENGLISH)
        val date = java.time.LocalDate.parse(cleanDate, formatter)
        return date.isAfter(java.time.LocalDate.now())
    } catch (_: Exception) {}
    try {
        val yearMatch = Regex("\\b(20\\d{2})\\b").find(cleanDate)?.value?.toIntOrNull()
        if (yearMatch != null) {
            val currentYear = java.time.LocalDate.now().year
            if (yearMatch > currentYear) return true
        }
    } catch (_: Exception) {}
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    repository: AppRepository,
    onNavigateBack: () -> Unit
) {
    val item by repository.getItem(itemId).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentItem = item ?: return
    val parentList by repository.getList(currentItem.listId).collectAsState(initial = null)
    val category = remember(currentItem.category, parentList?.category) {
        val catFromItem = currentItem.category.takeIf { it.isNotBlank() }?.let { Category.fromId(it) }
        val catFromParent = parentList?.category?.takeIf { it.isNotBlank() }?.let { Category.fromId(it) }
        catFromItem ?: catFromParent ?: Category.MOVIE
    }

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBookLogModal by remember { mutableStateOf(false) }
    var isOttDropdownOpen by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isWideLayout = isTablet
    val screenWidthDp = configuration.screenWidthDp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isOttDropdownOpen) {
                        isOttDropdownOpen = false
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Row - matching ListDetailScreen position and midline alignment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp, bottom = 8.dp)
                        .padding(horizontal = if (isWideLayout) 24.dp else 16.dp)
                        .height(44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isWideLayout) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
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
                            Text(
                                text = parentList?.name ?: "",
                                style = MaterialTheme.typography.headlineMedium,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onNavigateBack() }
                            )
                        }
                    } else {
                        // Wide / Landscape: Left border matches cover page below it!
                        Text(
                            text = parentList?.name ?: "",
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onNavigateBack() }
                        )
                    }

                    // Three dots on the right end: exactly matching vertical midline of 40.dp buttons
                    IconButton(
                        onClick = {
                            triggerAsmrTick(context)
                            showOptionsSheet = true
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Main Content Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isWideLayout) {
                        // Tablet/Landscape mode: 2-column layout
                        // Left column: Sticky enlarged cover spanning full available height, shifted up to match title card
                        // Right column: Vertically scrollable cards
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(0.36f)
                                    .fillMaxHeight()
                                    .offset(y = (-4).dp)
                                    .padding(end = 20.dp, bottom = 16.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                CoverImage(
                                    url = currentItem.coverUrl,
                                    category = category,
                                    uuid = currentItem.uuid,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(0.64f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // First card: details, watched/read/played, where to watch buttons
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        UnifiedTopHeroSection(
                                            item = currentItem,
                                            category = category,
                                            isOttDropdownOpen = isOttDropdownOpen,
                                            onToggleOtt = { isOttDropdownOpen = !isOttDropdownOpen },
                                            repository = repository,
                                            scope = scope,
                                            context = context,
                                            showCover = false
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                ItemCategoryBodySections(
                                    category = category,
                                    currentItem = currentItem,
                                    repository = repository,
                                    scope = scope,
                                    context = context,
                                    onOpenBookLog = { showBookLogModal = true }
                                )

                                Spacer(modifier = Modifier.height(48.dp))
                            }
                        }
                    } else {
                        // Phone / Portrait layout
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    UnifiedTopHeroSection(
                                        item = currentItem,
                                        category = category,
                                        isOttDropdownOpen = isOttDropdownOpen,
                                        onToggleOtt = { isOttDropdownOpen = !isOttDropdownOpen },
                                        repository = repository,
                                        scope = scope,
                                        context = context,
                                        showCover = true
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            ItemCategoryBodySections(
                                category = category,
                                currentItem = currentItem,
                                repository = repository,
                                scope = scope,
                                context = context,
                                onOpenBookLog = { showBookLogModal = true }
                            )

                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                }
            }
        }
    }

    // Modal: Item Options Bottom Sheet (Edit, Pin, Delete)
    if (showOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
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
                    text = currentItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Option 1: Edit
                Surface(
                    onClick = {
                        showOptionsSheet = false
                        showEditDialog = true
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

                // Option 2: Pin / Unpin
                Surface(
                    onClick = {
                        showOptionsSheet = false
                        triggerAsmrTick(context)
                        scope.launch { repository.toggleItemPinned(currentItem) }
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
                            Icons.Default.PushPin,
                            contentDescription = null,
                            tint = if (currentItem.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (currentItem.pinned) "Unpin from Top" else "Pin to Top",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Option 3: Delete
                Surface(
                    onClick = {
                        showOptionsSheet = false
                        showDeleteConfirm = true
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

    // Modal: Edit Item Dialog (Dynamic per category)
    if (showEditDialog) {
        var editTitle by remember { mutableStateOf(currentItem.title) }
        var editCreator by remember { mutableStateOf(currentItem.creator) }
        var editYear by remember { mutableStateOf(currentItem.releaseYear) }
        var editCoverUrl by remember { mutableStateOf(currentItem.coverUrl) }
        var editTotalPages by remember { mutableStateOf(if (currentItem.totalPages > 0) currentItem.totalPages.toString() else "") }
        var editOverview by remember { mutableStateOf(currentItem.overview) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
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
                        label = { Text(if (category == Category.BOOK || category == Category.COMIC || category == Category.MANGA) "Author / Writer" else "Creator / Studio / Director") },
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
                    if (category == Category.BOOK) {
                        OutlinedTextField(
                            value = editTotalPages,
                            onValueChange = { editTotalPages = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Total Pages") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (category == Category.MANGA || category == Category.COMIC) {
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
                        label = { Text(if (category == Category.BOOK || category == Category.GAME) "Synopsis" else "Overview") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditDialog = false
                        triggerStrongTick(context)
                        scope.launch {
                            val parsedPages = if (category == Category.BOOK || category == Category.MANGA || category == Category.COMIC) {
                                editTotalPages.toIntOrNull() ?: currentItem.totalPages
                            } else {
                                currentItem.totalPages
                            }
                            repository.updateItem(
                                currentItem.copy(
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
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal: Book Reading Log (Unified Time & Page & Total Pages)
    if (showBookLogModal) {
        BookUnifiedLogDialog(
            item = currentItem,
            onDismiss = { showBookLogModal = false },
            onConfirm = { addedMinutes, pageReached, newTotalPages ->
                showBookLogModal = false
                triggerStrongTick(context)
                scope.launch {
                    val finalTotalPages = if (newTotalPages != null && newTotalPages > 0) newTotalPages else currentItem.totalPages
                    val finalCurrentPage = pageReached ?: currentItem.currentPage
                    val isFinished = finalTotalPages > 0 && finalCurrentPage >= finalTotalPages
                    repository.updateItem(
                        currentItem.copy(
                            loggedMinutes = currentItem.loggedMinutes + addedMinutes,
                            currentPage = finalCurrentPage,
                            totalPages = finalTotalPages,
                            marked = if (isFinished) true else currentItem.marked
                        )
                    )
                }
            },
            onReset = {
                showBookLogModal = false
                triggerStrongTick(context)
                scope.launch {
                    repository.updateItem(
                        currentItem.copy(
                            loggedMinutes = 0,
                            currentPage = 0,
                            marked = false
                        )
                    )
                }
            }
        )
    }

    // Modal: Delete Confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(text = "Delete Item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "Remove “${currentItem.title}” from this list? This action cannot be undone.", style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            repository.deleteItem(currentItem)
                            onNavigateBack()
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
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// =========================================================================
// SUBCOMPONENTS: Category Body Sections & Unified Top Hero Section
// =========================================================================

@Composable
fun ItemCategoryBodySections(
    category: Category,
    currentItem: ListItem,
    repository: AppRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    onOpenBookLog: () -> Unit
) {
    when (category) {
        Category.MOVIE -> {
            RatingCardSection(item = currentItem, repository = repository, scope = scope, context = context)
            OverviewSection(currentItem.overview)
        }

        Category.TV_SHOW, Category.ANIME -> {
            RatingCardSection(item = currentItem, repository = repository, scope = scope, context = context)
            Spacer(modifier = Modifier.height(16.dp))
            SeasonEpisodeTracker(
                item = currentItem,
                repository = repository,
                scope = scope,
                context = context
            )
            OverviewSection(currentItem.overview)
        }

        Category.COMIC, Category.MANGA -> {
            RatingCardSection(item = currentItem, repository = repository, scope = scope, context = context)
            if (currentItem.totalPages > 0 || currentItem.totalEpisodes > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                ChaptersTracker(
                    item = currentItem,
                    repository = repository,
                    scope = scope,
                    context = context
                )
            }
            OverviewSection(currentItem.overview)
        }

        Category.BOOK -> {
            if (currentItem.totalPages > 0) {
                BookReadingLogCard(
                    item = currentItem,
                    onOpenLogModal = onOpenBookLog
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            RatingCardSection(item = currentItem, repository = repository, scope = scope, context = context)
            OverviewSection(currentItem.overview, title = "Synopsis")
        }

        Category.GAME, Category.BOARD_GAME -> {
            GameCompletionLogCard(
                item = currentItem,
                context = context
            )
            Spacer(modifier = Modifier.height(16.dp))
            RatingCardSection(item = currentItem, repository = repository, scope = scope, context = context)
            OverviewSection(currentItem.overview, title = "Synopsis")
        }

        else -> {
            RatingCardSection(item = currentItem, repository = repository, scope = scope, context = context)
            OverviewSection(currentItem.overview)
        }
    }
}

@Composable
fun UnifiedTopHeroSection(
    item: ListItem,
    category: Category,
    isOttDropdownOpen: Boolean,
    onToggleOtt: () -> Unit,
    repository: AppRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    showCover: Boolean = true
) {
    var ottPlatforms by remember { mutableStateOf<List<OttPlatform>>(emptyList()) }
    var isLoadingOtt by remember { mutableStateOf(false) }

    val hasOtt = category == Category.MOVIE || category == Category.TV_SHOW || category == Category.ANIME

    LaunchedEffect(item.title, category) {
        if (hasOtt) {
            isLoadingOtt = true
            ottPlatforms = SearchRepository().fetchOttPlatforms(item.title, category)
            isLoadingOtt = false
        }
    }

    val markLabel = if (item.marked) {
        when (category) {
            Category.MOVIE, Category.TV_SHOW, Category.ANIME -> "Watched"
            Category.BOOK, Category.COMIC, Category.MANGA -> "Read"
            Category.GAME, Category.BOARD_GAME -> "Played"
            else -> "Completed"
        }
    } else {
        when (category) {
            Category.MOVIE, Category.TV_SHOW, Category.ANIME -> "Mark Watched"
            Category.BOOK, Category.COMIC, Category.MANGA -> "Mark as Read"
            Category.GAME, Category.BOARD_GAME -> "Mark Played"
            else -> "Mark Completed"
        }
    }

    Column {
        if (showCover) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                CoverImage(
                    url = item.coverUrl,
                    category = category,
                    uuid = item.uuid,
                    modifier = Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 156.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    repeatDelayMillis = 1000,
                                    initialDelayMillis = 1200,
                                    velocity = 35.dp
                                )
                        )

                        if (item.releaseYear.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.releaseYear,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (item.creator.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val creatorPrefix = when (category) {
                                Category.MOVIE -> ""
                                Category.BOOK, Category.COMIC, Category.MANGA -> "by "
                                else -> ""
                            }
                            Text(
                                text = "$creatorPrefix${item.creator}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons fit beside cover poster for ALL media items
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Mark action button (Watched / Read / Played / Completed)
                        // Properly centered, font matching Where to Watch, NO tick mark icon when marked
                        Surface(
                            onClick = {
                                triggerStrongTick(context)
                                scope.launch { repository.toggleItemMarked(item) }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (item.marked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (item.marked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, if (item.marked) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = markLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Where to Watch button (only for Movies, TV Shows, Anime)
                        if (hasOtt) {
                            Surface(
                                onClick = {
                                    triggerAsmrTick(context)
                                    onToggleOtt()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isOttDropdownOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = if (isOttDropdownOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isOttDropdownOpen) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "Where to Watch",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Wide layout (no cover thumbnail since enlarged cover is pinned on the left)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                ) {
                    if (item.releaseYear.isNotBlank()) {
                        Text(
                            text = item.releaseYear,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.releaseYear.isNotBlank() && item.creator.isNotBlank()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (item.creator.isNotBlank()) {
                        val creatorPrefix = when (category) {
                            Category.MOVIE -> ""
                            Category.BOOK, Category.COMIC, Category.MANGA -> "by "
                            else -> ""
                        }
                        Text(
                            text = "$creatorPrefix${item.creator}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mark action button
                    Surface(
                        onClick = {
                            triggerStrongTick(context)
                            scope.launch { repository.toggleItemMarked(item) }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (item.marked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (item.marked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, if (item.marked) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = markLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Where to Watch button
                    if (hasOtt) {
                        Surface(
                            onClick = {
                                triggerAsmrTick(context)
                                onToggleOtt()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isOttDropdownOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (isOttDropdownOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isOttDropdownOpen) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "Where to Watch",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expanded Where to Watch Dropdown (beneath the hero row)
        if (hasOtt && isOttDropdownOpen) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLoadingOtt) {
                        Text(
                            text = "Finding streaming providers...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else if (ottPlatforms.isEmpty()) {
                        val defaultProviders = listOf("Netflix", "Amazon Prime", "Disney+", "Apple TV+")
                        Text(
                            text = "Available on popular platforms:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            defaultProviders.forEach { provider ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(
                                            text = provider,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        ottPlatforms.forEach { platform ->
                            Surface(
                                onClick = {
                                    if (platform.url.isNotBlank()) {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(platform.url)))
                                        } catch (_: Exception) {}
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = platform.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Rating Sliders (Shows ONLY selected scale, minimal, 10th decimals)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingCardSection(
    item: ListItem,
    repository: AppRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context
) {
    val ratingPrefs = remember { context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE) }
    val listScaleId = remember(item.listId) {
        ratingPrefs.getString("list_rating_scale_${item.listId}", "STARS_5") ?: "STARS_5"
    }

    if (listScaleId == "TIER_4") {
        // Skip to Perfection (Words on direct background in grey, when selected highlighted in accent)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rating",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val currentTier = when {
                        item.rating >= 8.75f -> "Perfection"
                        item.rating >= 6.25f -> "Go for it"
                        item.rating >= 3.75f -> "Timepass"
                        item.rating >= 1.25f -> "Skip"
                        else -> "—"
                    }
                    Text(
                        text = currentTier,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.rating > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val tiers = listOf(
                    "Skip" to 2.5f,
                    "Timepass" to 5.0f,
                    "Go for it" to 7.5f,
                    "Perfection" to 10.0f
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tiers.forEach { (label, value) ->
                        val isSelected = kotlin.math.abs(item.rating - value) < 1.25f && item.rating > 0f
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val newRating = if (isSelected) 0f else value
                                    val newReview = if (isSelected) 0 else when {
                                        value < 3f -> 1
                                        value < 6f -> 2
                                        value < 8f -> 3
                                        else -> 4
                                    }
                                    triggerStrongTick(context)
                                    scope.launch {
                                        repository.updateItem(
                                            item.copy(
                                                rating = newRating,
                                                userRating = newRating,
                                                reviewRating = newReview
                                            )
                                        )
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    } else if (listScaleId == "STARS_10") {
        // Minimal 10-Scale Slider Card (Native smooth track + thumb dot + guideline dots)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rating",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val score10 = (item.rating * 10f).roundToInt() / 10f
                    Text(
                        text = if (score10 > 0f) "$score10 / 10" else "— / 10",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (score10 > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = item.rating.coerceIn(0f, 10f),
                    onValueChange = { newRating10 ->
                        val rounded10 = (newRating10 * 10f).roundToInt() / 10f
                        if (rounded10 != item.rating) {
                            triggerAsmrTick(context)
                            scope.launch {
                                repository.updateItem(
                                    item.copy(
                                        rating = rounded10,
                                        userRating = rounded10,
                                        reviewRating = RatingConverter.tenPointToFourWord(rounded10)
                                    )
                                )
                            }
                        }
                    },
                    valueRange = 0f..10f,
                    steps = 0,
                    track = { sliderState ->
                        val activeDotColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
                        val inactiveDotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        val currentVal = sliderState.value.coerceIn(0f, 10f)
                        val progress = currentVal / 10f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    drawContent()
                                    val width = size.width
                                    val height = size.height
                                    val activeWidth = width * progress
                                    val dotRadius = 2.dp.toPx()
                                    for (i in 1..9) {
                                        val fraction = i.toFloat() / 10f
                                        val dotX = width * fraction
                                        val dotColor = if (dotX <= activeWidth) activeDotColor else inactiveDotColor
                                        drawCircle(
                                            color = dotColor,
                                            radius = dotRadius,
                                            center = Offset(dotX, height / 2f)
                                        )
                                    }
                                }
                        ) {
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        // Minimal 5-Star Slider Card (Native smooth track + thumb dot)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rating",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val score5 = ((item.rating / 2.0f) * 10f).roundToInt() / 10f
                    Text(
                        text = if (score5 > 0f) "$score5 / 5" else "— / 5",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (score5 > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = (item.rating / 2.0f).coerceIn(0f, 5f),
                    onValueChange = { newRating5 ->
                        val rounded5 = (newRating5 * 10f).roundToInt() / 10f
                        val newRating10 = rounded5 * 2.0f
                        if (newRating10 != item.rating) {
                            triggerAsmrTick(context)
                            scope.launch {
                                repository.updateItem(
                                    item.copy(
                                        rating = newRating10,
                                        userRating = rounded5,
                                        reviewRating = RatingConverter.fivePointToFourWord(rounded5)
                                    )
                                )
                            }
                        }
                    },
                    valueRange = 0f..5f,
                    steps = 0,
                    track = { sliderState ->
                        val activeDotColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
                        val inactiveDotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        val currentVal = sliderState.value.coerceIn(0f, 5f)
                        val progress = currentVal / 5f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    drawContent()
                                    val width = size.width
                                    val height = size.height
                                    val activeWidth = width * progress
                                    val dotRadius = 2.dp.toPx()
                                    for (i in 1..4) {
                                        val fraction = i.toFloat() / 5f
                                        val dotX = width * fraction
                                        val dotColor = if (dotX <= activeWidth) activeDotColor else inactiveDotColor
                                        drawCircle(
                                            color = dotColor,
                                            radius = dotRadius,
                                            center = Offset(dotX, height / 2f)
                                        )
                                    }
                                }
                        ) {
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: TV Shows & Anime Episode Tracker (Matching Stitch Screenshot)
// =========================================================================

@Composable
fun SeasonEpisodeTracker(
    item: ListItem,
    repository: AppRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context
) {
    var rawEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf(1) }

    LaunchedEffect(item.title, item.episodesDataJson) {
        if (item.episodesDataJson.isNotBlank()) {
            try {
                val arr = JSONArray(item.episodesDataJson)
                val list = mutableListOf<EpisodeItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        EpisodeItem(
                            season = obj.optInt("season", 1),
                            number = obj.optInt("number", i + 1),
                            name = obj.optString("name", "Episode ${i + 1}"),
                            airdate = obj.optString("airdate", ""),
                            airstamp = obj.optString("airstamp", "")
                        )
                    )
                }
                rawEpisodes = list
            } catch (_: Exception) {}
        }

        if (rawEpisodes.isEmpty()) {
            val fetched = SearchRepository().fetchEpisodes(item.title)
            if (fetched.isNotEmpty()) {
                rawEpisodes = fetched
                val arr = JSONArray()
                fetched.forEach { ep ->
                    val obj = JSONObject()
                    obj.put("season", ep.season)
                    obj.put("number", ep.number)
                    obj.put("name", ep.name)
                    obj.put("airdate", ep.airdate)
                    obj.put("airstamp", ep.airstamp)
                    arr.put(obj)
                }
                repository.saveCachedEpisodes(item, arr.toString())
            }
        }
    }

    if (rawEpisodes.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Episodes Available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val checkedKeys = remember(item.checkedEpisodesJson) { repository.parseCheckedKeys(item.checkedEpisodesJson) }
    val allEpisodeKeys = remember(rawEpisodes) { rawEpisodes.map { it.key } }

    val distinctSeasons = remember(rawEpisodes) {
        val s = rawEpisodes.map { it.season }.distinct().sorted()
        if (s.isEmpty()) listOf(1) else s
    }

    LaunchedEffect(distinctSeasons) {
        if (!distinctSeasons.contains(selectedSeason) && distinctSeasons.isNotEmpty()) {
            selectedSeason = distinctSeasons.first()
        }
    }

    val totalCount = rawEpisodes.size
    val checkedTotal = rawEpisodes.count { checkedKeys.contains(it.key) }
    val totalPct = if (totalCount > 0) (checkedTotal * 100 / totalCount) else 0

    val episodesInSeason = rawEpisodes.filter { it.season == selectedSeason }
    val seasonChecked = episodesInSeason.count { checkedKeys.contains(it.key) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$checkedTotal / $totalCount ($totalPct%)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val allSeasonChecked = episodesInSeason.isNotEmpty() && seasonChecked == episodesInSeason.size
                Surface(
                    onClick = {
                        triggerStrongTick(context)
                        val unlockedKeys = episodesInSeason.filter { !isEpisodeLocked(it.airdate) }.map { it.key }
                        scope.launch {
                            repository.setSeasonEpisodesChecked(
                                item = item,
                                seasonKeys = unlockedKeys,
                                shouldCheck = !allSeasonChecked,
                                allKeys = allEpisodeKeys
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (allSeasonChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.height(28.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = "Season $selectedSeason: $seasonChecked / ${episodesInSeason.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (allSeasonChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                distinctSeasons.forEach { s ->
                    val isTabActive = s == selectedSeason
                    Surface(
                        onClick = {
                            triggerAsmrTick(context)
                            selectedSeason = s
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isTabActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                            Text(
                                text = "Season $s",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTabActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                episodesInSeason.forEach { ep ->
                    val isChecked = checkedKeys.contains(ep.key)
                    val isLocked = isEpisodeLocked(ep.airdate)

                    Surface(
                        onClick = {
                            if (!isLocked) {
                                triggerAsmrTick(context)
                                scope.launch {
                                    repository.toggleEpisodeChecked(item, ep.key, allEpisodeKeys)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Ep ${ep.number}: ${ep.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isLocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val cleanAirdate = ep.airdate.trim()
                                val dateSubtitle = if (isLocked) "Upcoming • $cleanAirdate" else cleanAirdate
                                if (dateSubtitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = dateSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            if (isLocked) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    var isReminderActive by remember(item.id, ep.key) {
                                        mutableStateOf(EpisodeReminderManager.isReminderSet(context, item.id, ep.key))
                                    }

                                    IconButton(
                                        onClick = {
                                            triggerAsmrTick(context)
                                            val newStatus = EpisodeReminderManager.toggleReminder(context, item, ep)
                                            isReminderActive = newStatus
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isReminderActive) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                                            contentDescription = if (isReminderActive) "Reminder enabled" else "Enable reminder",
                                            tint = if (isReminderActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isChecked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .border(
                                            1.5.dp,
                                            if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isChecked) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Checked",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Comics & Manga Chapters Tracker
// =========================================================================

@Composable
fun ChaptersTracker(
    item: ListItem,
    repository: AppRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context
) {
    val totalChapters = if (item.totalPages > 0) item.totalPages else item.totalEpisodes
    if (totalChapters <= 0) {
        var showSetChaptersDialog by remember { mutableStateOf(false) }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Chapters Not Configured",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Set the total number of chapters to track your reading progress volume by volume.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { showSetChaptersDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Set Total Chapters", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showSetChaptersDialog) {
            var inputChapters by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showSetChaptersDialog = false },
                title = { Text("Set Total Chapters", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = inputChapters,
                        onValueChange = { inputChapters = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Total Chapters") },
                        placeholder = { Text("e.g. 72") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val count = inputChapters.toIntOrNull() ?: 0
                            if (count > 0) {
                                showSetChaptersDialog = false
                                triggerStrongTick(context)
                                scope.launch {
                                    repository.updateItem(item.copy(totalPages = count, totalEpisodes = count))
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSetChaptersDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp)
            )
        }
        return
    }

    val checkedKeys = remember(item.checkedEpisodesJson) { repository.parseCheckedKeys(item.checkedEpisodesJson) }

    val chapters = remember(totalChapters) {
        (1..totalChapters).map { chNum ->
            "CH$chNum" to chNum
        }
    }
    val allChapterKeys = remember(chapters) { chapters.map { it.first } }

    val checkedTotal = chapters.count { checkedKeys.contains(it.first) }
    val totalPct = if (totalChapters > 0) (checkedTotal * 100 / totalChapters) else 0
    val allChecked = totalChapters > 0 && checkedTotal == totalChapters

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$checkedTotal / $totalChapters ($totalPct%)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    onClick = {
                        triggerStrongTick(context)
                        scope.launch {
                            repository.setSeasonEpisodesChecked(
                                item = item,
                                seasonKeys = allChapterKeys,
                                shouldCheck = !allChecked,
                                allKeys = allChapterKeys
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (allChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.height(28.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = if (allChecked) "All Read" else "Mark All",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (allChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                chapters.forEach { (key, num) ->
                    val isChecked = checkedKeys.contains(key)
                    Surface(
                        onClick = {
                            triggerAsmrTick(context)
                            scope.launch {
                                repository.toggleEpisodeChecked(item, key, allChapterKeys)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chapter $num",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isChecked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .border(
                                        1.5.dp,
                                        if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Checked",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Books Reading Log Card (Single "Log" button)
// =========================================================================

@Composable
fun BookReadingLogCard(
    item: ListItem,
    onOpenLogModal: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reading Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    onClick = onOpenLogModal,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.height(32.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = "Log",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Time Logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMinutes(item.loggedMinutes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Current Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (item.totalPages > 0) "${item.currentPage} / ${item.totalPages} p" else "${item.currentPage} p",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (item.totalPages > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val progress = (item.currentPage.toFloat() / item.totalPages.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Game Completion Log Card (Inline single-value hour edit)
// =========================================================================

@Composable
fun GameCompletionLogCard(
    item: ListItem,
    context: Context
) {
    val prefs = remember { context.getSharedPreferences("lists_game_completion_prefs", Context.MODE_PRIVATE) }
    var storyHours by remember {
        mutableStateOf(prefs.getInt("game_${item.id}_story_hours", prefs.getInt("game_${item.id}_story", 0) / 60))
    }
    var extraHours by remember {
        mutableStateOf(prefs.getInt("game_${item.id}_extra_hours", prefs.getInt("game_${item.id}_extra", 0) / 60))
    }
    var compHours by remember {
        mutableStateOf(prefs.getInt("game_${item.id}_comp_hours", prefs.getInt("game_${item.id}_comp", 0) / 60))
    }

    var editingKey by remember { mutableStateOf<String?>(null) }
    var editHoursText by remember { mutableStateOf("") }

    val divisions = listOf(
        Triple("Story", "story", storyHours),
        Triple("Story + Extra", "extra", extraHours),
        Triple("Completion", "comp", compHours)
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Completion Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                divisions.forEach { (name, key, currentH) ->
                    val isEditingThis = editingKey == key

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (isEditingThis) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(52.dp)
                                            .height(32.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(6.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicTextField(
                                            value = editHoursText,
                                            onValueChange = {
                                                editHoursText = it.filter { ch -> ch.isDigit() }.take(4)
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "h",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = {
                                            val h = editHoursText.toIntOrNull() ?: 0
                                            prefs.edit().putInt("game_${item.id}_${key}_hours", h).apply()
                                            when (key) {
                                                "story" -> storyHours = h
                                                "extra" -> extraHours = h
                                                "comp" -> compHours = h
                                            }
                                            editingKey = null
                                            triggerAsmrTick(context)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Save",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { editingKey = null },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    onClick = {
                                        triggerAsmrTick(context)
                                        editingKey = key
                                        editHoursText = if (currentH > 0) currentH.toString() else ""
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.CenterEnd,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = if (currentH > 0) "${currentH}h" else "Log",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Overview / Synopsis
// =========================================================================

@Composable
fun OverviewSection(
    overview: String,
    title: String = "Overview"
) {
    if (overview.isNotBlank()) {
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Generic Action Buttons
// =========================================================================

@Composable
fun FullWidthActionButton(
    label: String,
    isMarked: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isMarked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (isMarked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (isMarked) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMarked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================================================
// DIALOGS: Unified Reading Log (Time, Page Reached, Total Pages) & Game Log
// =========================================================================

@Composable
fun BookUnifiedLogDialog(
    item: ListItem,
    onDismiss: () -> Unit,
    onConfirm: (addedMinutes: Int, pageReached: Int?, newTotalPages: Int?) -> Unit,
    onReset: () -> Unit
) {
    var hoursInput by remember { mutableStateOf("0") }
    var minutesInput by remember { mutableStateOf("30") }
    var pageInput by remember { mutableStateOf(if (item.currentPage > 0) item.currentPage.toString() else "") }
    var totalPagesInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Log Reading Session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Time Log
                Text(
                    text = "TIME SPENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = hoursInput,
                            onValueChange = { hoursInput = it.filter { c -> c.isDigit() }.take(3) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        )
                    }
                    Text(
                        text = "h",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, end = 16.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = minutesInput,
                            onValueChange = {
                                val v = it.filter { c -> c.isDigit() }.take(2)
                                val num = v.toIntOrNull() ?: 0
                                if (num < 60) minutesInput = v
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        )
                    }
                    Text(
                        text = "m",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    listOf("+15m" to 15, "+30m" to 30, "+1h" to 60).forEach { (label, addMins) ->
                        Surface(
                            onClick = {
                                val totalMin = ((hoursInput.toIntOrNull() ?: 0) * 60) + (minutesInput.toIntOrNull() ?: 0) + addMins
                                hoursInput = (totalMin / 60).toString()
                                minutesInput = (totalMin % 60).toString()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                // Section 2: Page Reached with bounds validation
                Text(
                    text = "PROGRESS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { ch -> ch.isDigit() }
                        val num = digits.toIntOrNull() ?: 0
                        val maxAllowed = if (item.totalPages > 0) {
                            item.totalPages
                        } else {
                            totalPagesInput.toIntOrNull() ?: Int.MAX_VALUE
                        }
                        if (digits.isEmpty() || num <= maxAllowed) {
                            pageInput = digits
                        }
                    },
                    label = { Text("Page Reached") },
                    placeholder = { Text(if (item.currentPage > 0) "${item.currentPage}" else "e.g. 120") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Option to set total pages if missing
                if (item.totalPages <= 0) {
                    OutlinedTextField(
                        value = totalPagesInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { ch -> ch.isDigit() }
                            totalPagesInput = digits
                            val maxP = digits.toIntOrNull()
                            if (maxP != null && (pageInput.toIntOrNull() ?: 0) > maxP) {
                                pageInput = maxP.toString()
                            }
                        },
                        label = { Text("Total Pages (Optional)") },
                        placeholder = { Text("e.g. 350") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = hoursInput.toIntOrNull() ?: 0
                    val m = minutesInput.toIntOrNull() ?: 0
                    val addedMin = (h * 60) + m
                    val reached = pageInput.toIntOrNull()
                    val newTotal = totalPagesInput.toIntOrNull()
                    onConfirm(addedMin, reached, newTotal)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp)
    )
}


