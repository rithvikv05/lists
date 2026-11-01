package com.lists.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lists.app.data.model.Category
import com.lists.app.data.model.RatingScaleOption
import com.lists.app.data.store.WebStore
import com.lists.app.ui.theme.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onConnectDrive: () -> Unit,
    onSyncNow: () -> Unit
) {
    val userEmail by WebStore.userEmail.collectAsState()
    val isSyncing by WebStore.syncing.collectAsState()
    val ratingPrefs by WebStore.ratingPrefs.collectAsState()

    var showRestoreDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        },
        containerColor = PureBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Google Drive Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Google Drive Sync",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    if (userEmail != null) {
                        Text(
                            text = "Connected as: $userEmail",
                            fontSize = 13.sp,
                            color = TextGrayLight
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onSyncNow,
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TextWhite,
                                    contentColor = PureBlack
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isSyncing) "Syncing..." else "Sync Now")
                            }

                            TextButton(
                                onClick = { WebStore.setAuth(null) }
                            ) {
                                Text("Disconnect", color = Color(0xFFFF453A))
                            }
                        }
                    } else {
                        Text(
                            text = "Sign in to backup and seamlessly sync your lists across mobile and web with zero data loss.",
                            fontSize = 13.sp,
                            color = TextGrayMedium,
                            lineHeight = 18.sp
                        )

                        Button(
                            onClick = onConnectDrive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextWhite,
                                contentColor = PureBlack
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Connect Google Drive", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Rating Scales Section
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Rating Scale Preferences",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    Category.entries.forEach { cat ->
                        val currentScale = WebStore.getRatingScale(cat)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${cat.displayName} Scale",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextGrayLight
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RatingScaleOption.entries.forEach { opt ->
                                    val isSelected = currentScale == opt
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) AccentWhite else DarkCardElevated)
                                            .clickable { WebStore.setRatingScale(cat, opt) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = opt.shortName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) PureBlack else TextWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Data Export / Import Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Backup & Restore",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val jsonStr = WebStore.exportBackupJson()
                                jsDownloadFile("listy_backup.json", jsonStr)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkCardElevated,
                                contentColor = TextWhite
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Export JSON")
                        }

                        Button(
                            onClick = { showRestoreDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkCardElevated,
                                contentColor = TextWhite
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Restore JSON")
                        }
                    }
                }
            }
        }
    }

    if (showRestoreDialog) {
        var restoreText by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Backup", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Paste contents of listy_backup.json below:",
                        color = TextGrayMedium,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = restoreText,
                        onValueChange = { restoreText = it },
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = TextWhite,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = Color(0xFFFF453A), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (WebStore.restoreBackupJson(restoreText)) {
                            showRestoreDialog = false
                        } else {
                            errorMessage = "Invalid JSON format. Please verify file."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = PureBlack)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = TextGrayMedium)
                }
            },
            containerColor = DarkCardBg
        )
    }
}

private fun jsDownloadFile(filename: String, content: String): Unit = js("""{
    var blob = new Blob([content], {type: 'application/json'});
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
}""")
