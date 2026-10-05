package com.example.calltranslator.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calltranslator.LanguageConfig
import com.example.calltranslator.LanguageOption
import com.example.calltranslator.R
import com.example.calltranslator.SpeechRecognitionManager
import com.example.calltranslator.TextToSpeechManager
import com.example.calltranslator.util.ContactUtils
import com.example.calltranslator.viewmodel.AiMode
import com.example.calltranslator.viewmodel.CallState
import com.example.calltranslator.viewmodel.CallViewModel
import java.util.UUID

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    speechManager: SpeechRecognitionManager,
    ttsManager: TextToSpeechManager,
    remoteName: String = "Remote User",
    viewModel: CallViewModel = viewModel(),
    onCallEnded: () -> Unit,
    onStartCallWithNumber: ((String, String) -> Unit)? = null,
    onNavigateToContacts: (() -> Unit)? = null,
    onNavigateToRecents: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val callState by viewModel.callState.collectAsState()
    val roomId by viewModel.roomId.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()
    val remoteTranslation by viewModel.remoteTranslation.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val myHearingLang by viewModel.myHearingLanguage.collectAsState()
    val remoteHearingLang by viewModel.remoteHearingLanguageName.collectAsState()
    val failureMessage by viewModel.failureMessage.collectAsState()
    val isLocalSpeaking by viewModel.isLocalSpeaking.collectAsState()
    val isRemoteSpeaking by viewModel.isRemoteSpeaking.collectAsState()
    val translationError by viewModel.translationError.collectAsState()
    val contextForToast = LocalContext.current

    // User-facing translation errors (Req 15)
    LaunchedEffect(translationError) {
        if (!translationError.isNullOrBlank()) {
            android.widget.Toast.makeText(contextForToast, translationError, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearTranslationError()
        }
    }
    
    val isListening by speechManager.isListening.collectAsState()
    val isSpeaking by ttsManager.isSpeaking.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()

    var inputRoomId by remember { mutableStateOf("") }
    var isMicMuted by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = callState != CallState.ENDED && callState != CallState.IDLE) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("End Call") },
            text = { Text("Do you want to end the active call?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.endCall()
                }) {
                    Text("End Call", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Outgoing Call Ringback Tone
    if (callState == CallState.CALLING || callState == CallState.CONNECTING) {
        val context = LocalContext.current
        DisposableEffect(callState) {
            var mediaPlayer: MediaPlayer? = null
            try {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, ringtoneUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("Ringback", "MediaPlayer failed: ${e.message}")
            }

            onDispose {
                try {
                    mediaPlayer?.let {
                        if (it.isPlaying) it.stop()
                        it.release()
                    }
                } catch (e: Exception) {
                    Log.e("Ringback", "MediaPlayer release failed: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(remoteTranslation, myHearingLang) {
        if (remoteTranslation.isNotEmpty()) {
            Log.d("TTS", "speaking remote translation in ${myHearingLang.code}")
            ttsManager.speak(remoteTranslation, myHearingLang)
            viewModel.clearRemoteTranslation()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initTranslation(speechManager, ttsManager)
    }

    // STT <-> TTS handoff (Req 10): pause STT while TTS speaks to prevent
    // the translated voice being recognized again; resume on completion.
    // myHearingLang is a key so STT restarts with the correct locale (Req 3).
    LaunchedEffect(isSpeaking, callState, aiMode, myHearingLang) {
        if (callState == CallState.CONNECTED && aiMode == AiMode.TRANSLATION) {
            if (isSpeaking) {
                Log.d("STT", "paused - TTS speaking")
                speechManager.stopListening()
            } else {
                Log.d("STT", "resumed with locale=${myHearingLang.speechLocale}")
                speechManager.startListening(myHearingLang)
            }
        } else {
            speechManager.stopListening()
        }
    }

    LaunchedEffect(callState) {
        if (callState == CallState.ENDED) {
            onCallEnded()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val glowColor = MaterialTheme.colorScheme.primary
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Background Glow
            Canvas(modifier = Modifier.fillMaxSize().blur(100.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.15f), Color.Transparent)
                    ),
                    radius = size.minDimension * 0.8f,
                    center = center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (callState == CallState.IDLE) {
                    CallSetup(
                        roomId = inputRoomId,
                        onRoomIdChange = { inputRoomId = it },
                        onJoinCall = { 
                            if (inputRoomId.isNotEmpty()) {
                                viewModel.joinCall(inputRoomId)
                            } else {
                                viewModel.startCall(UUID.randomUUID().toString().substring(0, 6).uppercase())
                            }
                        },
                        onStartCallWithNumber = onStartCallWithNumber
                    )
                } else {
                    CallActive(
                        callState = callState,
                        roomId = roomId,
                        aiMode = aiMode,
                        isListening = isListening,
                        isTranslating = isTranslating,
                        isSpeaking = isSpeaking,
                        isMicMuted = isMicMuted,
                        isSpeakerOn = isSpeakerOn,
                        myHearingLang = myHearingLang,
                        remoteHearingLang = remoteHearingLang,
                        isLocalSpeaking = isLocalSpeaking,
                        isRemoteSpeaking = isRemoteSpeaking,
                        onHearingLangChange = { viewModel.setMyHearingLanguage(it) },
                        remoteName = remoteName,
                        failureMessage = failureMessage,
                        callDuration = callDuration,
                        onToggleMic = { 
                            isMicMuted = !isMicMuted
                            viewModel.toggleMic(!isMicMuted)
                        },
                        onToggleSpeaker = { viewModel.toggleSpeaker() },
                        onToggleAiMode = { 
                            viewModel.toggleAiMode(if (aiMode == AiMode.NORMAL) AiMode.TRANSLATION else AiMode.NORMAL) 
                        },
                        onEndCall = { showExitDialog = true }
                    )
                }
            }
        }
    }
}

@Composable
fun CallSetup(
    roomId: String,
    onRoomIdChange: (String) -> Unit,
    onJoinCall: () -> Unit,
    onStartCallWithNumber: ((String, String) -> Unit)? = null
) {
    var dialInput by remember { mutableStateOf(roomId) }
    val context = LocalContext.current
    val contacts = remember { ContactUtils.fetchContacts(context) }
    val matchingContact = contacts.find {
        dialInput.isNotEmpty() && (it.phoneNumber == dialInput || it.phoneNumber.replace(Regex("[^0-9]"), "") == dialInput.replace(Regex("[^0-9]"), ""))
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo_foreground),
            contentDescription = null,
            modifier = Modifier.size(70.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Dial or Enter Room ID", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = dialInput,
            onValueChange = { 
                dialInput = it
                onRoomIdChange(it)
            },
            placeholder = { Text("Enter number or Room ID", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            shape = RoundedCornerShape(16.dp)
        )

        if (matchingContact != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "👤 ${matchingContact.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (dialInput.isNotEmpty()) {
                    if (onStartCallWithNumber != null && (dialInput.length > 6 || matchingContact != null)) {
                        onStartCallWithNumber(dialInput, matchingContact?.name ?: dialInput)
                    } else {
                        onJoinCall()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            shape = RoundedCornerShape(16.dp),
            enabled = dialInput.isNotEmpty()
        ) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("CALL", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallActive(
    callState: CallState,
    roomId: String,
    aiMode: AiMode,
    isListening: Boolean,
    isTranslating: Boolean,
    isSpeaking: Boolean,
    isMicMuted: Boolean,
    isSpeakerOn: Boolean,
    myHearingLang: LanguageOption,
    remoteHearingLang: String,
    isLocalSpeaking: Boolean,
    isRemoteSpeaking: Boolean,
    onHearingLangChange: (LanguageOption) -> Unit,
    remoteName: String,
    failureMessage: String,
    callDuration: String,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleAiMode: () -> Unit,
    onEndCall: () -> Unit
) {
    var showKeypad by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var dtmfInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top area: Status & Caller Info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            val statusText = when (callState) {
                CallState.CONNECTED -> "Connected • $callDuration"
                CallState.CALLING -> "Calling..."
                CallState.CONNECTING -> "Ringing..."
                CallState.RECONNECTING -> "Reconnecting..."
                CallState.FAILED -> failureMessage
                else -> "Connecting..."
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (callState == CallState.CONNECTED) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar Box
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                if (callState == CallState.CONNECTED && aiMode == AiMode.TRANSLATION && (isLocalSpeaking || isRemoteSpeaking || isSpeaking || isListening)) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_anim"
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp * pulseScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    )
                }

                Surface(
                    modifier = Modifier.size(90.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = remoteName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = remoteName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (roomId.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Room: $roomId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            // AI Translation Sub-status
            if (aiMode == AiMode.TRANSLATION) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isListening -> Color(0xFF4CAF50)
                                        isTranslating -> Color(0xFFFF9800)
                                        isSpeaking -> Color(0xFF2196F3)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                        )
                        Text(
                            text = when {
                                isListening -> "LISTENING (${myHearingLang.displayName})"
                                isTranslating -> "TRANSLATING..."
                                isSpeaking -> "SPEAKING ($remoteHearingLang)"
                                else -> "READY (${myHearingLang.displayName} ⇄ $remoteHearingLang)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Bottom area: Controls & End Call
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 4 Circular Controls: Keypad, Mute, Speaker, More
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularControlButton(
                    icon = Icons.Default.Dialpad,
                    label = "Keypad",
                    onClick = { showKeypad = true }
                )

                CircularControlButton(
                    icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMicMuted) "Muted" else "Mute",
                    active = isMicMuted,
                    activeColor = MaterialTheme.colorScheme.error,
                    onClick = onToggleMic
                )
                
                CircularControlButton(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    label = if (isSpeakerOn) "Speaker" else "Earpiece",
                    active = isSpeakerOn,
                    onClick = onToggleSpeaker
                )

                CircularControlButton(
                    icon = Icons.Default.MoreVert,
                    label = "More",
                    active = aiMode == AiMode.TRANSLATION,
                    activeColor = MaterialTheme.colorScheme.secondary,
                    onClick = { showMoreMenu = true }
                )
            }

            // Large End Call Button
            Surface(
                onClick = onEndCall,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }

    // In-Call Keypad Dialog
    if (showKeypad) {
        AlertDialog(
            onDismissRequest = { showKeypad = false },
            title = { Text("Keypad", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dtmfInput.ifEmpty { "Enter digits" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("*", "0", "#")
                    )
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { digit ->
                                Surface(
                                    onClick = { dtmfInput += digit },
                                    modifier = Modifier.size(50.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = digit, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dtmfInput = ""; showKeypad = false }) {
                    Text("Close")
                }
            }
        )
    }

    // More Menu Bottom Sheet (AI Translation Settings & Language Swap)
    if (showMoreMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("AI Call Translation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Enable AI Translation", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("Real-time speech-to-speech translation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = aiMode == AiMode.TRANSLATION,
                        onCheckedChange = { onToggleAiMode() }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                Text("Languages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Hear In Language:", style = MaterialTheme.typography.bodyMedium)
                    LanguageSelector(
                        label = "",
                        selected = myHearingLang,
                        list = LanguageConfig.supportedLanguages,
                        onSelected = onHearingLangChange
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun CircularControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = if (active) activeColor else containerColor,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) Color.White else contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    }
}
