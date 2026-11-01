package com.lists.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lists.app.data.model.*
import com.lists.app.ui.icons.ListyIcons
import com.lists.app.ui.theme.*

@Composable
fun CategoryIcon(
    category: Category,
    modifier: Modifier = Modifier,
    tint: Color = TextWhite
) {
    val icon = when (category) {
        Category.MOVIE -> ListyIcons.Movie
        Category.TV_SHOW -> ListyIcons.TvShow
        Category.BOOK -> ListyIcons.Book
        Category.GAME -> ListyIcons.VideoGame
    }
    Icon(
        imageVector = icon,
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
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CategoryIcon(
                category = category,
                modifier = Modifier.size(32.dp),
                tint = TextGrayDark
            )
        }
    }
}

@Composable
fun ItemGridCard(
    item: ListItem,
    category: Category,
    onClick: () -> Unit,
    onToggleMarked: () -> Unit,
    modifier: Modifier = Modifier,
    activeScale: RatingScaleOption? = null
) {
    val scale = activeScale ?: RatingScaleOption.getDefault(category)
    val displayBadge = RatingConverter.getDisplayBadge(item, scale)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(DarkCardElevated)
            ) {
                if (item.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.coverUrl,
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
                            modifier = Modifier.size(40.dp),
                            tint = TextGrayDark
                        )
                    }
                }

                // Top Left: Pin badge
                if (item.pinned) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PureBlack.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ListyIcons.Pin,
                            contentDescription = "Pinned",
                            tint = AccentWhite,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Top Right: Rating badge
                if (displayBadge != null) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PureBlack.copy(alpha = 0.85f))
                            .border(0.5.dp, DarkBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (displayBadge) {
                            is DisplayBadge.Text -> {
                                Text(
                                    text = displayBadge.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentWhite
                                )
                            }
                            is DisplayBadge.Numeric -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = AccentWhite,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = displayBadge.formatted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentWhite
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Right: Checkmark toggle button
                IconButton(
                    onClick = onToggleMarked,
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.marked) AccentWhite else PureBlack.copy(alpha = 0.75f)
                        )
                ) {
                    Icon(
                        imageVector = ListyIcons.Marked,
                        contentDescription = "Done",
                        tint = if (item.marked) PureBlack else TextGrayMedium,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Info section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.releaseYear.isNotEmpty() || item.creator.isNotEmpty()) {
                    Text(
                        text = listOf(item.releaseYear, item.creator)
                            .filter { it.isNotEmpty() }
                            .joinToString(" • "),
                        fontSize = 11.sp,
                        color = TextGrayMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ListyGridTile(
    userList: UserList,
    itemCount: Int,
    onClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = Category.fromId(userList.category)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(116.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIcon(
                    category = category,
                    modifier = Modifier.size(24.dp),
                    tint = TextWhite
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = itemCount.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextGrayMedium
                    )
                    IconButton(
                        onClick = onMoreOptionsClick,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextGrayMedium,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = userList.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
