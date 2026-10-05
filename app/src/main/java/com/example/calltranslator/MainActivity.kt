package com.example.calltranslator

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.calltranslator.model.*
import com.example.calltranslator.ui.*
import com.example.calltranslator.viewmodel.*
import com.example.calltranslator.webrtc.SignalingClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.calltranslator.ui.theme.CallTranslatorTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var speechManager: SpeechRecognitionManager
    private lateinit var ttsManager: TextToSpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        speechManager = SpeechRecognitionManager(this)
        ttsManager = TextToSpeechManager(this)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val appTheme by settingsViewModel.appTheme.collectAsState()
            val fontSize by settingsViewModel.fontSize.collectAsState()

            val typography = when (fontSize) {
                FontSize.SMALL -> Typography(
                    bodyLarge = TextStyle(fontSize = 14.sp),
                    headlineMedium = TextStyle(fontSize = 22.sp),
                    titleMedium = TextStyle(fontSize = 14.sp)
                )
                FontSize.MEDIUM -> Typography()
                FontSize.LARGE -> Typography(
                    bodyLarge = TextStyle(fontSize = 18.sp),
                    headlineMedium = TextStyle(fontSize = 28.sp),
                    titleMedium = TextStyle(fontSize = 20.sp)
                )
            }

            CallTranslatorTheme(appTheme = appTheme) {
                ProvideTextStyle(value = TextStyle()) {
                    AppNavigation(speechManager, ttsManager, settingsViewModel = settingsViewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
        ttsManager.shutdown()
    }
}

@Composable
fun AppNavigation(
    speechManager: SpeechRecognitionManager,
    ttsManager: TextToSpeechManager,
    authViewModel: AuthViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val signalingClient = remember { SignalingClient() }
    var incomingCall by remember { mutableStateOf<CallInvite?>(null) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    // Permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudio = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val readContacts = permissions[Manifest.permission.READ_CONTACTS] ?: false
        Log.d("Permissions", "Record Audio: $recordAudio, Read Contacts: $readContacts")
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS
            )
        )
    }

    // Listen for incoming calls if authenticated
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            val uid = auth.currentUser?.uid ?: ""
            db.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                val myNumber = snapshot.getString("phoneNumber") ?: ""
                if (myNumber.isNotEmpty()) {
                    signalingClient.listenForIncomingCalls(uid, myNumber) { invite ->
                        incomingCall = invite
                    }
                }
            }
        }
    }

    if (incomingCall != null) {
        val currentInvite = incomingCall!!
        LaunchedEffect(currentInvite.id) {
            signalingClient.listenForInviteStatus(currentInvite.id) { status ->
                if (status != "PENDING") {
                    if (status == "MISSED") {
                        showMissedCallNotification(context, currentInvite.callerName)
                    }
                    incomingCall = null
                }
            }
        }

        IncomingCallScreen(
            invite = currentInvite,
            onAccept = {
                val myHearIn = settingsViewModel.targetLanguage.value
                signalingClient.updateHearingLanguage(currentInvite.id, myHearIn, false)
                signalingClient.updateInviteStatus(currentInvite.id, "ACCEPTED")
                navController.navigate("call/${currentInvite.id}/${currentInvite.callerName}?isCaller=false")
                incomingCall = null
            },
            onDecline = {
                signalingClient.updateInviteStatus(currentInvite.id, "DECLINED")
                showMissedCallNotification(context, currentInvite.callerName)
                incomingCall = null
            }
        )
    } else {
        NavHost(
            navController = navController,
            startDestination = if (authState is AuthState.Authenticated) "history" else "login"
        ) {
            composable("login") {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegister = { navController.navigate("register") },
                    onLoginSuccess = {
                        navController.navigate("history") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("register") {
                RegisterScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = { navController.popBackStack() },
                    onRegisterSuccess = {
                        navController.navigate("history") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("phone") {


                PhoneScreen(
                    onStartCall = { number ->
                        val roomId = UUID.randomUUID().toString().substring(0, 6).uppercase()
                        val myUid = auth.currentUser?.uid ?: ""
                        db.collection("users").document(myUid).get().addOnSuccessListener { snapshot ->
                            val myName = snapshot.getString("displayName") ?: "Unknown"
                            val myNumber = snapshot.getString("phoneNumber") ?: ""
                            val myHearIn = settingsViewModel.targetLanguage.value
                            val invite = CallInvite(
                                id = roomId,
                                callerUid = myUid,
                                callerName = myName,
                                callerNumber = myNumber,
                                targetNumber = number,
                                status = "PENDING",
                                callerHearingLang = myHearIn,
                                receiverHearingLang = myHearIn
                            )
                            signalingClient.sendCallInvite(invite)
                            navController.navigate("call/$roomId/$number?isCaller=true")
                        }
                    },
                    onContactClick = { contact ->
                        selectedContact = contact
                        navController.navigate("contact_detail")
                    },
                    onNavigateToPhone = {},
                    onNavigateToContacts = { navController.navigate("contacts") },
                    onNavigateToRecents = { navController.navigate("history") },
                    onNavigateToFavorites = { navController.navigate("favorites") },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable("contacts") {
                ContactsScreen(
                    onContactClick = { contact ->
                        selectedContact = contact
                        navController.navigate("contact_detail")
                    },
                    onNavigateToPhone = { navController.navigate("phone") },
                    onNavigateToContacts = {},
                    onNavigateToRecents = { navController.navigate("history") },
                    onNavigateToFavorites = { navController.navigate("favorites") },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable("favorites") {
                FavoritesScreen(
                    onCallNumber = { name, number ->
                        val roomId = UUID.randomUUID().toString().substring(0, 6).uppercase()
                        val myUid = auth.currentUser?.uid ?: ""
                        db.collection("users").document(myUid).get().addOnSuccessListener { snapshot ->
                            val myName = snapshot.getString("displayName") ?: "Unknown"
                            val myNumber = snapshot.getString("phoneNumber") ?: ""
                            val myHearIn = settingsViewModel.targetLanguage.value
                            val invite = CallInvite(
                                id = roomId,
                                callerUid = myUid,
                                callerName = myName,
                                callerNumber = myNumber,
                                targetNumber = number,
                                status = "PENDING",
                                callerHearingLang = myHearIn,
                                receiverHearingLang = myHearIn
                            )
                            signalingClient.sendCallInvite(invite)
                            navController.navigate("call/$roomId/$name?isCaller=true")
                        }
                    },
                    onNavigateToPhone = { navController.navigate("phone") },
                    onNavigateToContacts = { navController.navigate("contacts") { popUpTo("contacts") { inclusive = true } } },
                    onNavigateToRecents = { navController.navigate("history") },
                    onNavigateToFavorites = {},
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPhone = { navController.navigate("phone") },
                    onNavigateToContacts = {
                        navController.navigate("contacts") {
                            popUpTo("contacts") { inclusive = true }
                        }
                    },
                    onNavigateToRecents = { navController.navigate("history") },
                    onNavigateToFavorites = { navController.navigate("favorites") },
                    onNavigateToHearInLanguage = { navController.navigate("settings_hear_in") },
                    onNavigateToAiModel = { navController.navigate("settings_ai_model") },
                    onNavigateToSpeed = { navController.navigate("settings_speed") },
                    onNavigateToAudioQuality = { navController.navigate("settings_audio") },
                    onNavigateToTheme = { navController.navigate("settings_theme") },
                    onNavigateToRingtone = { navController.navigate("settings_ringtone") },
                    onNavigateToPrivacy = { navController.navigate("settings_privacy") },
                    onNavigateToEncryption = { navController.navigate("settings_encryption") },
                    onNavigateToBackendUrl = { navController.navigate("settings_backend") },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable("settings_theme") { ThemeSelectionScreen(settingsViewModel) { navController.popBackStack() } }
            composable("settings_backend") { BackendUrlScreen(settingsViewModel) { navController.popBackStack() } }
            composable("settings_ringtone") { RingtoneSelectionScreen(settingsViewModel) { navController.popBackStack() } }
            composable("settings_privacy") { PrivacySettingsScreen(settingsViewModel) { navController.popBackStack() } }
            composable("settings_encryption") { EncryptionSettingsScreen { navController.popBackStack() } }
            composable("settings_hear_in") {
                val targetLang by settingsViewModel.targetLanguage.collectAsState()
                SelectionScreen(
                    "My \"Hear In\" Language",
                    LanguageConfig.supportedLanguages.map { it.displayName },
                    targetLang,
                    { settingsViewModel.setTargetLanguage(it) },
                    { navController.popBackStack() }
                )
            }
            composable("settings_ai_model") {
                val model by settingsViewModel.aiModel.collectAsState()
                SelectionScreen("AI Model", listOf("Gemini 1.5 Flash", "Gemini 1.5 Pro", "GPT-4o (Mock)"), model, { settingsViewModel.setAiModel(it) }, { navController.popBackStack() })
            }
            composable("settings_speed") {
                val speed by settingsViewModel.translationSpeed.collectAsState()
                SelectionScreen("Translation Speed", listOf("Fast", "Balanced", "Accurate"), speed, { settingsViewModel.setTranslationSpeed(it) }, { navController.popBackStack() })
            }
            composable("settings_audio") {
                val quality by settingsViewModel.audioQuality.collectAsState()
                SelectionScreen("Audio Quality", listOf("Low", "Medium", "High"), quality, { settingsViewModel.setAudioQuality(it) }, { navController.popBackStack() })
            }
            composable("contact_detail") {
                selectedContact?.let { contact ->
                    ContactDetailScreen(
                        contact = contact,
                        initialHearingLang = LanguageConfig.supportedLanguages.find { it.displayName == settingsViewModel.targetLanguage.value } ?: LanguageConfig.getEnglish(),
                        onNavigateBack = { navController.popBackStack() },
                        onStartAiCall = { _, _ ->
                            val roomId = UUID.randomUUID().toString().substring(0, 6).uppercase()
                            val myUid = auth.currentUser?.uid ?: ""
                            db.collection("users").document(myUid).get().addOnSuccessListener { snapshot ->
                                val myName = snapshot.getString("displayName") ?: "Unknown"
                                val myNumber = snapshot.getString("phoneNumber") ?: ""
                                val myHearIn = settingsViewModel.targetLanguage.value
                                
                                val invite = CallInvite(
                                    id = roomId,
                                    callerUid = myUid,
                                    callerName = myName,
                                    callerNumber = myNumber,
                                    targetNumber = contact.phoneNumber,
                                    status = "PENDING",
                                    callerHearingLang = myHearIn,
                                    receiverHearingLang = myHearIn
                                )
                                signalingClient.sendCallInvite(invite)
                                
                                signalingClient.listenForInviteStatus(roomId) { status ->
                                    if (status == "ACCEPTED") {
                                        navController.navigate("call/$roomId/${contact.name}?isCaller=true")
                                    }
                                }
                                
                                navController.navigate("call/$roomId/${contact.name}?isCaller=true")
                            }
                        }
                    )
                }
            }
            composable("history") {
                CallHistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onContactClick = { contact ->
                        selectedContact = contact
                        navController.navigate("contact_detail")
                    },
                    onCallBack = { name, number ->
                        val roomId = UUID.randomUUID().toString().substring(0, 6).uppercase()
                        val myUid = auth.currentUser?.uid ?: ""
                        db.collection("users").document(myUid).get().addOnSuccessListener { snapshot ->
                            val myName = snapshot.getString("displayName") ?: "Unknown"
                            val myNumber = snapshot.getString("phoneNumber") ?: ""
                            val myHearIn = settingsViewModel.targetLanguage.value
                            val invite = CallInvite(
                                id = roomId,
                                callerUid = myUid,
                                callerName = myName,
                                callerNumber = myNumber,
                                targetNumber = number,
                                status = "PENDING",
                                callerHearingLang = myHearIn,
                                receiverHearingLang = myHearIn
                            )
                            signalingClient.sendCallInvite(invite)
                            navController.navigate("call/$roomId/$name?isCaller=true")
                        }
                    },
                    onNavigateToPhone = { navController.navigate("phone") },
                    onNavigateToContacts = { navController.navigate("contacts") },
                    onNavigateToRecents = {},
                    onNavigateToFavorites = { navController.navigate("favorites") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToSearch = { navController.navigate("search") },
                    onAddContact = { navController.navigate("add_contact") }
                )
            }
            composable("search") {
                SearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onContactClick = { contact ->
                        selectedContact = contact
                        navController.navigate("contact_detail")
                    }
                )
            }
            composable("add_contact") {
                AddContactScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onContactAdded = { navController.popBackStack() }
                )
            }
            composable("call/{roomId}/{remoteName}?isCaller={isCaller}") { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                val remoteName = backStackEntry.arguments?.getString("remoteName") ?: "Remote User"
                val isCaller = backStackEntry.arguments?.getString("isCaller")?.toBoolean() ?: false
                
                val callViewModel: CallViewModel = viewModel()
                val myHearIn = settingsViewModel.targetLanguage.value
                val isPrivate = settingsViewModel.privateMode.value
                
                LaunchedEffect(Unit) {
                    callViewModel.setAppSettings(myHearIn, isPrivate, settingsViewModel.aiModel.value, settingsViewModel.translationSpeed.value)
                    callViewModel.setCallDetails(remoteName, "", isCaller)
                    if (isCaller) {
                        callViewModel.startCall(roomId)
                    } else {
                        callViewModel.joinCall(roomId)
                    }
                }

                CallScreen(
                    speechManager = speechManager,
                    ttsManager = ttsManager,
                    remoteName = remoteName,
                    viewModel = callViewModel,
                    onCallEnded = { navController.popBackStack() }
                )
            }
        }
    }
}

fun showMissedCallNotification(context: Context, callerName: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "missed_calls_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "Missed Calls", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.app_logo_foreground)
        .setContentTitle("Missed Call")
        .setContentText("Missed call from $callerName")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}
