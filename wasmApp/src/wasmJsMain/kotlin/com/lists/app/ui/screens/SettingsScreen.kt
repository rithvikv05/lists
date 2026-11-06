package com.lists.app.ui.screens

import android.content.Context
import android.widget.Toast
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
import kotlinx.coroutines.launch
import com.lists.app.currentTimeMillis
import com.lists.app.data.model.Category
import com.lists.app.data.model.HomeLayoutMode
import com.lists.app.data.model.RatingScaleOption
import com.lists.app.data.repository.AppRepository
import com.lists.app.data.store.WebStore
import com.lists.app.data.sync.DriveSyncManager
import com.lists.app.data.sync.GoogleDriveSync
import com.lists.app.data.sync.GoogleSignInAccount
import com.lists.app.data.sync.GoogleSignInHelper
import com.lists.app.getFileReaderResult
import com.lists.app.jsDownloadFile
import com.lists.app.jsDownloadPackage
import com.lists.app.jsPickAndReadPackage
import com.lists.app.statusBarsPadding
import com.lists.app.triggerAsmrTick
import com.lists.app.triggerStrongTick
import com.lists.app.ui.theme.AppAccentColor
import com.lists.app.ui.theme.AppThemeMode

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

    val ratingPrefs = remember { context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE) }

    val signInHelper = remember { GoogleSignInHelper(context) }
    val syncManager = remember { DriveSyncManager(context) }
    val userEmail by WebStore.userEmail.collectAsState()
    val signedInAccount = userEmail?.let { GoogleSignInAccount(it, it) }
    var autoSyncEnabled by remember { mutableStateOf(syncManager.autoSyncEnabled) }
    var isSyncing by remember { mutableStateOf(false) }
    var snapshots by remember { mutableStateOf(WebStore.getAvailableBackups()) }

    val refreshSnapshots: suspend () -> Unit = {
        val local = WebStore.getAvailableBackups()
        val drive = if (signedInAccount != null) GoogleDriveSync.listDriveRollingBackups() else emptyList<WebStore.BackupInfo>()
        snapshots = local + drive
    }

    LaunchedEffect(signedInAccount) {
        refreshSnapshots()
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWideLayout = screenWidthDp >= 600

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

                // SECTION 1: APPEARANCE
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
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 0.dp,
                    border = null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Line 1: Theme
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
                                                contentDescription = "Light",
                                                tint = if (activeThemeMode == AppThemeMode.LIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

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
                                                contentDescription = "Dark",
                                                tint = if (activeThemeMode == AppThemeMode.DARK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

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
                                                contentDescription = "System",
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

                        // Line 2: Home Layout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Home Layout",
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
                                    val isDiscrete = homeLayoutMode == HomeLayoutMode.DISCRETE
                                    Surface(
                                        onClick = {
                                            triggerAsmrTick(context)
                                            homeLayoutMode = HomeLayoutMode.DISCRETE
                                            uiPrefs.edit().putString("home_layout_mode", HomeLayoutMode.DISCRETE.name).apply()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isDiscrete) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ViewAgenda,
                                                contentDescription = "Discrete List",
                                                tint = if (isDiscrete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

                                    val isBento = homeLayoutMode == HomeLayoutMode.BENTO
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

                        // Line 3: Accent
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

                // SECTION 2: SYNC & BACKUP
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
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 0.dp,
                    border = null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                                refreshSnapshots()
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
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LinkOff,
                                            contentDescription = "Disconnect",
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        signInHelper.requestSignIn { success ->
                                            if (success) {
                                                coroutineScope.launch {
                                                    syncManager.syncNow(signInHelper.getSignedInAccount(), repository)
                                                    refreshSnapshots()
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Connect", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Export & Import Actions
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val json = repository.exportDataToJson()
                                        downloadJson("lists-backup-${currentTimeMillis()}.json", json)
                                        Toast.makeText(context, "Exported JSON", Toast.LENGTH_SHORT).show()
                                    },
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
                                    onClick = {
                                        pickAndReadJsonFile { json ->
                                            coroutineScope.launch {
                                                val success = repository.importDataFromJson(json)
                                                if (success) {
                                                    Toast.makeText(context, "Imported JSON", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val json = repository.exportDataToJson()
                                        jsDownloadPackage("lists-archive-${currentTimeMillis()}.listspkg", json)
                                        Toast.makeText(context, "Exported .lists package", Toast.LENGTH_SHORT).show()
                                    },
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
                                    onClick = {
                                        jsPickAndReadPackage(
                                            onSuccess = { json ->
                                                coroutineScope.launch {
                                                    val success = repository.importDataFromJson(json)
                                                    if (success) {
                                                        Toast.makeText(context, "Imported .lists package", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
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

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                thickness = 0.5.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Local & Drive Rolling Backups
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Rolling Backups",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    onClick = {
                                        triggerStrongTick(context)
                                        WebStore.saveAutoBackup()
                                        coroutineScope.launch {
                                            if (signedInAccount != null) {
                                                GoogleDriveSync.createAndUploadRollingBackup()
                                            }
                                            refreshSnapshots()
                                        }
                                        Toast.makeText(context, "Snapshot saved", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = "Take Snapshot",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            if (snapshots.isEmpty()) {
                                Text(
                                    text = "1 rolling backup is stored locally and 2 rolling backups are secured on Google Drive.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    snapshots.forEach { snap ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                    Text(
                                                        text = snap.label,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = snap.summary,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        triggerStrongTick(context)
                                                        coroutineScope.launch {
                                                            val success = if (snap.isDrive && snap.driveFileId != null) {
                                                                GoogleDriveSync.restoreDriveRollingBackup(snap.driveFileId)
                                                            } else {
                                                                WebStore.restoreFromBackup(snap.key)
                                                            }
                                                            if (success) {
                                                                Toast.makeText(context, "Restored snapshot", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, "Restore failed", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text("Restore", style = MaterialTheme.typography.labelSmall)
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

                // SECTION 3: NOTIFICATIONS & REMINDERS
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
                            modifier = Modifier.fillMaxWidth(),
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

                            var fallbackHour by remember {
                                mutableStateOf(kotlinx.browser.localStorage.getItem("fallback_reminder_hour")?.toIntOrNull() ?: 9)
                            }
                            val ampm = if (fallbackHour < 12) "AM" else "PM"
                            val displayHour = if (fallbackHour == 0) 12 else if (fallbackHour > 12) fallbackHour - 12 else fallbackHour
                            val formattedTime = "${if (displayHour < 10) "0" else ""}$displayHour:00 $ampm"

                            Surface(
                                onClick = {
                                    triggerAsmrTick(context)
                                    val nextHour = (fallbackHour + 1) % 24
                                    fallbackHour = nextHour
                                    kotlinx.browser.localStorage.setItem("fallback_reminder_hour", nextHour.toString())
                                },
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

                // SECTION 4: ABOUT
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
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 0.dp,
                    border = null,
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
                            text = "Lists Web • Material 3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
