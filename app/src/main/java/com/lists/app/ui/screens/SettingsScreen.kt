package com.lists.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import com.lists.app.data.model.Category
import com.lists.app.data.model.RatingScaleOption
import com.lists.app.data.repository.AppRepository
import com.lists.app.data.sync.DriveSyncManager
import com.lists.app.data.sync.GoogleSignInHelper
import com.lists.app.data.sync.SyncWorker
import com.lists.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var signedInAccount by remember { mutableStateOf(signInHelper.getSignedInAccount()) }
    var lastSyncTimestamp by remember { mutableLongStateOf(syncManager.lastSyncTimestamp) }
    var autoSyncEnabled by remember { mutableStateOf(syncManager.autoSyncEnabled) }
    var wifiOnlySync by remember { mutableStateOf(syncManager.wifiOnlySync) }
    var isSyncing by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showAdvancedSync by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            if (account != null) {
                if (autoSyncEnabled) {
                    SyncWorker.schedulePeriodicSync(context, wifiOnlySync)
                }
                coroutineScope.launch {
                    isSyncing = true
                    val syncRes = syncManager.syncNow(account, repository)
                    isSyncing = false
                    lastSyncTimestamp = syncManager.lastSyncTimestamp
                    when (syncRes) {
                        is DriveSyncManager.SyncResult.Success -> {
                            statusMessage = "Connected & Synced with Google Drive"
                            Toast.makeText(context, "Connected & Synced with Google Drive", Toast.LENGTH_SHORT).show()
                        }
                        is DriveSyncManager.SyncResult.Error -> {
                            statusMessage = "Initial sync failed: ${syncRes.message}"
                            Toast.makeText(context, "Initial sync failed: ${syncRes.message}", Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            statusMessage = "Google Sign-In failed: ${e.message}"
            Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // SAF File Pickers for Export & Import
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = repository.exportDataToJson()
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray())
                    }
                    statusMessage = "Backup exported successfully!"
                    Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    statusMessage = "Failed to export: ${e.message}"
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    } ?: ""
                    val success = repository.importDataFromJson(json)
                    if (success) {
                        statusMessage = "Backup imported successfully!"
                        Toast.makeText(context, "Imported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        statusMessage = "Invalid backup format"
                    }
                } catch (e: Exception) {
                    statusMessage = "Failed to import: ${e.message}"
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
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }
        }
    ) { padding ->
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val horizontalPadding = if (screenWidthDp >= 840) {
            ((screenWidthDp - 740) / 2).coerceAtLeast(20).dp
        } else {
            20.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = 16.dp)
        ) {
            // RATING SYSTEMS SECTION
            Text(
                text = "RATING SYSTEMS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Categories in clean single-line rows with dropdowns
            Category.entries.forEach { cat ->
                var currentScale by remember {
                    val defaultScale = RatingScaleOption.getDefault(cat)
                    val saved = ratingPrefs.getString("rating_scale_${cat.id}", defaultScale.id)
                    mutableStateOf(RatingScaleOption.fromId(saved, defaultScale))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cat.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextWhite
                    )

                    // Single-line compact dropdown selector
                    Box {
                        var dropdownExpanded by remember { mutableStateOf(false) }

                        Surface(
                            color = DarkCardElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkBorder),
                            modifier = Modifier.clickable { dropdownExpanded = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = currentScale.shortName,
                                    fontSize = 12.sp,
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .background(DarkCardElevated)
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        ) {
                            RatingScaleOption.entries.forEach { option ->
                                val isSelected = currentScale == option
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.displayName,
                                            color = if (isSelected) TextWhite else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        currentScale = option
                                        ratingPrefs.edit().putString("rating_scale_${cat.id}", option.id).apply()
                                        triggerAsmrTick(context)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(24.dp))

            // GOOGLE DRIVE SYNC SECTION
            Text(
                text = "GOOGLE DRIVE SYNC",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Sync Card
            Surface(
                color = DarkCardElevated,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (signedInAccount == null) {
                        // Signed out state
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Drive",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                )
                                Text(
                                    text = "Not connected",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                signInLauncher.launch(signInHelper.getSignInIntent())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextWhite,
                                contentColor = PureBlack
                            )
                        ) {
                            Text(
                                text = "Sign In with Google",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Signed in state
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF34C759))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = signedInAccount?.email ?: "Connected",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextWhite,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Last synced: ${formatLastSync(lastSyncTimestamp)}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        signInHelper.signOut()
                                        signedInAccount = null
                                        SyncWorker.cancelPeriodicSync(context)
                                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Sign Out",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Sync Now button
                        Button(
                            onClick = {
                                val acct = signedInAccount ?: return@Button
                                coroutineScope.launch {
                                    isSyncing = true
                                    val res = syncManager.syncNow(acct, repository)
                                    isSyncing = false
                                    lastSyncTimestamp = syncManager.lastSyncTimestamp
                                    when (res) {
                                        is DriveSyncManager.SyncResult.Success -> {
                                            statusMessage = "Synced successfully with Google Drive!"
                                            Toast.makeText(context, "Sync complete", Toast.LENGTH_SHORT).show()
                                        }
                                        is DriveSyncManager.SyncResult.Error -> {
                                            statusMessage = "Sync failed: ${res.message}"
                                            Toast.makeText(context, "Sync failed: ${res.message}", Toast.LENGTH_LONG).show()
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            enabled = !isSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextWhite,
                                contentColor = PureBlack,
                                disabledContainerColor = DarkBorder,
                                disabledContentColor = TextSecondary
                            )
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = PureBlack
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Now", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Auto-sync Toggle Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-sync",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextWhite
                                )
                                Text(
                                    text = "Sync on changes and every 6 hours",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = autoSyncEnabled,
                                onCheckedChange = { checked ->
                                    autoSyncEnabled = checked
                                    syncManager.autoSyncEnabled = checked
                                    if (checked) {
                                        SyncWorker.schedulePeriodicSync(context, wifiOnlySync)
                                    } else {
                                        SyncWorker.cancelPeriodicSync(context)
                                    }
                                    triggerAsmrTick(context)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PureBlack,
                                    checkedTrackColor = TextWhite,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkCardElevated
                                )
                            )
                        }

                        // Wi-Fi only Toggle Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sync on Wi-Fi only",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextWhite
                                )
                                Text(
                                    text = "Avoid using mobile cellular data",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = wifiOnlySync,
                                onCheckedChange = { checked ->
                                    wifiOnlySync = checked
                                    syncManager.wifiOnlySync = checked
                                    if (autoSyncEnabled) {
                                        SyncWorker.schedulePeriodicSync(context, checked)
                                    }
                                    triggerAsmrTick(context)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PureBlack,
                                    checkedTrackColor = TextWhite,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkCardElevated
                                )
                            )
                        }

                        // Advanced Options Accordion
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAdvancedSync = !showAdvancedSync }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Advanced Drive Options",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = if (showAdvancedSync) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (showAdvancedSync) {
                            Spacer(modifier = Modifier.height(8.dp))
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
                                            when (res) {
                                                is DriveSyncManager.SyncResult.Success -> {
                                                    Toast.makeText(context, "Uploaded to Drive", Toast.LENGTH_SHORT).show()
                                                }
                                                is DriveSyncManager.SyncResult.Error -> {
                                                    Toast.makeText(context, "Upload failed: ${res.message}", Toast.LENGTH_LONG).show()
                                                }
                                                else -> {}
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                                ) {
                                    Text("Force Upload", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        showRestoreDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
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
                                    when (res) {
                                        is DriveSyncManager.SyncResult.Success -> {
                                            statusMessage = "Restored successfully from Google Drive!"
                                            Toast.makeText(context, "Restored from Drive", Toast.LENGTH_SHORT).show()
                                        }
                                        is DriveSyncManager.SyncResult.Error -> {
                                            statusMessage = "Restore failed: ${res.message}"
                                            Toast.makeText(context, "Restore failed: ${res.message}", Toast.LENGTH_LONG).show()
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF453A),
                                contentColor = TextWhite
                            )
                        ) {
                            Text("Restore", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestoreDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(24.dp))

            // DATA BACKUP SECTION
            Text(
                text = "DATA BACKUP",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Two simple buttons: Export and Import
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        exportLauncher.launch("lists_backup.json")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PureBlack)
                ) {
                    Text("Export", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/*"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCardElevated, contentColor = TextWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text("Import", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkCardElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(text = msg, fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

private fun formatLastSync(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3600_000L -> "${(diff / 60_000L).coerceAtLeast(1)}m ago"
        diff < 86400_000L -> "${(diff / 3600_000L).coerceAtLeast(1)}h ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
