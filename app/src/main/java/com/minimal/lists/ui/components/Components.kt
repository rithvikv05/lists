package com.minimal.lists.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.minimal.lists.data.db.ListItem
import com.minimal.lists.data.model.Category
import com.minimal.lists.data.model.DisplayBadge
import com.minimal.lists.data.model.RatingConverter
import com.minimal.lists.data.model.RatingScaleOption
import com.minimal.lists.data.storage.OfflineStorageManager
import com.minimal.lists.ui.theme.*
import java.io.File


fun triggerAsmrTick(context: Context) {
    try {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(10)
        }
    } catch (_: Exception) {}
}

fun triggerStrongTick(context: Context) {
    try {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25)
        }
    } catch (_: Exception) {}
}

/**
 * Unified, compact search bar with vertically centered text and zero clipping.
 */
@Composable
fun ListsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String = "Search...",
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
    isLoading: Boolean = false
) {
    val internalRequester = remember { FocusRequester() }
    val effectiveRequester = focusRequester ?: internalRequester
    val focusManager = LocalFocusManager.current

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            effectiveRequester.requestFocus()
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(effectiveRequester)
                )
            }

            // Embedded loading indicator inside search bar beside clear button
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (query.isNotEmpty() || onClose != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}



@Composable
fun CoverImage(
    url: String,
    category: Category,
    uuid: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storageManager = remember { OfflineStorageManager.getInstance(context) }
    val modelData = remember(uuid, url) { storageManager.getDisplayCover(uuid, url) }
    val hasValidImage = (url.isNotBlank() && url != "null") || (modelData is java.io.File && modelData.exists())

    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (hasValidImage) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(modelData)
                    .crossfade(100)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(if (modelData is java.io.File) CachePolicy.DISABLED else CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}



/**
 * Discrete List Card directly from Stitch screen #11 (Discrete 1)
 */
/**
 * Accented Progress Tracker Ring with % completion inside.
 */
/**
 * Official Material 3 Circular Progress Indicator matching https://m3.material.io/components/progress-indicators/overview
 * - Thick rounded stroke (StrokeCap.Round)
 * - M3 primary accent color for active indicator
 * - M3 primary-tinted / secondaryContainer track with rounded stroke
 * - Zero numbers or text inside for a pure, minimal aesthetic
 * - Clean filled accent check badge when 100% complete
 */
@Composable
fun Material3CircularProgressIndicator(
    percentage: Int,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    strokeWidth: Dp = 3.2.dp,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
    showCheckWhenComplete: Boolean = true
) {
    val isComplete = percentage >= 100
    val pctFloat = (percentage.coerceIn(0, 100) / 100f)

    if (isComplete && showCheckWhenComplete) {
        Surface(
            shape = CircleShape,
            color = indicatorColor,
            modifier = modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size((size.value * 0.65f).dp)
                )
            }
        }
    } else {
        Canvas(modifier = modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val radius = (this.size.minDimension - stroke) / 2f
            val center = this.center

            // Full circular background track (soft rounded track)
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )

            // Active M3 progress indicator arc with rounded ends
            val effectiveSweep = if (isComplete) 360f else (360f * pctFloat)
            if (effectiveSweep > 0f) {
                drawArc(
                    color = indicatorColor,
                    startAngle = -90f,
                    sweepAngle = effectiveSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Calculates in-progress percentage for an item.
 * Supports TV shows, Anime, Books, Comics, Manga, and any media with tracked progress or completion.
 */
fun getItemProgressPercentage(item: ListItem, category: Category? = null): Int? {
    val effectiveCat = category ?: runCatching { Category.fromId(item.category) }.getOrNull()

    // 1. Exclude movies, games, board games, podcasts, music entirely from progress rings
    if (effectiveCat == Category.MOVIE ||
        effectiveCat == Category.GAME ||
        effectiveCat == Category.BOARD_GAME ||
        effectiveCat == Category.PODCAST ||
        effectiveCat == Category.MUSIC) {
        return null
    }

    // 2. TV show / Anime: ONLY if show has episode details (totalEpisodes > 0)
    if (effectiveCat == Category.TV_SHOW || effectiveCat == Category.ANIME) {
        val checkedKeysCount = if (item.checkedEpisodesJson.isNotBlank()) {
            try {
                org.json.JSONArray(item.checkedEpisodesJson).length()
            } catch (_: Exception) {
                item.currentEpisode
            }
        } else item.currentEpisode

        val totalEps = when {
            item.totalEpisodes > 0 -> item.totalEpisodes
            item.episodesDataJson.isNotBlank() -> {
                try {
                    org.json.JSONArray(item.episodesDataJson).length()
                } catch (_: Exception) {
                    0
                }
            }
            else -> 0
        }
        if (totalEps <= 0) return null
        return ((checkedKeysCount.toFloat() / totalEps.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    // 3. Book: ONLY if book has total pages filled (totalPages > 0)
    if (effectiveCat == Category.BOOK) {
        val totalPages = item.totalPages
        if (totalPages <= 0) return null
        return ((item.currentPage.toFloat() / totalPages.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    // 4. Manga / Comic: ONLY if manga/comic has chapter (totalEpisodes > 0) or page details (totalPages > 0)
    if (effectiveCat == Category.MANGA || effectiveCat == Category.COMIC) {
        val totalUnits = when {
            item.totalPages > 0 -> item.totalPages
            item.totalEpisodes > 0 -> item.totalEpisodes
            item.episodesDataJson.isNotBlank() -> {
                try {
                    org.json.JSONArray(item.episodesDataJson).length()
                } catch (_: Exception) { 0 }
            }
            else -> 0
        }
        if (totalUnits <= 0) return null

        val checkedChaptersCount = if (item.checkedEpisodesJson.isNotBlank()) {
            try {
                org.json.JSONArray(item.checkedEpisodesJson).length()
            } catch (_: Exception) { 0 }
        } else 0

        val currentUnit = when {
            checkedChaptersCount > 0 -> checkedChaptersCount
            item.currentPage > 0 -> item.currentPage
            item.currentEpisode > 0 -> item.currentEpisode
            else -> 0
        }

        return ((currentUnit.toFloat() / totalUnits.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    return null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscreteListCard(
    title: String,
    category: Category,
    itemCount: Int,
    completedCount: Int = 0,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.5.dp,
        border = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Row 1: Category UPPERCASE Name, and Item Count + Pin right-aligned
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$itemCount items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: List Title + Progress Percentage Ring + Chevron Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (itemCount > 0) {
                        val pct = ((completedCount.toFloat() / itemCount.toFloat()) * 100f).roundToInt()
                        Material3CircularProgressIndicator(
                            percentage = pct,
                            size = 22.dp,
                            strokeWidth = 3.2.dp
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}



/**
 * Bento Collection Card directly from Stitch Collections photo (Big rectangular 4-line boxes, 2D minimal, long-press to manage)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BentoCollectionCard(
    title: String,
    category: Category,
    itemCount: Int,
    completedCount: Int = 0,
    isPinned: Boolean = false,
    previewItems: List<ListItem> = emptyList(),
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.5.dp,
        border = null,
        modifier = modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Line 1: Category Tag (UPPERCASE) and Pin Icon (if pinned, in accent color)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )

                if (isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Lines 2-3: Collection Title (Exactly 2 lines reserved space)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 27.sp,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth()
            )

            // Line 4: Divider & Item Count & Progress Percentage Ring
            Column {
                HorizontalDivider(
                    thickness = 1.35.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$itemCount items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (itemCount > 0) {
                            val pct = ((completedCount.toFloat() / itemCount.toFloat()) * 100f).roundToInt()
                            Material3CircularProgressIndicator(
                                percentage = pct,
                                size = 20.dp,
                                strokeWidth = 3.0.dp
                            )
                        }

                        if (previewItems.isNotEmpty()) {
                            PosterFanStack(items = previewItems, category = category)
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)



/**
 * Overlapping 3-poster fan stack matching Stitch card preview
 */
@Composable
fun PosterFanStack(
    items: List<ListItem>,
    category: Category,
    modifier: Modifier = Modifier
) {
    val displayItems = items.take(3)
    Box(
        modifier = modifier
            .width(64.dp)
            .height(34.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        displayItems.forEachIndexed { index, item ->
            val rotation = when (index) {
                0 -> -8f
                1 -> -2f
                else -> 4f
            }
            val offsetX = (index * 14).dp
            val elevation = (index + 1) * 2

            Box(
                modifier = Modifier
                    .offset(x = -offsetX)
                    .zIndex(index.toFloat())
                    .rotate(rotation)
                    .width(24.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            ) {
                CoverImage(
                    url = item.coverUrl,
                    category = category,
                    uuid = item.uuid,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}



/**
 * Scalable Category Selector Modal Bottom Sheet (Stitch Screen #00 / #06)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScalableCategoryModal(
    categories: List<Category>,
    selectedCategory: Category?,
    showAllMedia: Boolean = true,
    showArchive: Boolean = false,
    isArchiveSelected: Boolean = false,
    categoryCounts: Map<String, Int> = emptyMap(),
    archivedCount: Int = 0,
    onSelectCategory: (Category?) -> Unit,
    onSelectArchive: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, categories) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.surfaceContainerHighest) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header (clean, subtext & cross removed, adjusted gap)
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search in Category Picker
            ListsSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholderText = "Search categories..."
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2-Column Categories Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // All Media Item (only shown if showAllMedia is true)
                if (showAllMedia) {
                    item {
                        val isAllSelected = selectedCategory == null && !isArchiveSelected
                        Surface(
                            onClick = {
                                onSelectCategory(null)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAllSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isAllSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "All Media",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                val totalCount = categoryCounts.values.sum()
                                if (totalCount > 0) {
                                    Text(
                                        text = "$totalCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { cat ->
                    val isSelected = selectedCategory == cat && !isArchiveSelected
                    val count = categoryCounts[cat.id] ?: 0

                    Surface(
                        onClick = {
                            onSelectCategory(cat)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = cat.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (count > 0) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                // Hidden Archive Category Item (only shown if showArchive is true)
                if (showArchive && (searchQuery.isBlank() || "archived".contains(searchQuery, ignoreCase = true) || "archive".contains(searchQuery, ignoreCase = true))) {
                    item {
                        Surface(
                            onClick = {
                                onSelectArchive()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isArchiveSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isArchiveSelected) MaterialTheme.colorScheme.tertiary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = "Archived",
                                        tint = if (isArchiveSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Archived",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isArchiveSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isArchiveSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (archivedCount > 0) {
                                    Text(
                                        text = "$archivedCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
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

/**
 * New List Modal directly from Stitch Screen #00 (CURRENT_new_list_modal.html)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewListModal(
    onDismiss: () -> Unit,
    onCreate: (name: String, category: Category) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Category.MOVIE) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dropdownListState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Title
            Text(
                text = "New List",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.3).sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // List Name input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "List Name",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(
                            text = "e.g. Cyberpunk Books 2025",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category selector with dropdown
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = { dropdownExpanded = !dropdownExpanded },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (dropdownExpanded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedCategory.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )

                        Icon(
                            imageVector = if (dropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (dropdownExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Dropdown menu items: EXACTLY 4 VISIBLE AT ONCE WITH SCROLLBAR
                if (dropdownExpanded) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        ) {
                            LazyColumn(
                                state = dropdownListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(Category.activeCategories) { cat ->
                                    val isSelected = cat == selectedCategory
                                    Surface(
                                        onClick = {
                                            selectedCategory = cat
                                            dropdownExpanded = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = cat.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Small vertical scrollbar indicator on the right
                            val firstIndex = dropdownListState.firstVisibleItemIndex
                            val totalCount = Category.entries.size
                            val maxScrollIndex = (totalCount - 4).coerceAtLeast(1)
                            val scrollFraction = (firstIndex.toFloat() / maxScrollIndex).coerceIn(0f, 1f)

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 2.dp, top = (scrollFraction * (168 - 44)).dp)
                                    .width(3.dp)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons (Compact, 2D minimal, centered text, no + icon, matching New List FAB)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val isValid = name.trim().isNotEmpty()
                Surface(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreate(trimmed, selectedCategory)
                        }
                    },
                    enabled = isValid,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    shadowElevation = 0.dp,
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create List",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
}



/**
 * Edit List Modal matching Stitch design and 2D minimal style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditListModal(
    initialName: String,
    initialCategory: Category,
    onDismiss: () -> Unit,
    onSave: (name: String, category: Category) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dropdownListState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Edit List",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.3).sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "List Name",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = { dropdownExpanded = !dropdownExpanded },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (dropdownExpanded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedCategory.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )

                        Icon(
                            imageVector = if (dropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (dropdownExpanded) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                state = dropdownListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(Category.activeCategories) { cat ->
                                    val isSelected = cat == selectedCategory
                                    Surface(
                                        onClick = {
                                            selectedCategory = cat
                                            dropdownExpanded = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = cat.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
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

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val isValid = name.trim().isNotEmpty()
                Surface(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            onSave(trimmed, selectedCategory)
                        }
                    },
                    enabled = isValid,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    shadowElevation = 0.dp,
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
}



/**
 * Grid Media Card for List Detail (3-column poster grid matching Stitch #17)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridMediaCard(
    item: ListItem,
    category: Category,
    scale: RatingScaleOption = RatingScaleOption.SCALE_10,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
        ) {
            CoverImage(
                url = item.coverUrl,
                category = category,
                uuid = item.uuid,
                modifier = Modifier.fillMaxSize()
            )

            // Pin badge on top-left if pinned (Adapts to light accent / dark theme)
            if (item.pinned) {
                val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                val pinBg = if (isLight) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.65f)
                val pinTint = if (isLight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                        .size(22.dp)
                        .background(pinBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = pinTint,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Progress loading circle filler on lower left corner with % completion inside
            val progressPct = getItemProgressPercentage(item, category)
            if (progressPct != null) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomStart)
                        .size(26.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val currentAccent = com.minimal.lists.ui.theme.LocalAppAccent.current
                    val coverAccent = currentAccent.darkPrimary
                    Material3CircularProgressIndicator(
                        percentage = progressPct,
                        size = 18.dp,
                        strokeWidth = 2.8.dp,
                        indicatorColor = coverAccent,
                        trackColor = coverAccent.copy(alpha = 0.25f),
                        showCheckWhenComplete = false
                    )
                }
            }

            // Accented Material 3 completion circle on lower right corner
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.marked) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.55f)
                    )
                    .border(
                        width = if (item.marked) 0.dp else 1.5.dp,
                        color = if (item.marked) Color.Transparent else Color.White.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
                    .clickable {
                        onToggleComplete?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (item.marked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * List Media Card for List Detail (Horizontal card matching Stitch #17)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListMediaRow(
    item: ListItem,
    category: Category,
    scale: RatingScaleOption,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        border = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(
                url = item.coverUrl,
                category = category,
                uuid = item.uuid,
                modifier = Modifier
                    .width(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = listOf(item.creator, item.releaseYear).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // In list mode: keep progress ring right to the left of the completed circle
            val progressPct = getItemProgressPercentage(item, category)
            if (progressPct != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Material3CircularProgressIndicator(
                    percentage = progressPct,
                    size = 20.dp,
                    strokeWidth = 3.0.dp,
                    showCheckWhenComplete = false
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Accented Material 3 completion circle on right end
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.marked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                    )
                    .border(
                        width = if (item.marked) 0.dp else 1.5.dp,
                        color = if (item.marked) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
                    .clickable {
                        onToggleComplete?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (item.marked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

