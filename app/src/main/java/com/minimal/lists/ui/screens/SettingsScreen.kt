package com.minimal.lists.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import com.minimal.lists.data.model.HomeLayoutMode
import com.minimal.lists.data.repository.AppRepository
import com.minimal.lists.data.storage.BackupSnapshot
import com.minimal.lists.data.storage.OfflineStorageManager
import com.minimal.lists.data.storage.RollingBackupManager
import com.minimal.lists.data.sync.DriveSyncManager
import com.minimal.lists.data.sync.GoogleSignInHelper
import com.minimal.lists.data.sync.SyncWorker
import com.minimal.lists.ui.components.triggerAsmrTick
import com.minimal.lists.ui.components.triggerStrongTick
import com.minimal.lists.ui.theme.AppAccentColor
import com.minimal.lists.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: AppRepository,
    darkTheme: Boolean = true,
    themeMode: AppThemeMode = AppThemeMode.AUTO,
    onThemeChange: (Boolean) -> Unit = {},
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val uiPrefs = remember { context.getSharedPreferences("lists_ui_prefs", Context.MODE_PRIVATE) }
    var homeLayoutMode by remember {
        val saved = uiPrefs.getString("home_layout_mode", HomeLayoutMode.DISCRETE.name)
        mutableStateOf(
            try { HomeLayoutMode.valueOf(saved ?: HomeLayoutMode.DISCRETE.name) }
            catch (_: Exception) { HomeLayoutMode.DISCRETE }
        )
    }

    val themePrefs = remember { context.getSharedPreferences("lists_theme_prefs", Context.MODE_PRIVATE) }
    var activeThemeMode by remember(themeMode) {
        val saved = themePrefs.getString("app_theme_mode", themeMode.id)
        mutableStateOf(AppThemeMode.fromId(saved))
    }
    var activeAccentId by remember {
        mutableStateOf(themePrefs.getString("app_accent_color", AppAccentColor.LAVENDER.id) ?: AppAccentColor.LAVENDER.id)
    }
    var isAccentPaletteExpanded by remember { mutableStateOf(false) }

    val offlineStorageManager = remember { OfflineStorageManager.getInstance(context) }
    var includeCoversInBackup by remember {
        val prefs = context.getSharedPreferences("lists_storage_prefs", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean("backup_include_offline_covers", true))
    }

    val rollingBackupManager = remember { RollingBackupManager(context) }
    var snapshots by remember { mutableStateOf<List<BackupSnapshot>>(emptyList()) }
    var snapshotToRestore by remember { mutableStateOf<BackupSnapshot?>(null) }
    var driveSnapshots by remember { mutableStateOf<List<DriveSyncManager.DriveBackupInfo>>(emptyList()) }
    var driveSnapshotToRestore by remember { mutableStateOf<DriveSyncManager.DriveBackupInfo?>(null) }

    val signInHelper = remember { GoogleSignInHelper(context) }
    val syncManager = remember { DriveSyncManager(context) }
    var signedInAccount by remember { mutableStateOf(signInHelper.getSignedInAccount()) }
    var autoSyncEnabled by remember { mutableStateOf(syncManager.autoSyncEnabled) }
    var isSyncing by remember { mutableStateOf(false) }

    val settingsPrefs = remember { context.getSharedPreferences("lists_settings_prefs", Context.MODE_PRIVATE) }
    var fallbackHour by remember { mutableStateOf(settingsPrefs.getInt("fallback_reminder_hour", 9)) }
    var fallbackMinute by remember { mutableStateOf(settingsPrefs.getInt("fallback_reminder_minute", 0)) }

    // Load snapshots on entry and trigger auto-snapshot
    LaunchedEffect(signedInAccount) {
        rollingBackupManager.captureSnapshot(repository)
        snapshots = rollingBackupManager.listSnapshots()
        if (signedInAccount != null) {
            driveSnapshots = syncManager.listDriveRollingBackups(signedInAccount!!)
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            if (account != null) {
                if (autoSyncEnabled) SyncWorker.schedulePeriodicSync(context, syncManager.wifiOnlySync)
                coroutineScope.launch {
                    isSyncing = true
                    val syncRes = syncManager.syncNow(account, repository)
                    isSyncing = false
                    if (syncRes is DriveSyncManager.SyncResult.Success) {
                        Toast.makeText(context, "Synced with Google Drive", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = repository.exportDataToJson()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    Toast.makeText(context, "Exported JSON successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                    val success = repository.importDataFromJson(json)
                    if (success) {
                        rollingBackupManager.captureSnapshot(repository)
                        snapshots = rollingBackupManager.listSnapshots()
                        Toast.makeText(context, "Imported JSON successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid JSON format", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val exportListsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val success = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        offlineStorageManager.exportPackage(outputStream, repository, includeCoversInBackup)
                    } ?: false
                    if (success) {
                        Toast.makeText(context, "Exported .lists package successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importListsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val success = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        offlineStorageManager.importPackage(inputStream, repository)
                    } ?: false
                    if (success) {
                        rollingBackupManager.captureSnapshot(repository)
                        snapshots = rollingBackupManager.listSnapshots()
                        Toast.makeText(context, "Restored .lists package successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid .lists package", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = if (isWideLayout) 24.dp else 16.dp)
            ) {
                // Top Header Row matching ListsScreen & ListDetailScreen position and midline alignment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp, bottom = 8.dp)
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
                                text = "Settings",
                                style = MaterialTheme.typography.headlineMedium,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                // =========================================================================
                // SECTION 1: APPEARANCE (Exactly 3 Lines, no icon in heading)
                // =========================================================================
                Text(
                    text = "APPEARANCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Line 1: Theme (Light / Dark / Auto 3-mode segmented slider)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Theme",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Light
                                    Surface(
                                        onClick = {
                                            triggerAsmrTick(context)
                                            activeThemeMode = AppThemeMode.LIGHT
                                            themePrefs.edit().putString("app_theme_mode", AppThemeMode.LIGHT.id).apply()
                                            onThemeModeChange(AppThemeMode.LIGHT)
                                            onThemeChange(false)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (activeThemeMode == AppThemeMode.LIGHT) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.LightMode,
                                                contentDescription = "Light Mode",
                                                tint = if (activeThemeMode == AppThemeMode.LIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

                                    // Dark
                                    Surface(
                                        onClick = {
                                            triggerAsmrTick(context)
                                            activeThemeMode = AppThemeMode.DARK
                                            themePrefs.edit().putString("app_theme_mode", AppThemeMode.DARK.id).apply()
                                            onThemeModeChange(AppThemeMode.DARK)
                                            onThemeChange(true)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (activeThemeMode == AppThemeMode.DARK) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.DarkMode,
                                                contentDescription = "Dark Mode",
                                                tint = if (activeThemeMode == AppThemeMode.DARK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

                                    // Auto (System)
                                    Surface(
                                        onClick = {
                                            triggerAsmrTick(context)
                                            activeThemeMode = AppThemeMode.AUTO
                                            themePrefs.edit().putString("app_theme_mode", AppThemeMode.AUTO.id).apply()
                                            onThemeModeChange(AppThemeMode.AUTO)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (activeThemeMode == AppThemeMode.AUTO) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.BrightnessAuto,
                                                contentDescription = "Auto Mode",
                                                tint = if (activeThemeMode == AppThemeMode.AUTO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Line 2: Layout (List / Bento 2-mode segmented slider)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Layout",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val isBento = homeLayoutMode == HomeLayoutMode.BENTO
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = {
                                            triggerAsmrTick(context)
                                            homeLayoutMode = HomeLayoutMode.DISCRETE
                                            uiPrefs.edit().putString("home_layout_mode", HomeLayoutMode.DISCRETE.name).apply()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (!isBento) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ViewAgenda,
                                                contentDescription = "List Layout",
                                                tint = if (!isBento) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            triggerAsmrTick(context)
                                            homeLayoutMode = HomeLayoutMode.BENTO
                                            uiPrefs.edit().putString("home_layout_mode", HomeLayoutMode.BENTO.name).apply()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isBento) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.GridView,
                                                contentDescription = "Bento Layout",
                                                tint = if (isBento) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Line 3: Accent (Accent name on left, interactive color dot on right)
                        val activeAccent = AppAccentColor.fromId(activeAccentId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Accent",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val activeDotColor = if (activeAccent == AppAccentColor.MONOCHROME) {
                                if (darkTheme) Color.White else Color.Black
                            } else {
                                activeAccent.previewColor
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(activeDotColor)
                                    .border(
                                        2.dp,
                                        if (isAccentPaletteExpanded) MaterialTheme.colorScheme.onSurface else if (activeAccent == AppAccentColor.MONOCHROME && !darkTheme) Color.Black.copy(alpha = 0.25f) else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable {
                                        triggerAsmrTick(context)
                                        isAccentPaletteExpanded = !isAccentPaletteExpanded
                                    }
                            )
                        }

                        // Expanded Accent Color Palette (Clean horizontal row of dots, no names/descriptions)
                        AnimatedVisibility(
                            visible = isAccentPaletteExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppAccentColor.entries.forEach { accent ->
                                        val isSelected = accent.id == activeAccentId
                                        val dotColor = if (accent == AppAccentColor.MONOCHROME) {
                                            if (darkTheme) Color.White else Color.Black
                                        } else {
                                            accent.previewColor
                                        }
                                        val checkTint = if (accent == AppAccentColor.MONOCHROME) {
                                            if (darkTheme) Color.Black else Color.White
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                                .border(
                                                    2.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.onSurface else if (accent == AppAccentColor.MONOCHROME && !darkTheme) Color.Black.copy(alpha = 0.25f) else Color.Transparent,
                                                    CircleShape
                                                )
                                                .clickable {
                                                    triggerStrongTick(context)
                                                    activeAccentId = accent.id
                                                    themePrefs.edit().putString("app_accent_color", accent.id).apply()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = checkTint,
                                                    modifier = Modifier.size(16.dp)
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

                // =========================================================================
                // SECTION 2: SYNC & BACKUP (Combined Section, no icon in heading)
                // =========================================================================
                Text(
                    text = "SYNC & BACKUP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 1. Google Drive Cloud Sync
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Google Drive",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (signedInAccount != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = signedInAccount?.email ?: "Not connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (signedInAccount != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = {
                                            if (isSyncing) return@Button
                                            val account = signedInAccount ?: return@Button
                                            coroutineScope.launch {
                                                isSyncing = true
                                                syncManager.syncNow(account, repository)
                                                rollingBackupManager.captureSnapshot(repository)
                                                snapshots = rollingBackupManager.listSnapshots()
                                                driveSnapshots = syncManager.listDriveRollingBackups(account)
                                                isSyncing = false
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        if (isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.2.dp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        } else {
                                            Text("Sync", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                signInHelper.signOut()
                                                signedInAccount = null
                                                SyncWorker.cancelPeriodicSync(context)
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.LinkOff,
                                            contentDescription = "Disconnect",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { signInLauncher.launch(signInHelper.getSignInIntent()) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Sign In", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (signedInAccount != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Auto-sync in background",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = autoSyncEnabled,
                                    onCheckedChange = { enabled ->
                                        autoSyncEnabled = enabled
                                        syncManager.autoSyncEnabled = enabled
                                        if (enabled) SyncWorker.schedulePeriodicSync(context, syncManager.wifiOnlySync)
                                        else SyncWorker.cancelPeriodicSync(context)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. Rolling Backups (1 Local · 2 Google Drive)
                        Text(
                            text = "Rolling Backups",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 1 Local Snapshot
                            snapshots.forEach { snapshot ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = "Local Backup (1/1)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${snapshot.formattedDate} • ${snapshot.itemCount} items • ${snapshot.formattedSize}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Button(
                                            onClick = { snapshotToRestore = snapshot },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Restore", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // 2 Google Drive Backups
                            driveSnapshots.forEach { driveSnap ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = driveSnap.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${driveSnap.formattedDate} • ${driveSnap.formattedSize}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Button(
                                            onClick = { driveSnapshotToRestore = driveSnap },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Restore", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Offline Covers Option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Save Cover Images in Backups",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Includes offline WebP images inside .lists archive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = includeCoversInBackup,
                                onCheckedChange = { enabled ->
                                    includeCoversInBackup = enabled
                                    val prefs = context.getSharedPreferences("lists_storage_prefs", Context.MODE_PRIVATE)
                                    prefs.edit().putBoolean("backup_include_offline_covers", enabled).apply()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // 4. Export & Import Actions
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Row 1: JSON
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { exportJsonLauncher.launch("lists-backup-${System.currentTimeMillis()}.json") },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export JSON", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                                }

                                OutlinedButton(
                                    onClick = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import JSON", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                                }
                            }

                            // Row 2: .lists compressed package (identical minimal OutlinedButton styling)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { exportListsLauncher.launch("lists-archive-${System.currentTimeMillis()}.lists") },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export .lists", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                                }

                                OutlinedButton(
                                    onClick = { importListsLauncher.launch(arrayOf("*/*")) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import .lists", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // =========================================================================
                // SECTION 3: NOTIFICATIONS & REMINDERS
                // =========================================================================
                Text(
                    text = "NOTIFICATIONS & REMINDERS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    triggerAsmrTick(context)
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            fallbackHour = h
                                            fallbackMinute = m
                                            settingsPrefs.edit()
                                                .putInt("fallback_reminder_hour", h)
                                                .putInt("fallback_reminder_minute", m)
                                                .apply()
                                        },
                                        fallbackHour,
                                        fallbackMinute,
                                        false
                                    ).show()
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Fallback Reminder Time",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Alert time when exact episode airtime is unavailable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val ampm = if (fallbackHour < 12) "AM" else "PM"
                            val displayHour = if (fallbackHour == 0) 12 else if (fallbackHour > 12) fallbackHour - 12 else fallbackHour
                            val formattedTime = String.format(java.util.Locale.ENGLISH, "%02d:%02d %s", displayHour, fallbackMinute, ampm)

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Text(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // =========================================================================
                // SECTION 4: ABOUT (No icon in heading)
                // =========================================================================
                Text(
                    text = "ABOUT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Lists",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "com.minimal.lists • v2.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    // Restore Snapshot Confirmation Dialog
    snapshotToRestore?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { snapshotToRestore = null },
            title = {
                Text(
                    text = "Restore Snapshot?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Restore state from ${snapshot.formattedDate} (${snapshot.itemCount} items)? This will replace your current library state.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val success = rollingBackupManager.restoreSnapshot(snapshot, repository)
                            snapshotToRestore = null
                            if (success) {
                                Toast.makeText(context, "Restored snapshot successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to restore snapshot", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { snapshotToRestore = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Restore Drive Snapshot Confirmation Dialog
    driveSnapshotToRestore?.let { driveSnap ->
        AlertDialog(
            onDismissRequest = { driveSnapshotToRestore = null },
            title = {
                Text(
                    text = "Restore Drive Backup?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Restore state from ${driveSnap.name} (${driveSnap.formattedDate})? This will replace your current library state.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val account = signedInAccount
                        if (account != null) {
                            coroutineScope.launch {
                                val success = syncManager.restoreDriveRollingBackup(account, driveSnap.fileId, repository)
                                driveSnapshotToRestore = null
                                if (success) {
                                    Toast.makeText(context, "Restored Drive backup successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to restore Drive backup", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            driveSnapshotToRestore = null
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { driveSnapshotToRestore = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
