package com.lists.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.lists.app.R
import com.lists.app.data.api.EpisodeItem
import com.lists.app.data.api.OttPlatform
import com.lists.app.data.api.SearchRepository
import com.lists.app.data.db.ListItem
import com.lists.app.data.model.Category
import com.lists.app.data.model.DisplayBadge
import com.lists.app.data.model.RatingConverter
import com.lists.app.data.model.RatingScaleOption
import com.lists.app.data.repository.AppRepository
import com.lists.app.ui.components.CategoryIcon
import com.lists.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

// Helper to determine if an episode has not aired yet
private fun isFutureAirdate(airdate: String): Boolean {
    if (airdate.isBlank()) return false
    return try {
        val parts = airdate.trim().split("-")
        if (parts.size >= 3) {
            val y = parts[0].toIntOrNull() ?: return false
            val m = parts[1].toIntOrNull() ?: return false
            val d = parts[2].substring(0, 2).toIntOrNull() ?: return false
            val airdateStr = "$y-${if (m < 10) "0$m" else "$m"}-${if (d < 10) "0$d" else "$d"}"
            airdateStr > "2026-11-01"
        } else false
    } catch (_: Exception) {
        false
    }
}

fun triggerStrongTick(context: Any? = null) {
    com.lists.app.triggerStrongTick(context)
}

