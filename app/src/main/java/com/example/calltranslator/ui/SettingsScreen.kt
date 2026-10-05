package com.example.calltranslator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calltranslator.viewmodel.AppTheme
import com.example.calltranslator.viewmodel.SettingsViewModel

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPhone: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToRecents: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToHearInLanguage: () -> Unit,
    onNavigateToAiModel: () -> Unit,
    onNavigateToSpeed: () -> Unit,
    onNavigateToAudioQuality: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToFontSize: (() -> Unit)? = null,
    onNavigateToRingtone: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToEncryption: () -> Unit,
    onNavigateToBackendUrl: () -> Unit,
    onLogout: () -> Unit
) {
    val appTheme by viewModel.appTheme.collectAsState()
    val ringtone by viewModel.ringtone.collectAsState()
    val targetLang by viewModel.targetLanguage.collectAsState()
    val aiModel by viewModel.aiModel.collectAsState()
    val speed by viewModel.translationSpeed.collectAsState()
    val backendUrl by viewModel.backendUrl.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val vibration by viewModel.vibration.collectAsState()
    val incomingNotif by viewModel.incomingNotif.collectAsState()
    val missedNotif by viewModel.missedNotif.collectAsState()
    val statusNotif by viewModel.statusNotif.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "settings",
                onNavigateToPhone = onNavigateToPhone,
                onNavigateToContacts = onNavigateToContacts,
                onNavigateToRecents = onNavigateToRecents,
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToSettings = {}
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "Appearance") {
                SettingsItem(
                    icon = when(appTheme) {
                        AppTheme.DARK -> Icons.Default.DarkMode
                        AppTheme.LIGHT -> Icons.Default.LightMode
                        AppTheme.PURPLE -> Icons.Default.Palette
                        AppTheme.MIDNIGHT -> Icons.Default.Nightlight
                        AppTheme.AMOLED -> Icons.Default.Contrast
                    },
                    label = "Theme: ${appTheme.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    onClick = onNavigateToTheme
                )
            }

            SettingsSection(title = "AI Translation") {
                SettingsItem(icon = Icons.Default.Language, label = "My \"Hear In\" Language: $targetLang", onClick = onNavigateToHearInLanguage)
                SettingsItem(icon = Icons.Default.AutoAwesome, label = "AI Translation Model: $aiModel", onClick = onNavigateToAiModel)
                SettingsItem(icon = Icons.Default.Speed, label = "Real-time Speed: $speed", onClick = onNavigateToSpeed)
                SettingsItem(icon = Icons.Default.Cloud, label = "Backend API URL: $backendUrl", onClick = onNavigateToBackendUrl)
            }

            SettingsSection(title = "Call settings") {
                SettingsItem(icon = Icons.Default.VolumeUp, label = "Audio Quality: $audioQuality", onClick = onNavigateToAudioQuality)
                SettingsItem(icon = Icons.Default.RingVolume, label = "Ringtone: $ringtone", onClick = onNavigateToRingtone)
                SettingsSwitchItem(
                    icon = Icons.Default.Vibration,
                    label = "Vibration",
                    checked = vibration,
                    onCheckedChange = { viewModel.setVibration(it) }
                )
                SettingsItem(icon = Icons.Default.Security, label = "Encryption", onClick = onNavigateToEncryption)
            }

            SettingsSection(title = "Privacy & History") {
                SettingsItem(icon = Icons.Default.Lock, label = "Privacy & Data", onClick = onNavigateToPrivacy)
                SettingsItem(icon = Icons.Default.History, label = "View Call History", onClick = onNavigateToRecents)
            }

            SettingsSection(title = "Notifications") {
                SettingsSwitchItem(
                    icon = Icons.Default.PhoneCallback,
                    label = "Incoming Call Notifications",
                    checked = incomingNotif,
                    onCheckedChange = { viewModel.setIncomingNotif(it) }
                )
                SettingsSwitchItem(
                    icon = Icons.Default.PhoneMissed,
                    label = "Missed Call Notifications",
                    checked = missedNotif,
                    onCheckedChange = { viewModel.setMissedNotif(it) }
                )
                SettingsSwitchItem(
                    icon = Icons.Default.Notifications,
                    label = "Call Status Notifications",
                    checked = statusNotif,
                    onCheckedChange = { viewModel.setStatusNotif(it) }
                )
            }

            SettingsSection(title = "Account") {
                SettingsItem(
                    icon = Icons.Default.ExitToApp,
                    label = "Logout",
                    onClick = { showLogoutDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out of AI Call Translator?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
        Divider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    }
}

@Composable
fun SettingsItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun SettingsSwitchItem(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
