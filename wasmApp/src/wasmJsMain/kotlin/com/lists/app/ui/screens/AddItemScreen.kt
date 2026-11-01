package com.lists.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lists.app.R
import com.lists.app.data.api.BookSearchType
import com.lists.app.data.api.BookSubFilter
import com.lists.app.data.api.SearchRepository
import com.lists.app.data.api.TvSubFilter
import com.lists.app.data.model.Category
import com.lists.app.data.model.SearchResult
import com.lists.app.data.repository.AppRepository
import com.lists.app.ui.components.CategoryIcon
import com.lists.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    listId: Long,
    repository: AppRepository,
    onNavigateBack: () -> Unit
) {
    val list by repository.getList(listId).collectAsState(initial = null)
    val existingItems by repository.getItems(listId).collectAsState(initial = emptyList())
    val searchRepo = remember { SearchRepository() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Category is fixed and decided by the folder/list!
    val category = list?.let { Category.fromId(it.category) } ?: Category.MOVIE

    var selectedBookFilter by remember(list?.name) {
        val name = list?.name?.lowercase() ?: ""
        mutableStateOf(
            when {
                name.contains("manga") -> BookSubFilter.MANGA
                name.contains("comic") -> BookSubFilter.COMICS
                else -> BookSubFilter.ALL
            }
        )
    }

    var selectedBookSearchType by remember { mutableStateOf(BookSearchType.ALL) }
    var isBookSearchTypeMenuOpen by remember { mutableStateOf(false) }

    var selectedTvFilter by remember(list?.name) {
        val name = list?.name?.lowercase() ?: ""
        mutableStateOf(
            when {
                name.contains("anime") -> TvSubFilter.ANIME
                else -> TvSubFilter.ALL
            }
        )
    }

    var selectedYear by remember { mutableStateOf<String?>(null) }
    var isYearMenuOpen by remember { mutableStateOf(false) }

    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var isLanguageMenuOpen by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchQuery, category, selectedBookFilter, selectedTvFilter, selectedBookSearchType, selectedYear, selectedLanguage) {
        val q = searchQuery.trim()
        if (q.startsWith("http://", ignoreCase = true) || q.startsWith("https://", ignoreCase = true)) {
            isLoading = true
            val results = searchRepo.search(q, category, selectedBookFilter, selectedTvFilter, selectedBookSearchType, selectedYear, selectedLanguage)
            searchResults = results
            isLoading = false
        } else if (q.length >= 2) {
            isLoading = true
            delay(250)
            val results = searchRepo.search(q, category, selectedBookFilter, selectedTvFilter, selectedBookSearchType, selectedYear, selectedLanguage)
            searchResults = results
            isLoading = false
        } else {
            searchResults = emptyList()
            isLoading = false
        }
    }

    val searchPlaceholder = remember(category, selectedBookSearchType) {
        if (category == Category.BOOK) {
            when (selectedBookSearchType) {
                BookSearchType.AUTHOR -> "Search author name..."
                BookSearchType.TITLE -> "Search book title..."
                BookSearchType.ALL -> "Search books, authors, manga or link..."
            }
        } else {
            "${category.searchHint} or paste link..."
        }
    }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val yearOptions = remember {
        listOf("All", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2015", "2010", "2000s", "1990s")
    }

    val languageOptions = remember {
        listOf(
            Pair<String?, String>(null, "All"),
            Pair("en", "English (EN)"),
            Pair("ja", "Japanese (JA)"),
            Pair("ko", "Korean (KO)"),
            Pair("es", "Spanish (ES)"),
            Pair("fr", "French (FR)"),
            Pair("de", "German (DE)"),
            Pair("hi", "Hindi (HI)"),
            Pair("zh", "Chinese (ZH)"),
            Pair("it", "Italian (IT)")
        )
    }

    Scaffold(
        containerColor = PureBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = searchPlaceholder,
                                color = TextGrayMedium,
                                fontSize = 14.sp
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = TextWhite),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextGrayMedium,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = TextWhite,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
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
        ) {
            // Horizontal scrolling bar for all filters (Category sub-filters, Year, Country/Language)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Book sub-filters
                if (category == Category.BOOK) {
                    items(BookSubFilter.values().toList()) { filter ->
                        val isSelected = selectedBookFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) TextWhite else DarkCardElevated)
                                .clickable { selectedBookFilter = filter }
                                .padding(horizontal = 13.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) PureBlack else TextSecondary
                            )
                        }
                    }

                    // Book/Author toggle pill
                    item {
                        val isCustomType = selectedBookSearchType != BookSearchType.ALL
                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isCustomType) TextWhite else DarkCardElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (isCustomType) TextWhite else BorderSubtle,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        if (isCustomType) {
                                            selectedBookSearchType = BookSearchType.ALL
                                        } else {
                                            isBookSearchTypeMenuOpen = true
                                        }
                                    }
                                    .padding(horizontal = 13.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isCustomType) "By: ${selectedBookSearchType.label} ✕" else "Search: Book/Author ▾",
                                    fontSize = 12.sp,
                                    fontWeight = if (isCustomType) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCustomType) PureBlack else TextSecondary
                                )
                            }

                            DropdownMenu(
                                expanded = isBookSearchTypeMenuOpen,
                                onDismissRequest = { isBookSearchTypeMenuOpen = false },
                                modifier = Modifier.background(DarkCardElevated)
                            ) {
                                BookSearchType.values().forEach { bType ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = when (bType) {
                                                    BookSearchType.ALL -> "All (Book or Author)"
                                                    BookSearchType.TITLE -> "Book Title Only"
                                                    BookSearchType.AUTHOR -> "Author Name Only"
                                                },
                                                fontSize = 13.sp,
                                                color = if (bType == selectedBookSearchType) TextWhite else TextSecondary
                                            )
                                        },
                                        onClick = {
                                            selectedBookSearchType = bType
                                            isBookSearchTypeMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // TV / Anime sub-filters
                if (category == Category.TV_SHOW) {
                    items(TvSubFilter.values().toList()) { filter ->
                        val isSelected = selectedTvFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) TextWhite else DarkCardElevated)
                                .clickable { selectedTvFilter = filter }
                                .padding(horizontal = 13.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) PureBlack else TextSecondary
                            )
                        }
                    }
                }

                // Year Filter Pill & Dropdown
                item {
                    val isYearActive = selectedYear != null
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isYearActive) TextWhite else DarkCardElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (isYearActive) TextWhite else BorderSubtle,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    if (isYearActive) {
                                        selectedYear = null
                                    } else {
                                        isYearMenuOpen = true
                                    }
                                }
                                .padding(horizontal = 13.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isYearActive) "Year: $selectedYear ✕" else "Year ▾",
                                fontSize = 12.sp,
                                fontWeight = if (isYearActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isYearActive) PureBlack else TextSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = isYearMenuOpen,
                            onDismissRequest = { isYearMenuOpen = false },
                            modifier = Modifier.background(DarkCardElevated)
                        ) {
                            yearOptions.forEach { y ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = y,
                                            fontSize = 13.sp,
                                            color = if (y == "All" && selectedYear == null || y == selectedYear) TextWhite else TextSecondary
                                        )
                                    },
                                    onClick = {
                                        selectedYear = if (y == "All") null else y
                                        isYearMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Country / Language Filter Pill & Dropdown
                item {
                    val isLangActive = selectedLanguage != null
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isLangActive) TextWhite else DarkCardElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (isLangActive) TextWhite else BorderSubtle,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    if (isLangActive) {
                                        selectedLanguage = null
                                    } else {
                                        isLanguageMenuOpen = true
                                    }
                                }
                                .padding(horizontal = 13.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isLangActive) "Lang: ${selectedLanguage?.uppercase()} ✕" else "Lang ▾",
                                fontSize = 12.sp,
                                fontWeight = if (isLangActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isLangActive) PureBlack else TextSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = isLanguageMenuOpen,
                            onDismissRequest = { isLanguageMenuOpen = false },
                            modifier = Modifier.background(DarkCardElevated)
                        ) {
                            languageOptions.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            color = if (code == selectedLanguage) TextWhite else TextSecondary
                                        )
                                    },
                                    onClick = {
                                        selectedLanguage = code
                                        isLanguageMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TextWhite)
                }
            } else if (searchResults.isEmpty() && searchQuery.length >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results for \"$searchQuery\"",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Search to add items to ${list?.name ?: "list"}",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { result ->
                        val alreadyInList = existingItems.any { existing ->
                            val urlMatches = (result.externalUrl.isNotEmpty() && existing.externalUrl.isNotEmpty() && existing.externalUrl == result.externalUrl) ||
                                    (result.coverUrl.isNotEmpty() && existing.coverUrl.isNotEmpty() && existing.coverUrl == result.coverUrl)
                            val titleMatches = existing.title.trim().equals(result.title.trim(), ignoreCase = true)
                            val resYearDigits = result.releaseYear.filter { it.isDigit() }.take(4)
                            val existYearDigits = existing.releaseYear.filter { it.isDigit() }.take(4)
                            val yearMatches = resYearDigits.isNotEmpty() && existYearDigits.isNotEmpty() && resYearDigits == existYearDigits
                            val creatorMatches = result.creator.isNotBlank() && existing.creator.isNotBlank() && existing.creator.equals(result.creator, ignoreCase = true)
                            val bothDetailsEmpty = result.releaseYear.isBlank() && existing.releaseYear.isBlank() && result.creator.isBlank() && existing.creator.isBlank()

                            urlMatches || (titleMatches && (yearMatches || creatorMatches || bothDetailsEmpty))
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        repository.addItemFromSearch(listId, result)
                                        snackbarHostState.showSnackbar("Added '${result.title}'")
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = DarkCardElevated),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (result.coverUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(result.coverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = result.title,
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 72.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(PureBlack),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 72.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(PureBlack),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CategoryIcon(
                                            category = category,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = result.creator.ifEmpty { result.releaseYear }
                                    if (subtitle.isNotEmpty()) {
                                        Text(
                                            text = subtitle,
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (result.overview.isNotEmpty()) {
                                        Text(
                                            text = result.overview,
                                            fontSize = 11.sp,
                                            color = TextGrayMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (alreadyInList) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Added",
                                            fontSize = 11.sp,
                                            color = TextGrayMedium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_add),
                                            contentDescription = "Add again",
                                            tint = TextGrayMedium,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_add),
                                        contentDescription = "Add",
                                        tint = TextWhite,
                                        modifier = Modifier.size(20.dp)
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
