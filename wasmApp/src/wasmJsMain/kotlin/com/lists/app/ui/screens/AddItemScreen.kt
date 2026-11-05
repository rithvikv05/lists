package com.lists.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lists.app.formatDecimal1
import com.lists.app.statusBarsPadding
import com.lists.app.triggerAsmrTick
import com.lists.app.triggerStrongTick
import com.lists.app.data.api.BookSearchType
import com.lists.app.data.api.SearchRepository
import com.lists.app.data.model.Category
import com.lists.app.data.model.SearchResult
import com.lists.app.data.repository.AppRepository
import com.lists.app.ui.components.*
import com.lists.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    listId: Long,
    repository: AppRepository,
    initialQuery: String? = null,
    onNavigateBack: () -> Unit
) {
    val list by repository.getList(listId).collectAsState(initial = null)
    val existingItems by repository.getItems(listId).collectAsState(initial = emptyList())
    val searchRepo = remember { SearchRepository() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var activeCategory by remember { mutableStateOf(Category.MOVIE) }
    var hasUserSelectedCategory by remember { mutableStateOf(false) }

    LaunchedEffect(list?.category) {
        if (!hasUserSelectedCategory && list != null) {
            activeCategory = Category.fromId(list!!.category)
        }
    }
    var showCategoryModal by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedYear by remember { mutableStateOf<String?>(null) }
    var isYearMenuOpen by remember { mutableStateOf(false) }

    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var isLanguageMenuOpen by remember { mutableStateOf(false) }

    var bookSearchType by remember { mutableStateOf(BookSearchType.ALL) }
    var isBookSearchTypeMenuOpen by remember { mutableStateOf(false) }

    val yearOptions = remember {
        listOf("All", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2015", "2010", "2000s", "1990s")
    }

    val languageOptions = remember {
        listOf(
            Pair<String?, String>(null, "All"),
            // Popular Global
            Pair("en", "English (EN)"),
            Pair("ja", "Japanese (JA)"),
            Pair("ko", "Korean (KO)"),
            Pair("es", "Spanish (ES)"),
            Pair("fr", "French (FR)"),
            Pair("de", "German (DE)"),
            Pair("zh", "Chinese (ZH)"),
            Pair("it", "Italian (IT)"),
            Pair("ru", "Russian (RU)"),
            Pair("pt", "Portuguese (PT)"),
            Pair("ar", "Arabic (AR)"),
            // Indian Languages
            Pair("hi", "Hindi (HI)"),
            Pair("ta", "Tamil (TA)"),
            Pair("te", "Telugu (TE)"),
            Pair("ml", "Malayalam (ML)"),
            Pair("kn", "Kannada (KN)"),
            Pair("bn", "Bengali (BN)"),
            Pair("mr", "Marathi (MR)"),
            Pair("gu", "Gujarati (GU)"),
            Pair("pa", "Punjabi (PA)"),
            Pair("ur", "Urdu (UR)"),
            Pair("or", "Odia (OR)"),
            Pair("as", "Assamese (AS)")
        )
    }

    var previewItem by remember { mutableStateOf<SearchResult?>(null) }

    val toggleItem: (SearchResult) -> Unit = { res ->
        val existing = existingItems.firstOrNull {
            it.title.equals(res.title, ignoreCase = true) &&
                    (res.releaseYear.isEmpty() || it.releaseYear == res.releaseYear)
        }
        triggerStrongTick()
        scope.launch {
            if (existing != null) {
                repository.deleteItem(existing)
                snackbarHostState.showSnackbar("Removed “${res.title}”")
            } else {
                repository.addItemFromSearch(listId, res)
                snackbarHostState.showSnackbar("Added “${res.title}”")
            }
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }

    LaunchedEffect(searchQuery, activeCategory, selectedYear, selectedLanguage, bookSearchType) {
        val q = searchQuery.trim()
        if (q.length >= 2) {
            isLoading = true
            delay(160)
            val results = searchRepo.search(
                query = q,
                category = activeCategory,
                bookSearchType = bookSearchType,
                selectedYear = selectedYear,
                selectedLanguage = selectedLanguage
            )
            searchResults = results
            isLoading = false
        } else {
            searchResults = emptyList()
            isLoading = false
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWideLayout = screenWidthDp >= 600

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .padding(horizontal = if (isWideLayout) 24.dp else 16.dp)
            ) {
                // Top Header Row with Category Pill precisely aligned with Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp, bottom = 8.dp)
                        .height(44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isWideLayout) {
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
                        }
                        Column {
                            Text(
                                text = "Add Item",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = list?.name ?: "List",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Category switch button (precisely right-aligned with Search Bar)
                    Surface(
                        onClick = {
                            triggerAsmrTick()
                            showCategoryModal = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = activeCategory.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Unified Search Bar matching Stitch (42dp height, centered text, embedded spinner beside close)
                ListsSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = activeCategory.searchHint,
                    focusRequester = focusRequester,
                    autoFocus = true,
                    isLoading = isLoading,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Category-specific Filters under search bar (compact, natural widths, equal spacing)
                val isBooksOrComics = activeCategory == Category.BOOK || activeCategory == Category.COMIC || activeCategory == Category.MANGA
                val isGame = activeCategory == Category.GAME || activeCategory == Category.BOARD_GAME
                val isShowLang = !isGame

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Year Filter (All categories)
                    val isYearActive = selectedYear != null
                    Box {
                        Surface(
                            onClick = {
                                if (isYearActive) selectedYear = null else isYearMenuOpen = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isYearActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (isYearActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isYearActive) "Year: $selectedYear" else "Year",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isYearActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isYearActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isYearActive) Icons.Default.Close else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = if (isYearActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isYearMenuOpen,
                            onDismissRequest = { isYearMenuOpen = false }
                        ) {
                            yearOptions.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text(y, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        selectedYear = if (y == "All") null else y
                                        isYearMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // 2. Language Filter (Movies, TV Shows, Anime, Books, Comics, Manga)
                    if (isShowLang) {
                        val isLangActive = selectedLanguage != null
                        Box {
                            Surface(
                                onClick = {
                                    if (isLangActive) selectedLanguage = null else isLanguageMenuOpen = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isLangActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (isLangActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isLangActive) "Lang: ${selectedLanguage?.uppercase()}" else "Lang",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isLangActive) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isLangActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (isLangActive) Icons.Default.Close else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = if (isLangActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isLanguageMenuOpen,
                                onDismissRequest = { isLanguageMenuOpen = false }
                            ) {
                                languageOptions.forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            selectedLanguage = code
                                            isLanguageMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Search Mode Filter (Books, Comics, Manga: Both / Title / Author)
                    if (isBooksOrComics) {
                        val isTypeFiltered = bookSearchType != BookSearchType.ALL
                        Box {
                            Surface(
                                onClick = { isBookSearchTypeMenuOpen = true },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isTypeFiltered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (isTypeFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val typeLabel = when (bookSearchType) {
                                        BookSearchType.ALL -> "Title/Author"
                                        BookSearchType.TITLE -> "Title"
                                        BookSearchType.AUTHOR -> "Author"
                                    }
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isTypeFiltered) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isTypeFiltered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = if (isTypeFiltered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isBookSearchTypeMenuOpen,
                                onDismissRequest = { isBookSearchTypeMenuOpen = false }
                            ) {
                                listOf(
                                    BookSearchType.ALL to "Title/Author",
                                    BookSearchType.TITLE to "Title",
                                    BookSearchType.AUTHOR to "Author"
                                ).forEach { (type, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            bookSearchType = type
                                            isBookSearchTypeMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Results Feed
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 36.dp)
                ) {
                    // Custom Item Card
                    if (searchQuery.trim().isNotEmpty()) {
                        item {
                            Surface(
                                onClick = {
                                    triggerStrongTick()
                                    scope.launch {
                                        repository.addCustomItem(listId, searchQuery.trim(), activeCategory.id)
                                        snackbarHostState.showSnackbar("Added “${searchQuery.trim()}”")
                                        searchQuery = ""
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Add as custom item",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "“${searchQuery.trim()}”",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Search Results List
                    items(searchResults, key = { it.title + it.releaseYear + it.creator }) { result ->
                        val isAlreadyAdded = existingItems.any {
                            it.title.equals(result.title, ignoreCase = true) &&
                                    (result.releaseYear.isEmpty() || it.releaseYear == result.releaseYear)
                        }

                        var isPreviewActive by remember { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(result) {
                                    detectTapGestures(
                                        onPress = {
                                            isPreviewActive = false
                                            val holdJob = scope.launch {
                                                delay(260)
                                                isPreviewActive = true
                                                triggerStrongTick()
                                                previewItem = result
                                            }
                                            try {
                                                tryAwaitRelease()
                                            } finally {
                                                holdJob.cancel()
                                                if (isPreviewActive) {
                                                    previewItem = null
                                                }
                                            }
                                        },
                                        onTap = {
                                            if (!isPreviewActive) {
                                                toggleItem(result)
                                            }
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoverImage(
                                    url = result.coverUrl,
                                    category = activeCategory,
                                    modifier = Modifier
                                        .width(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val subtitle = listOf(result.creator, result.releaseYear)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")

                                    if (subtitle.isNotEmpty()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        toggleItem(result)
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (isAlreadyAdded) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = if (isAlreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAlreadyAdded) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (isAlreadyAdded) "Remove" else "Add",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating Quick Look Hover / Popup Preview on Long Press
            AnimatedVisibility(
                visible = previewItem != null,
                enter = fadeIn(animationSpec = tween(140)) + scaleIn(initialScale = 0.92f, animationSpec = tween(140)),
                exit = fadeOut(animationSpec = tween(100)) + scaleOut(targetScale = 0.92f, animationSpec = tween(100))
            ) {
                previewItem?.let { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.76f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                previewItem = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 330.dp)
                                .fillMaxWidth(0.88f)
                                .padding(vertical = 24.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            shadowElevation = 16.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(18.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Enlarged Cover Image
                                CoverImage(
                                    url = item.coverUrl,
                                    category = item.category,
                                    modifier = Modifier
                                        .width(160.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(12.dp))
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val metaLine = listOf(item.creator, item.releaseYear, item.category.displayName)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                if (metaLine.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = metaLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                val stats = buildList {
                                    if (item.rating > 0f) {
                                        add("★ ${formatDecimal1(item.rating)}")
                                    }
                                    if (item.totalPages > 0) {
                                        add("${item.totalPages} pages")
                                    }
                                    if (item.totalEpisodes > 0) {
                                        add("${item.totalEpisodes} eps")
                                    }
                                }
                                if (stats.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        stats.forEach { stat ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = stat,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (item.overview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = item.overview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 6,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 17.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Release finger to dismiss",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCategoryModal) {
        ScalableCategoryModal(
            categories = Category.activeCategories,
            selectedCategory = activeCategory,
            showAllMedia = false,
            showArchive = false,
            onSelectCategory = { cat ->
                if (cat != null) {
                    hasUserSelectedCategory = true
                    activeCategory = cat
                    selectedYear = null
                    selectedLanguage = null
                    bookSearchType = BookSearchType.ALL
                    showCategoryModal = false
                }
            },
            onDismiss = { showCategoryModal = false }
        )
    }
}
