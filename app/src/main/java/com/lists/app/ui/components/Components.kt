package com.lists.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lists.app.R
import android.content.Context
import androidx.compose.runtime.remember
import com.lists.app.data.model.DisplayBadge
import com.lists.app.data.model.RatingConverter
import com.lists.app.data.model.RatingScaleOption
import com.lists.app.data.db.ListItem
import com.lists.app.data.model.Category
import com.lists.app.ui.screens.triggerStrongTick
import com.lists.app.ui.theme.*

@Composable
fun CategoryIcon(
    category: Category,
    modifier: Modifier = Modifier,
    tint: Color = TextWhite
) {
    Icon(
        painter = painterResource(id = category.iconRes),
        contentDescription = category.displayName,
        modifier = modifier,
        tint = tint
    )
}

@Composable
fun PosterImage(
    url: String,
    category: Category,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f / 3f
) {
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(6.dp))
            .background(DarkCardElevated)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(id = category.iconRes),
                contentDescription = null,
                tint = TextGrayDark,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// Exactly reproduces item_item_grid.xml from Listy
@Composable
fun ItemGridCard(
    item: ListItem,
    category: Category,
    onClick: () -> Unit,
    onToggleMarked: () -> Unit,
    modifier: Modifier = Modifier,
    activeScale: RatingScaleOption? = null
) {
    val context = LocalContext.current
    val ratingPrefs = remember { context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE) }
    val scale = activeScale ?: run {
        val defaultScale = RatingScaleOption.getDefault(category)
        val saved = ratingPrefs.getString("rating_scale_${category.id}", defaultScale.id)
        RatingScaleOption.fromId(saved, defaultScale)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkCardElevated)
                .border(0.5.dp, DarkBorder, RoundedCornerShape(6.dp))
        ) {
            if (item.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CategoryIcon(
                        category = category,
                        tint = TextGrayDark,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Top Badges: Pin (left) and Rating (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (item.pinned) {
                    Surface(
                        color = PureBlack.copy(alpha = 0.75f),
                        shape = CircleShape,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pin),
                                contentDescription = "Pinned",
                                tint = TextWhite,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                val badge = RatingConverter.getDisplayBadge(item, scale)
                if (badge != null) {
                    Surface(
                        color = PureBlack.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkBorder),
                        modifier = Modifier.padding(1.dp)
                    ) {
                        when (badge) {
                            is DisplayBadge.Text -> {
                                Text(
                                    text = badge.label,
                                    color = TextWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            is DisplayBadge.Numeric -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = TextWhite,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = badge.formatted,
                                        color = TextWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom-right: Checkmark / Marked icon (matching bg_marked_icon.xml from Listy)
            IconButton(
                onClick = {
                    triggerStrongTick(context)
                    onToggleMarked()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(28.dp)
            ) {
                Surface(
                    color = if (item.marked) TextWhite else PureBlack.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_marked),
                            contentDescription = if (item.marked) "Completed" else "Incomplete",
                            tint = if (item.marked) PureBlack else TextGrayMedium,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = item.title,
            color = if (item.marked) TextGrayMedium else TextWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        // Subtitle (Release year or episode progress)
        val subtitle = when {
            category == Category.TV_SHOW && item.currentEpisode > 0 -> "S${item.currentSeason} E${item.currentEpisode}"
            category == Category.BOOK && item.currentPage > 0 -> "Page ${item.currentPage}"
            item.releaseYear.isNotEmpty() -> item.releaseYear
            item.creator.isNotEmpty() -> item.creator
            else -> ""
        }

        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = TextGrayMedium,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Exactly reproduces item_item_list_checkable.xml from Listy
@Composable
fun ItemListRow(
    item: ListItem,
    category: Category,
    onClick: () -> Unit,
    onToggleMarked: () -> Unit,
    modifier: Modifier = Modifier,
    activeScale: RatingScaleOption? = null
) {
    val context = LocalContext.current
    val ratingPrefs = remember { context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE) }
    val scale = activeScale ?: run {
        val defaultScale = RatingScaleOption.getDefault(category)
        val saved = ratingPrefs.getString("rating_scale_${category.id}", defaultScale.id)
        RatingScaleOption.fromId(saved, defaultScale)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(DarkCardBg)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        PosterImage(
            url = item.coverUrl,
            category = category,
            modifier = Modifier
                .width(46.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.pinned) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pin),
                        contentDescription = "Pinned",
                        tint = TextWhite,
                        modifier = Modifier
                            .size(13.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(
                    text = item.title,
                    color = if (item.marked) TextGrayMedium else TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            val meta = buildString {
                if (item.releaseYear.isNotEmpty()) append(item.releaseYear)
                if (item.creator.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(item.creator)
                }
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    color = TextGrayMedium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val badge = RatingConverter.getDisplayBadge(item, scale)
            if (badge != null) {
                Surface(
                    color = DarkCardElevated,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkBorder),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    when (badge) {
                        is DisplayBadge.Text -> {
                            Text(
                                text = badge.label,
                                color = TextWhite,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        is DisplayBadge.Numeric -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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

            if (category == Category.TV_SHOW && item.currentEpisode > 0) {
                Text(
                    text = "S${item.currentSeason} E${item.currentEpisode}${if (item.totalEpisodes > 0) "/${item.totalEpisodes}" else ""}",
                    color = TextGrayLight,
                    fontSize = 11.sp
                )
            } else if (category == Category.BOOK && item.currentPage > 0) {
                Text(
                    text = "Page ${item.currentPage}${if (item.totalPages > 0) "/${item.totalPages}" else ""}",
                    color = TextGrayLight,
                    fontSize = 11.sp
                )
            }
        }

        IconButton(onClick = {
            triggerStrongTick(context)
            onToggleMarked()
        }) {
            Surface(
                color = if (item.marked) TextWhite else PureBlack,
                shape = CircleShape,
                border = if (item.marked) null else CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_marked),
                        contentDescription = null,
                        tint = if (item.marked) PureBlack else TextGrayDark,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
