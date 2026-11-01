package com.lists.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.lists.app.currentTimeMillis
import com.lists.app.data.model.Category
import com.lists.app.data.model.RatingScaleOption
import com.lists.app.data.repository.AppRepository
import com.lists.app.data.sync.DriveSyncManager
import com.lists.app.data.sync.GoogleSignInAccount
import com.lists.app.data.sync.GoogleSignInHelper
import com.lists.app.data.sync.SyncWorker
import com.lists.app.encodeURIComponent
import com.lists.app.jsDownloadFile
import com.lists.app.jsDownloadPackage
import com.lists.app.jsPickAndReadPackage
import com.lists.app.getFileReaderResult
import com.lists.app.ui.theme.*

private fun downloadJson(filename: String, content: String) {
    try {
        jsDownloadFile(filename, content)
    } catch (e: Throwable) {
        println("Export error: ${e.message}")
    }
}

private fun pickAndReadJsonFile(onRead: (String) -> Unit) {
    try {
        val input = kotlinx.browser.document.createElement("input") as org.w3c.dom.HTMLInputElement
        input.type = "file"
        input.accept = ".json"
        input.onchange = {
            val file = input.files?.item(0)
            if (file != null) {
                val reader = org.w3c.files.FileReader()
                reader.onload = {
                    val text = getFileReaderResult(reader)
                    if (text.isNotEmpty()) onRead(text)
                }
                reader.readAsText(file)
            }
        }
        input.click()
    } catch (e: Throwable) {
        println("Import error: ${e.message}")
    }
}


