package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.ui.theme.*
import com.example.update.AppUpdateManager
import com.example.update.UpdateUiState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun UpdateDialog(
    updateManager: AppUpdateManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state by updateManager.updateState.collectAsState()

    var hasInstallPermission by remember {
        mutableStateOf(updateManager.canInstallApks())
    }

    LaunchedEffect(state) {
        if (state is UpdateUiState.ReadyToInstall) {
            hasInstallPermission = updateManager.canInstallApks()
        }
    }

    Dialog(
        onDismissRequest = {
            if (state !is UpdateUiState.Downloading) {
                updateManager.resetState()
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is UpdateUiState.Downloading,
            dismissOnClickOutside = state !is UpdateUiState.Downloading
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = FootySurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, FootyAccentGreen.copy(alpha = 0.35f)),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val currentState = state) {
                    is UpdateUiState.Checking -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(FootyAccentGreenDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = FootyAccentGreen,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Checking for Updates...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FootyTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Contacting update server for latest release notes",
                            style = MaterialTheme.typography.bodySmall.copy(color = FootyTextSecondary),
                            textAlign = TextAlign.Center
                        )
                    }

                    is UpdateUiState.UpToDate -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(FootyAccentGreenDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Up to date",
                                tint = FootyAccentGreen,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "You're on the Latest Version!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FootyTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) is currently installed.",
                            style = MaterialTheme.typography.bodySmall.copy(color = FootyTextSecondary),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                updateManager.resetState()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FootyAccentGreenDark,
                                contentColor = FootyAccentGreen
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Awesome", fontWeight = FontWeight.Bold)
                        }
                    }

                    is UpdateUiState.UpdateAvailable -> {
                        val info = currentState.info
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(FootyStatusYellowBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Update available",
                                tint = FootyStatusYellow,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "New Update Available!",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = FootyTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = FootyTextSecondary)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = FootyAccentGreen,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(16.dp)
                            )
                            Surface(
                                color = FootyAccentGreenDark,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FootyAccentGreenBorder)
                            ) {
                                Text(
                                    text = "v${info.versionName} (Build ${info.versionCode})",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = FootyAccentGreen,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (info.changelog.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "What's New:",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = FootyTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .background(FootyPitchBlack, RoundedCornerShape(12.dp))
                                    .border(1.dp, FootySurfaceBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = info.changelog,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FootyTextPrimary,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    updateManager.resetState()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FootySurfaceBorder)
                            ) {
                                Text("Later", color = FootyTextSecondary)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        updateManager.downloadUpdate(info)
                                    }
                                },
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FootyAccentGreenDark,
                                    contentColor = FootyAccentGreen
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateUiState.Downloading -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(FootyAccentGreenDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Downloading",
                                tint = FootyAccentGreen,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Downloading Update...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FootyTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val percentage = (currentState.progress * 100).toInt()
                        LinearProgressIndicator(
                            progress = { currentState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = FootyAccentGreen,
                            trackColor = FootyPitchBlack
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val downloadedMB = currentState.bytesDownloaded / (1024f * 1024f)
                        val totalMB = currentState.totalBytes / (1024f * 1024f)
                        val progressText = if (currentState.totalBytes > 0) {
                            String.format(Locale.getDefault(), "%.1f MB / %.1f MB (%d%%)", downloadedMB, totalMB, percentage)
                        } else {
                            String.format(Locale.getDefault(), "%.1f MB downloaded", downloadedMB)
                        }

                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodySmall.copy(color = FootyTextSecondary)
                        )
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(FootyAccentGreenDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.InstallMobile,
                                contentDescription = "Ready to Install",
                                tint = FootyAccentGreen,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Update Ready to Install!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FootyTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "The latest APK was downloaded successfully.",
                            style = MaterialTheme.typography.bodySmall.copy(color = FootyTextSecondary),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!hasInstallPermission) {
                            Surface(
                                color = FootyStatusYellowBg,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FootyStatusYellow.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = FootyStatusYellow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Android permission required to allow install from this app.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = FootyTextPrimary,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    updateManager.openInstallPermissionSettings()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FootyStatusYellow,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Permission in Settings", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    updateManager.installApk(currentState.apkFile)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FootyAccentGreenDark,
                                    contentColor = FootyAccentGreen
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install Update Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(FootyStatusRedBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = FootyStatusRed,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Update Check Failed",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FootyTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodySmall.copy(color = FootyTextSecondary),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    updateManager.resetState()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FootySurfaceBorder)
                            ) {
                                Text("Close", color = FootyTextSecondary)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        updateManager.checkForUpdates()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FootyAccentGreenDark,
                                    contentColor = FootyAccentGreen
                                )
                            ) {
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateUiState.Idle -> {
                        // Not shown
                    }
                }
            }
        }
    }
}
