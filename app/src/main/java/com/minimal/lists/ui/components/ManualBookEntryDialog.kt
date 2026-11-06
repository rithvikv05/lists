package com.minimal.lists.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBookEntryDialog(
    initialTitle: String = "",
    initialIsbn: String = "",
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        author: String,
        isbn: String,
        totalPages: Int,
        language: String,
        publisher: String,
        publishDate: String,
        coverUrl: String
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf("") }
    var isbn by remember(initialIsbn) { mutableStateOf(initialIsbn) }
    var totalPagesText by remember { mutableStateOf("") }
    var selectedLanguageCode by remember { mutableStateOf("en") }
    var publisher by remember { mutableStateOf("") }
    var publishDate by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }

    var isLangMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val languages = remember {
        listOf(
            "en" to "English (EN)",
            "ja" to "Japanese (JA)",
            "es" to "Spanish (ES)",
            "fr" to "French (FR)",
            "de" to "German (DE)",
            "zh" to "Chinese (ZH)",
            "ko" to "Korean (KO)",
            "hi" to "Hindi (HI)",
            "it" to "Italian (IT)",
            "pt" to "Portuguese (PT)",
            "ru" to "Russian (RU)",
            "other" to "Other"
        )
    }

    val selectedLangLabel = languages.firstOrNull { it.first == selectedLanguageCode }?.second ?: selectedLanguageCode

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Text(
                    text = "Manual Book Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Add custom book details directly",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cover preview if URL provided
                    if (coverUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = "Cover Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // 1. Title (Compulsory)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; showError = false },
                        label = { Text("Title *") },
                        isError = showError && title.isBlank(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Author (Optional - not compulsory)
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author / Creator (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3. ISBN on separate line
                    OutlinedTextField(
                        value = isbn,
                        onValueChange = { isbn = it },
                        label = { Text("ISBN (10 or 13)") },
                        placeholder = { Text("e.g. 9780141187761") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 4. Total Pages and Language on ONE line (Language is an identical OutlinedTextField dropdown)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = totalPagesText,
                            onValueChange = { totalPagesText = it.filter { c -> c.isDigit() } },
                            label = { Text("Total Pages") },
                            placeholder = { Text("e.g. 350") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        // Language Dropdown - rendered as a native OutlinedTextField so styling matches 100%
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = selectedLangLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Language") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select language",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Transparent overlay to open dropdown menu on click
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isLangMenuOpen = true }
                            )

                            DropdownMenu(
                                expanded = isLangMenuOpen,
                                onDismissRequest = { isLangMenuOpen = false }
                            ) {
                                languages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            selectedLanguageCode = code
                                            isLangMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 5. Publisher on ONE line with Date Select field on same line opening DatePicker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = publisher,
                            onValueChange = { publisher = it },
                            label = { Text("Publisher") },
                            placeholder = { Text("e.g. Penguin") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        // Date - formatted as an identical OutlinedTextField with calendar icon
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = publishDate,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Date") },
                                placeholder = { Text("Select date") },
                                singleLine = true,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Pick Date",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Transparent overlay to open date picker on click
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showDatePicker = true }
                            )
                        }
                    }

                    // 6. Cover URL (Optional)
                    OutlinedTextField(
                        value = coverUrl,
                        onValueChange = { coverUrl = it },
                        label = { Text("Cover Image URL (optional)") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showError) {
                        Text(
                            text = "Please enter a Title.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                showError = true
                            } else {
                                triggerStrongTick(context)
                                val pages = totalPagesText.toIntOrNull() ?: 0
                                onSave(
                                    title.trim(),
                                    author.trim(),
                                    isbn.trim(),
                                    pages,
                                    selectedLanguageCode.trim(),
                                    publisher.trim(),
                                    publishDate.trim(),
                                    coverUrl.trim()
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Book")
                    }
                }
            }
        }
    }

    // Material 3 Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            publishDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