fun triggerAsmrTick(context: Any? = null) {
    com.lists.app.triggerAsmrTick(context)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    repository: AppRepository,
    onNavigateBack: () -> Unit
) {
    val item by repository.getItem(itemId).collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val searchRepo = remember { SearchRepository() }

    // Rating Systems Preferences
    val ratingPrefs = remember { context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editPictureUrl by remember { mutableStateOf("") }
    var editOverview by remember { mutableStateOf("") }
    var loadedEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var ottPlatforms by remember { mutableStateOf<List<OttPlatform>>(emptyList()) }

    if (item == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = TextWhite)
        }
        return
    }

    val currentItem = item!!
    val category = Category.fromId(currentItem.category)

    LaunchedEffect(currentItem.id, currentItem.coverUrl) {
        if (currentItem.coverUrl.isBlank()) {
            repository.ensureItemCover(currentItem)
        }
    }

    val configuredScale = remember(category) {
        val defaultScale = RatingScaleOption.getDefault(category)
        val saved = ratingPrefs.getString("rating_scale_${category.id}", defaultScale.id)
        RatingScaleOption.fromId(saved, defaultScale)
    }

    val parentList by repository.getList(currentItem.listId).collectAsState(initial = null)

    val isTvShow = category == Category.TV_SHOW
    val isMangaOrComic = (category == Category.BOOK) && (
            parentList?.name?.contains("manga", ignoreCase = true) == true ||
            parentList?.name?.contains("comic", ignoreCase = true) == true ||
            parentList?.name?.contains("manhwa", ignoreCase = true) == true ||
            parentList?.name?.contains("webtoon", ignoreCase = true) == true ||
            currentItem.title.contains("manga", ignoreCase = true) ||
            currentItem.title.contains("comic", ignoreCase = true) ||
            currentItem.overview.contains("manga", ignoreCase = true) ||
            currentItem.overview.contains("comic", ignoreCase = true) ||
            currentItem.overview.contains("chapter", ignoreCase = true) ||
            currentItem.externalUrl.contains("anilist", ignoreCase = true) ||
            currentItem.originalTitle.isNotEmpty() ||
            currentItem.totalPages > 0 ||
            currentItem.totalEpisodes > 0 ||
            currentItem.checkedEpisodesJson.isNotBlank() ||
            currentItem.episodesDataJson.isNotBlank()
    )

    // Automatically load overview for books if not already present
    LaunchedEffect(currentItem.id, currentItem.title) {
        if (category == Category.BOOK && currentItem.overview.isBlank()) {
            val desc = searchRepo.fetchBookOverview(currentItem.title)
            if (desc.isNotBlank()) {
                repository.updateItem(currentItem.copy(overview = desc))
            }
        }
    }

    // Fetch OTT streaming platforms for TV Shows and Movies
    LaunchedEffect(currentItem.id, currentItem.title, currentItem.category) {
        if (category == Category.TV_SHOW || category == Category.MOVIE) {
            val fetched = searchRepo.fetchOttPlatforms(currentItem.title, category)
            ottPlatforms = fetched
        }
    }

    // Load or generate episodes/chapters for TV shows, Anime & Manga/Comics
    LaunchedEffect(currentItem.id, currentItem.title, isMangaOrComic) {
        if (isTvShow) {
            if (currentItem.episodesDataJson.isNotBlank()) {
                val parsed = parseEpisodesJson(currentItem.episodesDataJson)
                if (parsed.isNotEmpty()) {
                    loadedEpisodes = parsed
                    return@LaunchedEffect
                }
            }

            // Fetch from TVMaze API
            isLoadingEpisodes = true
            val fetched = searchRepo.fetchEpisodes(currentItem.title)
            if (fetched.isNotEmpty()) {
                loadedEpisodes = fetched
                val jsonStr = serializeEpisodes(fetched)
                repository.saveCachedEpisodes(currentItem, jsonStr)
            } else {
                val total = if (currentItem.totalEpisodes > 0) currentItem.totalEpisodes else 12
                val generated = (1..total).map { num ->
                    EpisodeItem(season = 1, number = num, name = "Episode $num", airdate = "")
                }
                loadedEpisodes = generated
                val jsonStr = serializeEpisodes(generated)
                repository.saveCachedEpisodes(currentItem, jsonStr)
            }
            isLoadingEpisodes = false
        } else if (isMangaOrComic) {
            if (currentItem.episodesDataJson.isNotBlank()) {
                val parsed = parseEpisodesJson(currentItem.episodesDataJson)
                if (parsed.isNotEmpty()) {
                    loadedEpisodes = parsed
                    return@LaunchedEffect
                }
            }
            var total = if (currentItem.totalPages > 0) currentItem.totalPages
            else if (currentItem.totalEpisodes > 0) currentItem.totalEpisodes
            else 0

            if (total <= 0) {
                val liveCount = searchRepo.fetchMangaChapterCount(currentItem.title)
                if (liveCount > 0) {
                    total = liveCount
                    repository.updateItem(currentItem.copy(totalPages = liveCount))
                } else {
                    total = 24
                }
            }

            val generated = (1..total).map { num ->
                EpisodeItem(season = 1, number = num, name = "Chapter $num", airdate = "")
            }
            loadedEpisodes = generated
            val jsonStr = serializeEpisodes(generated)
            repository.saveCachedEpisodes(currentItem, jsonStr)
        }
    }

    val checkedKeys = remember(currentItem.checkedEpisodesJson) {
        repository.parseCheckedKeys(currentItem.checkedEpisodesJson)
    }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    // Edit item
                    IconButton(onClick = {
                        editName = currentItem.title
                        editDate = currentItem.releaseYear.ifEmpty { currentItem.releaseDate }
                        editPictureUrl = currentItem.coverUrl
                        editOverview = currentItem.overview
                        showEditDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Pin toggle
                    IconButton(onClick = {
                        triggerAsmrTick(context)
                        scope.launch { repository.toggleItemPinned(currentItem) }
                    }) {
                        Icon(
                            painter = painterResource(id = if (currentItem.pinned) R.drawable.ic_pin else R.drawable.ic_unpin),
                            contentDescription = "Pin",
                            tint = if (currentItem.pinned) TextWhite else TextGrayDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Delete item
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Delete",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        }
    ) { padding ->
        val horizontalPadding = 20.dp


        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = 8.dp)
        ) {
            // Header: Poster + Title + Year + Creator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box {
                    if (currentItem.coverUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentItem.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = currentItem.title,
                            modifier = Modifier
                                .size(width = 86.dp, height = 126.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkCardBg),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 86.dp, height = 126.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            CategoryIcon(
                                category = category,
                                tint = TextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Cover Badge according to active rating scale (ONLY if rated by user)
                    val badge = RatingConverter.getDisplayBadge(currentItem, configuredScale)
                    if (badge != null) {
                        Surface(
                            color = PureBlack.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkBorder),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(4.dp)
                        ) {
                            when (badge) {
                                is DisplayBadge.Text -> {
                                    Text(
                                        text = badge.label,
                                        color = TextWhite,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                is DisplayBadge.Numeric -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = TextWhite,
                                            modifier = Modifier.size(9.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = badge.formatted,
                                            color = TextWhite,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentItem.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        lineHeight = 26.sp
                    )

                    if (currentItem.releaseYear.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentItem.releaseYear,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    if (currentItem.creator.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentItem.creator,
                            fontSize = 12.sp,
                            color = TextGrayMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // OTT Streaming Links as direct clickable buttons
                    if (category == Category.TV_SHOW || category == Category.MOVIE) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (ottPlatforms.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Checking streaming availability...",
                                    color = TextGrayMedium,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(ottPlatforms) { platform ->
                                    val isJustWatch = platform.name.equals("JustWatch", ignoreCase = true)
                                    Surface(
                                        color = DarkCardElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            0.5.dp,
                                            if (isJustWatch) Color(0xFFFBC02D).copy(alpha = 0.6f) else DarkBorder
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                triggerStrongTick(context)
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(platform.url))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isJustWatch) Color(0xFFFBC02D) else TextWhite,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = platform.name,
                                                color = if (isJustWatch) Color(0xFFFBC02D) else TextWhite,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ACTION BUTTON (Movies, Regular Books, Games) - TV Shows & Manga/Comics are driven by episodes/chapters checklist
            if (category != Category.TV_SHOW && !isMangaOrComic) {
                CategoryActionButton(
                    category = category,
                    isMarked = currentItem.marked,
                    onToggle = {
                        triggerStrongTick(context)
                        scope.launch { repository.toggleItemMarked(currentItem) }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // DYNAMIC RATING SYSTEM BASED ON USER SETTINGS (WITH BIDIRECTIONAL BACKEND SYNC)
            when (configuredScale) {
                RatingScaleOption.SKIP_PERFECTION -> {
                    val currentReview = if (currentItem.reviewRating in 1..4) {
                        currentItem.reviewRating
                    } else if (currentItem.userRating > 5.0f) {
                        RatingConverter.tenPointToFourWord(currentItem.userRating)
                    } else if (currentItem.userRating > 0f) {
                        RatingConverter.fivePointToFourWord(currentItem.userRating)
                    } else {
                        0
                    }
                    MinimalTextReviewSlider(
                        currentReview = currentReview,
                        onReviewSelected = { newReview ->
                            val numeric = if (newReview == 0) 0f else RatingConverter.fourWordToFivePoint(newReview)
                            scope.launch {
                                repository.updateItem(
                                    currentItem.copy(
                                        reviewRating = newReview,
                                        userRating = numeric
                                    )
                                )
                            }
                        }
                    )
                }
                RatingScaleOption.SCALE_5 -> {
                    val currentRating5 = if (currentItem.userRating > 0f) {
                        if (currentItem.userRating > 5.0f) {
                            RatingConverter.tenPointToFivePoint(currentItem.userRating)
                        } else {
                            currentItem.userRating
                        }
                    } else if (currentItem.reviewRating in 1..4) {
                        RatingConverter.fourWordToFivePoint(currentItem.reviewRating)
                    } else {
                        0f
                    }
                    FlushAsmrRatingSlider(
                        currentRating = currentRating5,
                        maxRating = 5.0f,
                        onRatingChange = { newRating ->
                            val review = RatingConverter.fivePointToFourWord(newRating)
                            scope.launch {
                                repository.updateItem(
                                    currentItem.copy(
                                        userRating = newRating,
                                        reviewRating = review
                                    )
                                )
                            }
                        }
                    )
                }
                RatingScaleOption.SCALE_10 -> {
                    val currentRating10 = if (currentItem.userRating > 0f) {
                        if (currentItem.userRating <= 5.0f) {
                            RatingConverter.fivePointToTenPoint(currentItem.userRating)
                        } else {
                            currentItem.userRating
                        }
                    } else if (currentItem.reviewRating in 1..4) {
                        RatingConverter.fourWordToTenPoint(currentItem.reviewRating)
                    } else {
                        0f
                    }
                    FlushAsmrRatingSlider(
                        currentRating = currentRating10,
                        maxRating = 10.0f,
                        onRatingChange = { newRating ->
                            val review = RatingConverter.tenPointToFourWord(newRating)
                            scope.launch {
                                repository.updateItem(
                                    currentItem.copy(
                                        userRating = newRating,
                                        reviewRating = review
                                    )
                                )
                            }
                        }
                    )
                }
            }

            // DIVIDER AFTER RATING SCALE
            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(18.dp))

            // TV SHOWS, ANIME, MANGA & COMICS: Season-wise episode / Chapter checklist
            if (category == Category.TV_SHOW || isMangaOrComic) {
                SeasonEpisodeChecklistView(
                    item = currentItem,
                    episodes = loadedEpisodes,
                    checkedKeys = checkedKeys,
                    selectedSeason = selectedSeason,
                    isLoading = isLoadingEpisodes,
                    isManga = isMangaOrComic,
                    onSelectSeason = { selectedSeason = it },
                    onToggleEpisode = { key, allKeys ->
                        triggerStrongTick(context)
                        scope.launch { repository.toggleEpisodeChecked(currentItem, key, allKeys) }
                    },
                    onSelectAllSeason = { seasonKeys, shouldCheck, allKeys ->
                        triggerStrongTick(context)
                        scope.launch {
                            repository.setSeasonEpisodesChecked(currentItem, seasonKeys, shouldCheck, allKeys)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Overview / Description (Available for books, movies, shows, games)
            if (currentItem.overview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column {
                    Text(
                        text = "Overview",
                        color = TextGrayMedium,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentItem.overview,
                        color = TextWhite,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // External Link
            if (currentItem.externalUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.externalUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        .copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Online", color = TextWhite, fontSize = 13.sp)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkCardBg,
            title = { Text("Delete Item", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Remove \"${currentItem.title}\" from this list?", color = TextGrayLight) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteItem(currentItem)
                            showDeleteConfirm = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PureBlack)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextGrayMedium)
                }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = DarkCardBg,
            title = {
                Text(
                    text = "Edit Item",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name", color = TextGrayMedium) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextWhite, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("Release Date / Year", color = TextGrayMedium) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextWhite, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editPictureUrl,
                        onValueChange = { editPictureUrl = it },
                        label = { Text("Picture URL", color = TextGrayMedium) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = TextWhite, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (editPictureUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(editPictureUrl.trim())
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Preview",
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkCardElevated),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editOverview,
                        onValueChange = { editOverview = it },
                        label = { Text("Overview", color = TextGrayMedium) },
                        minLines = 3,
                        maxLines = 6,
                        textStyle = LocalTextStyle.current.copy(color = TextWhite, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.updateItem(
                                currentItem.copy(
                                    title = editName.trim(),
                                    releaseYear = editDate.trim(),
                                    releaseDate = editDate.trim(),
                                    coverUrl = editPictureUrl.trim(),
                                    overview = editOverview.trim(),
                                    updatedAt = com.lists.app.currentTimeMillis()
                                )
                            )
                        }
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PureBlack),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextGrayMedium)
                }
            }
        )
    }
}

// -----------------------------------------------------------------
// CATEGORY SPECIFIC ACTION BUTTONS
// -----------------------------------------------------------------

@Composable
fun CategoryActionButton(
    category: Category,
    isMarked: Boolean,
    onToggle: () -> Unit
) {
    val (unmarkedLabel, markedLabel) = when (category) {
        Category.BOOK -> "Mark as read" to "Read"
        Category.GAME -> "Mark as played" to "Played"
        else -> "Mark as watched" to "Watched"
    }

    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isMarked) TextWhite else DarkCardElevated,
            contentColor = if (isMarked) PureBlack else TextWhite
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (isMarked) null else ButtonDefaults.outlinedButtonBorder(enabled = true)
            .copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_marked),
                contentDescription = null,
                tint = if (isMarked) PureBlack else TextWhite,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isMarked) markedLabel else unmarkedLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

// -----------------------------------------------------------------
// MINIMAL TEXT REVIEW SLIDER (EXACT EQUAL GAPS, NO DOTS, NO BACKGROUND)
// -----------------------------------------------------------------

@Composable
fun MinimalTextReviewSlider(
    currentReview: Int,
    onReviewSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val labels = listOf("Skip", "Timepass", "Go for It", "Perfection")

    Column(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RATING",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            if (currentReview in 1..4) {
                Text(
                    text = labels[currentReview - 1],
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tactile segmented pill bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkCardElevated)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { idx, label ->
                val pos = idx + 1
                val isSelected = currentReview == pos

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSelected) TextWhite else Color.Transparent)
                        .clickable {
                            val newRating = if (isSelected) 0 else pos
                            triggerAsmrTick(context)
                            onReviewSelected(newRating)
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) PureBlack else TextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------
// FLUSH ASMR DECIMAL SLIDER (SUPPORTS 5-STAR & 10-POINT SCALE)
// -----------------------------------------------------------------

@Composable
fun FlushAsmrRatingSlider(
    currentRating: Float,
    maxRating: Float = 5.0f,
    onRatingChange: (Float) -> Unit
) {
    val context = LocalContext.current
    var lastHapticStep by remember { mutableIntStateOf((currentRating * 10).roundToInt()) }

    val safeRating = currentRating.coerceIn(0.0f, maxRating)
    val fraction = (safeRating / maxRating).coerceIn(0f, 1f)

    val landmarkDotsCount = maxRating.toInt() // 5 for 5-star, 10 for 10-point (0..N = N+1 dots)

    Column(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RATING",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Text(
                text = "${com.lists.app.formatDecimal1(safeRating)} / ${maxRating.toInt()}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Flush Decimal Slider with discrete dots pattern
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp)
                .pointerInput(maxRating) {
                    detectTapGestures { offset ->
                        val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val rawRating = newFraction * maxRating
                        val stepped = (rawRating * 10).roundToInt() / 10.0f
                        val stepInt = (stepped * 10).roundToInt()
                        if (stepInt != lastHapticStep) {
                            lastHapticStep = stepInt
                            triggerAsmrTick(context)
                        }
                        onRatingChange(stepped)
                    }
                }
                .pointerInput(maxRating) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        val rawRating = newFraction * maxRating
                        val stepped = (rawRating * 10).roundToInt() / 10.0f
                        val stepInt = (stepped * 10).roundToInt()
                        if (stepInt != lastHapticStep) {
                            lastHapticStep = stepInt
                            triggerAsmrTick(context)
                        }
                        onRatingChange(stepped)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val totalWidth = size.width

                // Fine 1dp guideline
                drawLine(
                    color = Color(0xFF222226),
                    start = Offset(0f, centerY),
                    end = Offset(totalWidth, centerY),
                    strokeWidth = 1.dp.toPx()
                )

                // Landmark dots (0, 1, 2, ... N)
                for (i in 0..landmarkDotsCount) {
                    val dotX = (i / maxRating) * totalWidth
                    drawCircle(
                        color = Color(0xFF424248),
                        radius = (if (landmarkDotsCount > 5) 2.5.dp else 3.dp).toPx(),
                        center = Offset(dotX, centerY)
                    )
                }

                // Tactile pure white thumb with subtle glow
                val knobX = fraction * totalWidth
                drawCircle(
                    color = Color(0x33FFFFFF),
                    radius = 14.dp.toPx(),
                    center = Offset(knobX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = Offset(knobX, centerY)
                )
            }
        }
    }
}

// -----------------------------------------------------------------
// SEASON & EPISODE CHECKLIST (INTEGRATED DIRECTLY INTO AMOLED BLACK)
// -----------------------------------------------------------------

@Composable
fun SeasonEpisodeChecklistView(
    item: ListItem,
    episodes: List<EpisodeItem>,
    checkedKeys: Set<String>,
    selectedSeason: Int,
    isLoading: Boolean,
    isManga: Boolean,
    onSelectSeason: (Int) -> Unit,
    onToggleEpisode: (String, List<String>) -> Unit,
    onSelectAllSeason: (List<String>, Boolean, List<String>) -> Unit
) {
    val context = LocalContext.current
    val seasons = remember(episodes) {
        episodes.map { it.season }.distinct().sorted().ifEmpty { listOf(1) }
    }

    val activeSeason = if (selectedSeason in seasons) selectedSeason else seasons.firstOrNull() ?: 1

    val seasonEpisodes = remember(episodes, activeSeason) {
        episodes.filter { it.season == activeSeason }
    }

    // Only count released (already aired) episodes in numbers and completion tracking
    val allReleasedEpisodes = remember(episodes) {
        episodes.filter { !isFutureAirdate(it.airdate) }
    }
    val allReleasedKeys = remember(allReleasedEpisodes) {
        allReleasedEpisodes.map { it.key }
    }

    val seasonReleasedEpisodes = remember(seasonEpisodes) {
        seasonEpisodes.filter { !isFutureAirdate(it.airdate) }
    }
    val seasonReleasedKeys = remember(seasonReleasedEpisodes) {
        seasonReleasedEpisodes.map { it.key }
    }

    val seasonWatchedCount = remember(seasonReleasedKeys, checkedKeys) {
        seasonReleasedKeys.count { checkedKeys.contains(it) }
    }

    val totalWatchedCount = remember(allReleasedKeys, checkedKeys) {
        allReleasedKeys.count { checkedKeys.contains(it) }
    }

    val isAllSeasonWatched = seasonReleasedKeys.isNotEmpty() && seasonWatchedCount == seasonReleasedKeys.size
    val isAllShowWatched = allReleasedKeys.isNotEmpty() && totalWatchedCount == allReleasedKeys.size

    // DIRECTLY INTEGRATED INTO AMOLED BLACK: NO GREY BACKGROUND CONTAINER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(vertical = 4.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isManga) "CHAPTERS" else "SEASONS & EPISODES",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextGrayMedium,
                letterSpacing = 1.sp
            )

            // Strictly white "Done" when complete, or "$watched/$total" of released episodes
            val progressText = when {
                isAllShowWatched || item.marked -> "Done"
                allReleasedKeys.isEmpty() -> "Upcoming"
                else -> "$totalWatchedCount/${allReleasedKeys.size}"
            }
            Text(
                text = progressText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Season Text Slider: Wrapped in Box with smooth guidelines/fades at both ends
        if (seasons.size > 1) {
            val seasonListState = rememberLazyListState()
            val canScrollSeasonLeft by remember {
                derivedStateOf { seasonListState.firstVisibleItemIndex > 0 || seasonListState.firstVisibleItemScrollOffset > 0 }
            }
            val canScrollSeasonRight by remember {
                derivedStateOf { seasonListState.canScrollForward }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    state = seasonListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(seasons) { s ->
                        val isSelected = s == activeSeason
                        val sReleased = episodes.filter { it.season == s && !isFutureAirdate(it.airdate) }
                        val sKeys = sReleased.map { it.key }
                        val sCount = sKeys.count { checkedKeys.contains(it) }
                        val isSeasonComplete = sKeys.isNotEmpty() && sCount == sKeys.size

                        val seasonLabel = when {
                            sKeys.isEmpty() -> "Season $s • Upcoming"
                            isSeasonComplete -> "Season $s • Done"
                            else -> "Season $s ($sCount/${sKeys.size})"
                        }

                        Surface(
                            color = if (isSelected) Color(0xFF1E1E22) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                0.5.dp,
                                if (isSelected) Color(0xFF48484E) else DarkBorder
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerStrongTick(context)
                                    onSelectSeason(s)
                                }
                        ) {
                            Text(
                                text = seasonLabel,
                                color = if (isSelected) TextWhite else TextGrayMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                val leftShadowAlpha by animateFloatAsState(
                    targetValue = if (canScrollSeasonLeft) 1f else 0f,
                    label = "leftSeasonShadow"
                )
                val rightShadowAlpha by animateFloatAsState(
                    targetValue = if (canScrollSeasonRight) 1f else 0f,
                    label = "rightSeasonShadow"
                )

                // Subtle minimal white shadow at left edge (appears when user slides right)
                if (leftShadowAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(18.dp)
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.14f * leftShadowAlpha),
                                        Color.White.copy(alpha = 0.04f * leftShadowAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Subtle minimal white shadow at right edge (visible by default to signify sliding)
                if (rightShadowAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(20.dp)
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.04f * rightShadowAlpha),
                                        Color.White.copy(alpha = 0.14f * rightShadowAlpha)
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Season Sub-header with Select All action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val subHeaderLabel = when {
                seasonReleasedKeys.isEmpty() -> if (seasons.size > 1) "Season $activeSeason • Upcoming" else "Upcoming"
                isAllSeasonWatched -> if (seasons.size > 1) "Season $activeSeason • Done" else "Done"
                else -> if (seasons.size > 1) "Season $activeSeason ($seasonWatchedCount/${seasonReleasedKeys.size})" else "$seasonWatchedCount/${seasonReleasedKeys.size}"
            }

            Text(
                text = subHeaderLabel,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )

            if (seasonReleasedKeys.isNotEmpty()) {
                Surface(
                    color = Color(0xFF161618),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, DarkBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            triggerStrongTick(context)
                            onSelectAllSeason(seasonReleasedKeys, !isAllSeasonWatched, allReleasedKeys)
                        }
                ) {
                    Text(
                        text = if (isAllSeasonWatched) "Uncheck All" else "Select All",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(6.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(20.dp))
            }
        } else {
            // Episode list integrated directly into pure black with rounded hover ripples
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                seasonEpisodes.forEach { ep ->
                    val isFuture = isFutureAirdate(ep.airdate)
                    val isChecked = checkedKeys.contains(ep.key)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (!isFuture) {
                                    Modifier.clickable {
                                        triggerStrongTick(context)
                                        onToggleEpisode(ep.key, allReleasedKeys)
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${ep.number}. ${ep.name}",
                                color = if (isFuture) TextGrayDark else if (isChecked) TextGrayMedium else TextWhite,
                                fontWeight = if (isChecked || isFuture) FontWeight.Normal else FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (ep.airdate.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isFuture) "Airs ${ep.airdate}" else ep.airdate,
                                    color = if (isFuture) Color(0xFF8E8E93) else TextGrayDark,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        if (isFuture) {
                            Surface(
                                color = Color(0xFF141416),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, DarkBorder)
                            ) {
                                Text(
                                    text = "Upcoming",
                                    color = TextGrayDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isChecked) TextWhite else Color.Transparent)
                                    .border(1.dp, if (isChecked) TextWhite else DarkBorder, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Checked",
                                        tint = PureBlack,
                                        modifier = Modifier.size(15.dp)
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

// -----------------------------------------------------------------
// JSON HELPERS
// -----------------------------------------------------------------

private fun serializeEpisodes(episodes: List<EpisodeItem>): String {
    return Json.encodeToString(episodes)
}

private fun parseEpisodesJson(json: String): List<EpisodeItem> {
    if (json.isBlank()) return emptyList()
    return try {
        Json.decodeFromString<List<EpisodeItem>>(json)
    } catch (e: Exception) {
        emptyList()
    }
}

