package com.example.calltranslator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calltranslator.model.CallStateHolder
import com.example.calltranslator.repository.TranslationHistoryRepository
import com.example.calltranslator.viewmodel.AppTheme
import com.example.calltranslator.viewmodel.CallState
import com.example.calltranslator.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val currentTheme by viewModel.appTheme.collectAsState()

    val themes = listOf(
        Triple(AppTheme.LIGHT, "Light Theme", "White/light background, dark text"),
        Triple(AppTheme.DARK, "Dark Theme", "Dark background, white text"),
        Triple(AppTheme.PURPLE, "Purple Theme", "Deep purple background & highlights"),
        Triple(AppTheme.MIDNIGHT, "Midnight Theme", "Very dark navy/black background"),
        Triple(AppTheme.AMOLED, "AMOLED Theme", "Pure black background (#000000)")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Selection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(themes) { (themeEnum, name, description) ->
                val selected = currentTheme == themeEnum
                Card(
                    onClick = { viewModel.setAppTheme(themeEnum) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = when(themeEnum) {
                                AppTheme.LIGHT -> Color(0xFFF8F9FA)
                                AppTheme.DARK -> Color(0xFF1E1E1E)
                                AppTheme.PURPLE -> Color(0xFF2D1B3E)
                                AppTheme.MIDNIGHT -> Color(0xFF0F122B)
                                AppTheme.AMOLED -> Color.Black
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {}
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        RadioButton(
                            selected = selected,
                            onClick = { viewModel.setAppTheme(themeEnum) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneSelectionScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val currentRingtone by viewModel.ringtone.collectAsState()
    val ringtones = listOf("Default Ringtone", "Classic Bell", "Modern Synth", "Gentle Chime")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ringtone") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(ringtones) { ringtone ->
                ListItem(
                    headlineContent = { Text(ringtone, color = MaterialTheme.colorScheme.onSurface) },
                    trailingContent = {
                        if (ringtone == currentRingtone) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.setRingtone(ringtone) }
                )
                Divider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val privateMode by viewModel.privateMode.collectAsState()
    val scope = rememberCoroutineScope()
    val historyRepo = remember { TranslationHistoryRepository() }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearedMessage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Private Translation Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("Do not save transcripts or history when ON.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                Switch(
                    checked = privateMode,
                    onCheckedChange = { viewModel.setPrivateMode(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Privacy Statement",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Audio is not recorded or stored. All translations are processed securely and transiently in real-time.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Translation History")
            }

            if (clearedMessage) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Translation history cleared successfully.", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Translation History") },
            text = { Text("Are you sure you want to delete all translation history? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        try {
                            historyRepo.clearAllHistory()
                            clearedMessage = true
                        } catch (_: Exception) {}
                    }
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptionSettingsScreen(onNavigateBack: () -> Unit) {
    val callState by CallStateHolder.callState.collectAsState()

    val statusText = when (callState) {
        CallState.CONNECTED -> "Protected"
        CallState.CALLING, CallState.CONNECTING, CallState.RECONNECTING -> "Connecting"
        CallState.FAILED, CallState.DISCONNECTED -> "Unavailable"
        else -> "Ready"
    }

    val description = when (callState) {
        CallState.CONNECTED -> "WebRTC DTLS/SRTP is protecting call media."
        CallState.CALLING, CallState.CONNECTING, CallState.RECONNECTING -> "Establishing secure WebRTC DTLS/SRTP handshake..."
        CallState.FAILED, CallState.DISCONNECTED -> "Secure transport could not be established."
        else -> "WebRTC DTLS/SRTP secure media transport will be established automatically when you start a call."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encryption") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Text(
                text = "Encryption Status: $statusText",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when(statusText) {
                    "Protected" -> Color(0xFF4CAF50)
                    "Connecting" -> Color(0xFFFF9800)
                    "Unavailable" -> Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "WebRTC DTLS/SRTP secure media transport is used to protect audio media during the call.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendUrlScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val currentUrl by viewModel.backendUrl.collectAsState()
    var urlInput by remember { mutableStateOf(currentUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backend API URL") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Text("Enter backend URL. Local: http://10.0.2.2:8000/ — Cloud: https://YOUR-BACKEND-NAME.onrender.com/", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    viewModel.setBackendUrl(urlInput)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(options) { option ->
                ListItem(
                    headlineContent = { Text(option, color = MaterialTheme.colorScheme.onSurface) },
                    trailingContent = {
                        if (option == selectedOption) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { onOptionSelected(option) }
                )
                Divider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(title: String, info: String, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Text(text = info, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