@Composable
fun SettingsScreen(
    repository: AppRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // Rating Systems SharedPreferences
    val ratingPrefs = remember { context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE) }

    // Google Drive Sync state
    val signInHelper = remember { GoogleSignInHelper(context) }
    val syncManager = remember { DriveSyncManager(context) }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(signInHelper.getSignedInAccount()) }
    var lastSyncTimestamp by remember { mutableLongStateOf(syncManager.lastSyncTimestamp) }
    var autoSyncEnabled by remember { mutableStateOf(syncManager.autoSyncEnabled) }
    var wifiOnlySync by remember { mutableStateOf(syncManager.wifiOnlySync) }
    var isSyncing by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showAdvancedSync by remember { mutableStateOf(false) }

    val handleExportPackage = {
        try {
            val json = repository.exportDataToJson()
            jsDownloadPackage("lists_backup.listspkg", json)
            statusMessage = "Exported .listspkg successfully!"
            Toast.makeText(context, "Exported .listspkg", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusMessage = "Failed to export package: ${e.message}"
        }
    }

    val handleImportPackage = {
        jsPickAndReadPackage(
            onSuccess = { json ->
                coroutineScope.launch {
                    val success = repository.importDataFromJson(json)
                    if (success) {
                        statusMessage = "Imported .listspkg successfully!"
                        Toast.makeText(context, "Imported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        statusMessage = "Invalid backup format in package"
                    }
                }
            },
            onError = { err ->
                statusMessage = err
            }
        )
    }

    val handleExport = {
        try {
            val json = repository.exportDataToJson()
            downloadJson("lists_backup.json", json)
            statusMessage = "Backup exported successfully!"
            Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusMessage = "Failed to export: ${e.message}"
        }
    }

    val handleImport = {
        pickAndReadJsonFile { json ->
            coroutineScope.launch {
                val success = repository.importDataFromJson(json)
                if (success) {
                    statusMessage = "Backup imported successfully!"
                    Toast.makeText(context, "Imported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    statusMessage = "Invalid backup format"
                }
            }
        }
    }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite
                    )
                }
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Status banner if present
            if (statusMessage != null) {
                Surface(
                    color = DarkCardElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = TextWhite,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { statusMessage = null }) {
                            Text("Dismiss", color = TextGrayMedium, fontSize = 12.sp)
                        }
                    }
                }
            }

            // SECTION 1: Rating Systems
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RATING SYSTEMS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextGrayMedium,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Choose the rating scale displayed on covers and detail pages for each category.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = DarkCardBg,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        CategoryRatingItem(
                            category = Category.MOVIE,
                            prefs = ratingPrefs
                        )
                        HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                        CategoryRatingItem(
                            category = Category.TV_SHOW,
                            prefs = ratingPrefs
                        )
                        HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                        CategoryRatingItem(
                            category = Category.BOOK,
                            prefs = ratingPrefs
                        )
                        HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                        CategoryRatingItem(
                            category = Category.GAME,
                            prefs = ratingPrefs
                        )
                    }
                }
            }

            // SECTION 2: Google Drive Cloud Sync
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "GOOGLE DRIVE SYNC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextGrayMedium,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Seamlessly back up and synchronize all lists and items across your Android devices and Web.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = DarkCardBg,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Connection row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (signedInAccount != null) Color(0xFF34C759) else TextGrayMedium,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (signedInAccount != null) "Connected" else "Google Drive",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                )
                                Text(
                                    text = signedInAccount?.email ?: "Not connected",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            if (signedInAccount == null) {
                                Button(
                                    onClick = {
                                        signInHelper.requestSignIn { success ->
                                            signedInAccount = signInHelper.getSignedInAccount()
                                            if (success) {
                                                coroutineScope.launch {
                                                    isSyncing = true
                                                    val syncRes = syncManager.syncNow(signedInAccount, repository)
                                                    isSyncing = false
                                                    lastSyncTimestamp = syncManager.lastSyncTimestamp
                                                    statusMessage = "Connected & Synced with Google Drive"
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TextWhite,
                                        contentColor = PureBlack
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            signInHelper.signOut()
                                            signedInAccount = null
                                            statusMessage = "Disconnected from Google Drive"
                                        }
                                    }
                                ) {
                                    Text("Disconnect", color = Color(0xFFFF453A), fontSize = 12.sp)
                                }
                            }
                        }

                        if (signedInAccount != null) {
                            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

                            // Last sync row + Sync Now button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Last synced",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = formatLastSync(lastSyncTimestamp),
                                        fontSize = 13.sp,
                                        color = TextWhite,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Button(
                                    onClick = {
                                        val acct = signedInAccount ?: return@Button
                                        coroutineScope.launch {
                                            isSyncing = true
                                            val res = syncManager.syncNow(acct, repository)
                                            isSyncing = false
                                            lastSyncTimestamp = syncManager.lastSyncTimestamp
                                            statusMessage = if (res is DriveSyncManager.SyncResult.Success) {
                                                "Synced with Google Drive"
                                            } else {
                                                "Sync failed"
                                            }
                                        }
                                    },
                                    enabled = !isSyncing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DarkCardElevated,
                                        contentColor = TextWhite,
                                        disabledContainerColor = DarkCardElevated.copy(alpha = 0.5f),
                                        disabledContentColor = TextGrayMedium
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = TextWhite,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Syncing...", fontSize = 12.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = TextWhite
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

                            // Advanced sync options
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAdvancedSync = !showAdvancedSync },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Advanced Cloud Options",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = if (showAdvancedSync) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = TextGrayMedium,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (showAdvancedSync) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val acct = signedInAccount ?: return@OutlinedButton
                                                coroutineScope.launch {
                                                    isSyncing = true
                                                    val res = syncManager.forceUpload(acct, repository)
                                                    isSyncing = false
                                                    lastSyncTimestamp = syncManager.lastSyncTimestamp
                                                    statusMessage = "Uploaded to Drive"
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(42.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                                        ) {
                                            Text("Force Upload", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        OutlinedButton(
                                            onClick = { showRestoreDialog = true },
                                            modifier = Modifier.weight(1f).height(42.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF453A))
                                        ) {
                                            Text("Restore Cloud", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Restore Dialog
            if (showRestoreDialog) {
                AlertDialog(
                    onDismissRequest = { showRestoreDialog = false },
                    containerColor = DarkCardElevated,
                    title = {
                        Text(
                            text = "Restore from Google Drive?",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    text = {
                        Text(
                            text = "This will replace your local database with the snapshot currently stored on Google Drive. Proceed?",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showRestoreDialog = false
                                val acct = signedInAccount ?: return@Button
                                coroutineScope.launch {
                                    isSyncing = true
                                    val res = syncManager.forceDownload(acct, repository)
                                    isSyncing = false
                                    lastSyncTimestamp = syncManager.lastSyncTimestamp
                                    statusMessage = if (res is DriveSyncManager.SyncResult.Success) {
                                        "Restored successfully from Google Drive!"
                                    } else {
                                        "Restore failed"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF453A),
                                contentColor = TextWhite
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Restore", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestoreDialog = false }) {
                            Text("Cancel", color = TextGrayMedium, fontSize = 12.sp)
                        }
                    }
                )
            }

            // SECTION 3: Manual JSON Backup & Restore
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "OFFLINE BACKUP & RESTORE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextGrayMedium,
                    letterSpacing = 1.sp
                )

                Surface(
                    color = DarkCardBg,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Row 1: Binary Archive (.listspkg)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = handleExportPackage,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkCardElevated,
                                    contentColor = TextWhite
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Export .listspkg",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = handleImportPackage,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkCardElevated,
                                    contentColor = TextWhite
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Import .listspkg",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Row 2: Universal JSON Backup
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = handleExport,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkCardElevated,
                                    contentColor = TextWhite
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Export JSON",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = handleImport,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkCardElevated,
                                    contentColor = TextWhite
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Import JSON",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CategoryRatingItem(
    category: Category,
    prefs: android.content.SharedPreferences
) {
    val defaultScale = RatingScaleOption.getDefault(category)
    val savedId = prefs.getString("rating_scale_${category.id}", defaultScale.id)
    var selectedOption by remember(savedId) {
        mutableStateOf(RatingScaleOption.fromId(savedId, defaultScale))
    }
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextWhite
            )
            Text(
                text = selectedOption.displayName,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Box {
            TextButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = selectedOption.shortName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentWhite
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkCardElevated)
            ) {
                RatingScaleOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.displayName,
                                fontSize = 13.sp,
                                color = if (option == selectedOption) AccentWhite else TextWhite,
                                fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            selectedOption = option
                            prefs.edit().putString("rating_scale_${category.id}", option.id).apply()
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun formatLastSync(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val diff = currentTimeMillis() - timestamp
    val mins = diff / 60000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        mins < 1L -> "Just now"
        mins < 60L -> "${mins}m ago"
        hours < 24L -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
