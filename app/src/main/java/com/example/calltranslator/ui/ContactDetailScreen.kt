package com.example.calltranslator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calltranslator.LanguageConfig
import com.example.calltranslator.LanguageOption
import com.example.calltranslator.model.Contact
import com.example.calltranslator.webrtc.SignalingClient
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contact: Contact,
    initialHearingLang: LanguageOption = LanguageConfig.getEnglish(),
    onNavigateBack: () -> Unit,
    onStartAiCall: (Contact, LanguageOption) -> Unit
) {
    val context = LocalContext.current
    val signalingClient = remember { SignalingClient() }
    val auth = FirebaseAuth.getInstance()
    var isRegistered by remember { mutableStateOf<Boolean?>(null) }
    var isBlocked by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    var myHearingLang by remember { mutableStateOf(initialHearingLang) }

    LaunchedEffect(contact.phoneNumber) {
        val uid = auth.currentUser?.uid ?: ""
        signalingClient.checkUserRegistered(contact.phoneNumber) { registered ->
            isRegistered = registered
        }
        if (uid.isNotEmpty()) {
            signalingClient.checkContactBlocked(uid, contact.phoneNumber) { blocked ->
                isBlocked = blocked
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Call") },
                            onClick = {
                                showMenu = false
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                context.startActivity(intent)
                            },
                            leadingIcon = { Icon(Icons.Default.Call, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("AI Call") },
                            onClick = {
                                showMenu = false
                                if (!isBlocked) {
                                    onStartAiCall(contact, myHearingLang)
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Language, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isBlocked) "Unblock Contact" else "Block Contact") },
                            onClick = {
                                showMenu = false
                                val uid = auth.currentUser?.uid ?: ""
                                if (uid.isNotEmpty()) {
                                    if (isBlocked) {
                                        signalingClient.unblockContact(uid, contact.phoneNumber) { success ->
                                            if (success) isBlocked = false
                                        }
                                    } else {
                                        signalingClient.blockContact(uid, contact.phoneNumber) { success ->
                                            if (success) isBlocked = true
                                        }
                                    }
                                }
                            },
                            leadingIcon = { Icon(if (isBlocked) Icons.Default.Check else Icons.Default.Block, null) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = contact.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(text = contact.phoneNumber, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            
            if (isBlocked) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Blocked Contact", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Language I want to hear Person in:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LanguageSelector(
                        label = "", 
                        selected = myHearingLang, 
                        list = LanguageConfig.supportedLanguages
                    ) { myHearingLang = it }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Normal Call")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onStartAiCall(contact, myHearingLang) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = isRegistered == true && !isBlocked
            ) {
                Icon(Icons.Default.Language, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isBlocked) "Contact Blocked" else "AI Translate Call")
            }

            if (isRegistered == false) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "This person needs to install and register in AI Call Translator.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (isRegistered == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking AI registration...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
        }
    }
}
