package com.example.calltranslator.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calltranslator.LanguageConfig
import com.example.calltranslator.LanguageOption
import com.example.calltranslator.SpeechRecognitionManager
import com.example.calltranslator.TextToSpeechManager
import com.example.calltranslator.model.CallHistoryItem
import com.example.calltranslator.model.CallStateHolder
import com.example.calltranslator.model.TranslationHistoryItem
import com.example.calltranslator.repository.CallHistoryRepository
import com.example.calltranslator.repository.TranslationHistoryRepository
import com.example.calltranslator.webrtc.SignalingClient
import com.example.calltranslator.webrtc.WebRtcManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*
import java.util.*

enum class CallState { IDLE, CALLING, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, FAILED, ENDING, ENDED }
enum class AiMode { NORMAL, TRANSLATION }

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState = _callState.asStateFlow()

    private fun updateCallState(state: CallState) {
        _callState.value = state
        CallStateHolder.updateState(state)
    }

    private val _aiMode = MutableStateFlow(AiMode.NORMAL)
    val aiMode = _aiMode.asStateFlow()

    private val _roomId = MutableStateFlow("")
    val roomId = _roomId.asStateFlow()

    private val _remoteTranslation = MutableStateFlow("")
    val remoteTranslation = _remoteTranslation.asStateFlow()

    private val _callDuration = MutableStateFlow("00:00")
    val callDuration = _callDuration.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn = _isSpeakerOn.asStateFlow()

    private val _myHearingLanguage = MutableStateFlow(LanguageConfig.getEnglish())
    val myHearingLanguage = _myHearingLanguage.asStateFlow()

    private val _remoteHearingLanguageName = MutableStateFlow("Tamil")
    val remoteHearingLanguageName = _remoteHearingLanguageName.asStateFlow()

    private val _failureMessage = MutableStateFlow("Connection Failed")
    val failureMessage = _failureMessage.asStateFlow()

    private val _callQuality = MutableStateFlow("Good")
    val callQuality = _callQuality.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("")
    val detectedLanguage = _detectedLanguage.asStateFlow()

    private val _isLocalSpeaking = MutableStateFlow(false)
    val isLocalSpeaking = _isLocalSpeaking.asStateFlow()

    private val _isRemoteSpeaking = MutableStateFlow(false)
    val isRemoteSpeaking = _isRemoteSpeaking.asStateFlow()

    private var privateMode = false
    private var aiModel = "Gemini 1.5 Flash"
    private var translationSpeed = "Balanced"
    private var timer: Timer? = null
    private var secondsElapsed = 0
    private var callTimeoutJob: Job? = null
    private var hasCleanedUp = false
    private var listenersRegisteredForRoom: String? = null

    // AI-translation pipeline state (Req 9, 10, 12, 13)
    private val _translationError = MutableStateFlow<String?>(null)
    val translationError = _translationError.asStateFlow()
    private var userMicMuted = false
    private var lastSentText = ""
    private var lastSentAtMs = 0L
    private var translationRequestJob: Job? = null

    private val signalingClient = SignalingClient()
    private val historyRepository = TranslationHistoryRepository()
    private val callHistoryRepo = CallHistoryRepository()
    private val translatorViewModel = TranslatorViewModel(application)
    private lateinit var webRtcManager: WebRtcManager
    private var isCaller = false
    private var contactName = ""
    private var contactNumber = ""
    private var callType = "Outgoing"

    // Integrated Managers
    private var speechManager: SpeechRecognitionManager? = null
    private var ttsManager: TextToSpeechManager? = null

    val isTranslating = translatorViewModel.isTranslating

    fun setAppSettings(hearIn: String, isPrivate: Boolean, model: String, speed: String) {
        val langOption = LanguageConfig.supportedLanguages.find { it.displayName == hearIn } ?: LanguageConfig.getEnglish()
        _myHearingLanguage.value = langOption
        privateMode = isPrivate
        aiModel = model
        translationSpeed = speed
    }

    fun setCallDetails(name: String, number: String, outgoing: Boolean) {
        contactName = name
        contactNumber = number
        callType = if (outgoing) "Outgoing" else "Incoming"
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            viewModelScope.launch {
                Log.d("WebRTC", "ICE Connection Change: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> {
                        if (_callState.value != CallState.CONNECTED) {
                            callTimeoutJob?.cancel()
                            callTimeoutJob = null
                            updateCallState(CallState.CONNECTED)
                            Log.d("CALL_STATE", "connected room=${_roomId.value}")
                            _aiMode.value = AiMode.TRANSLATION
                            _callQuality.value = "Good"
                            // AI ON => original WebRTC mic muted; translated audio via signaling+remote TTS.
                            Log.d("AI_TRANSLATION", "enabled")
                            webRtcManager.setTranslationMode(true, userMicMuted)
                            Log.d("AudioDebug", "ICE Connection: $state. AI_AUTO_ENABLED. WebRTC mic muted for translation.")
                            startTimer()
                        }
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        updateCallState(CallState.RECONNECTING)
                        _callQuality.value = "Fair"
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        callTimeoutJob?.cancel()
                        callTimeoutJob = null
                        _failureMessage.value = "Connection Failed"
                        updateCallState(CallState.FAILED)
                        _callQuality.value = "Poor"
                        stopTimer()
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        callTimeoutJob?.cancel()
                        callTimeoutJob = null
                        updateCallState(CallState.ENDED)
                        stopTimer()
                    }
                    else -> {}
                }
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let { signalingClient.sendIceCandidate(_roomId.value, it, isCaller) }
        }
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {
            stream?.audioTracks?.forEach { track ->
                Log.d("AudioDebug", "Remote AudioStream track received: ID=${track.id()}")
                track.setEnabled(true)
            }
        }
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track()
            if (track is AudioTrack) {
                Log.d("AudioDebug", "Remote AudioTrack received: ID=${track.id()}")
                track.setEnabled(true)
            }
        }
    }

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            sdp?.let {
                webRtcManager.setLocalDescription(it, this)
                if (isCaller) {
                    signalingClient.createRoom(_roomId.value, it, 
                        onAnswerReceived = { answer -> webRtcManager.setRemoteDescription(answer, this) },
                        onIceCandidateReceived = { candidate -> webRtcManager.addIceCandidate(candidate) }
                    )
                } else {
                    signalingClient.sendAnswer(_roomId.value, it)
                }
            }
        }
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {
            _failureMessage.value = "Connection Failed"
            updateCallState(CallState.FAILED)
        }
        override fun onSetFailure(p0: String?) {
            _failureMessage.value = "Connection Failed"
            updateCallState(CallState.FAILED)
        }
    }

    init {
        webRtcManager = WebRtcManager(application, peerConnectionObserver)
    }

    fun initTranslation(sm: SpeechRecognitionManager, tm: TextToSpeechManager) {
        this.speechManager = sm
        this.ttsManager = tm
        // Route local STT finals through the pipeline. processSpeech() itself checks
        // aiMode/call state from the flows, so no stale Compose-captured state here.
        sm.onSpeechFinal = { text -> processSpeech(text) }
        Log.d("AI_TRANSLATION", "translation managers attached")
        ensureSignalingListeners()
        // Initial push of my hearing language (only when a room is active)
        if (_roomId.value.isNotEmpty()) {
            Log.d("AI_TRANSLATION", "pushing local hearing language: ${_myHearingLanguage.value.displayName}")
            signalingClient.updateHearingLanguage(_roomId.value, _myHearingLanguage.value.displayName, isCaller)
        }
    }

    /** Register Firebase translation/language listeners once the room id is known. */
    private fun ensureSignalingListeners() {
        val room = _roomId.value
        if (room.isEmpty() || listenersRegisteredForRoom == room) return
        listenersRegisteredForRoom = room
        // Listen for remote translations
        signalingClient.listenForTranslations(room) { text, senderIsCaller ->
            if (senderIsCaller != isCaller) { // If it's from the other person
                Log.d("AI_TRANSLATION", "remote translation received: $text. Triggering local TTS.")
                _remoteTranslation.value = text
                _isRemoteSpeaking.value = true
                viewModelScope.launch {
                    delay(1500L)
                    _isRemoteSpeaking.value = false
                }
            }
        }
        // Listen for hearing language updates
        signalingClient.listenForHearingLanguages(room) { callerLang, calleeLang ->
            val remoteLang = if (isCaller) calleeLang else callerLang
            Log.d("AI_TRANSLATION", "hearing language update: remote wants to hear $remoteLang")
            _remoteHearingLanguageName.value = remoteLang
        }
    }

    fun setMyHearingLanguage(language: LanguageOption) {
        _myHearingLanguage.value = language
        signalingClient.updateHearingLanguage(_roomId.value, language.displayName, isCaller)
    }

    fun clearRemoteTranslation() {
        _remoteTranslation.value = ""
    }

    fun startCall(id: String) {
        isCaller = true
        _roomId.value = id
        updateCallState(CallState.CALLING)
        Log.d("CALL_STATE", "calling room=$id")
        ensureSignalingListeners()
        webRtcManager.createPeerConnection()
        webRtcManager.createOffer(sdpObserver)

        signalingClient.listenForInviteStatus(id) { status ->
            if (status == "DECLINED") {
                callTimeoutJob?.cancel()
                callTimeoutJob = null
                _failureMessage.value = "Call Declined"
                updateCallState(CallState.FAILED)
            }
        }

        // 30 seconds outgoing call timeout (No Answer)
        callTimeoutJob = viewModelScope.launch {
            delay(30000L)
            if (_callState.value == CallState.CALLING || _callState.value == CallState.CONNECTING) {
                _failureMessage.value = "No Answer"
                updateCallState(CallState.FAILED)
                signalingClient.updateInviteStatus(id, "MISSED")
                endCall()
            }
        }
    }

    fun joinCall(id: String) {
        isCaller = false
        _roomId.value = id
        updateCallState(CallState.CONNECTING)
        Log.d("CALL_STATE", "joining room=$id")
        ensureSignalingListeners()
        webRtcManager.createPeerConnection()
        // Mute mic until connected to prevent ringtone audio leak to caller
        webRtcManager.toggleMic(false)

        signalingClient.joinRoom(id, 
            onOfferReceived = { offer ->
                webRtcManager.setRemoteDescription(offer, sdpObserver)
                webRtcManager.createAnswer(sdpObserver)
            },
            onIceCandidateReceived = { candidate -> webRtcManager.addIceCandidate(candidate) }
        )
    }

    fun toggleAiMode(mode: AiMode) {
        if (_aiMode.value == mode) return
        _aiMode.value = mode
        if (mode == AiMode.TRANSLATION) {
            Log.d("AI_TRANSLATION", "enabled")
            _translationError.value = null
            // Mute original WebRTC mic; STT (device mic) keeps working independently.
            if (_callState.value == CallState.CONNECTED) {
                webRtcManager.setTranslationMode(true, userMicMuted)
            }
        } else {
            Log.d("AI_TRANSLATION", "disabled - restoring normal microphone audio")
            translationRequestJob?.cancel()
            translationRequestJob = null
            lastSentText = ""
            try { speechManager?.stopListening() } catch (_: Exception) {}
            try { ttsManager?.stop() } catch (_: Exception) {}
            _remoteTranslation.value = ""
            if (_callState.value == CallState.CONNECTED) {
                webRtcManager.setTranslationMode(false, userMicMuted)
            }
        }
    }

    fun detectLanguage(text: String): String {
        if (text.any { it in '\u0B80'..'\u0BFF' }) return "Tamil"
        if (text.any { it in '\u0900'..'\u097F' }) return "Hindi"
        if (text.any { it in '\u0C00'..'\u0C7F' }) return "Telugu"
        if (text.any { it in '\u0D00'..'\u0D7F' }) return "Malayalam"
        if (text.any { it in '\u0C80'..'\u0CFF' }) return "Kannada"
        val lower = text.lowercase()
        if (lower.contains(" el ") || lower.contains(" la ") || lower.contains(" hola ")) return "Spanish"
        if (lower.contains(" le ") || lower.contains(" la ") || lower.contains(" bonjour ")) return "French"
        if (lower.contains(" der ") || lower.contains(" die ") || lower.contains(" hallo ")) return "German"
        return "English"
    }

    fun processSpeech(text: String) {
        if (_aiMode.value != AiMode.TRANSLATION) return
        if (_callState.value != CallState.CONNECTED) return
        val clean = text.trim()
        if (clean.isEmpty()) return
        // Prevent duplicate Gemini requests for the same sentence (Req 5).
        val now = System.currentTimeMillis()
        if (clean.equals(lastSentText, ignoreCase = true) && (now - lastSentAtMs) < 4000L) {
            Log.d("STT", "duplicate result ignored: $clean")
            return
        }
        lastSentText = clean
        lastSentAtMs = now

        val detected = detectLanguage(clean)
        _detectedLanguage.value = detected
        _isLocalSpeaking.value = true
        viewModelScope.launch {
            delay(1200L)
            _isLocalSpeaking.value = false
        }

        // Language mapping (Req 3): my STT language = my hearing language code;
        // Gemini target = what the REMOTE user wants to hear (as ISO code).
        val sourceCode = _myHearingLanguage.value.code
        val targetName = _remoteHearingLanguageName.value
        val targetCode = LanguageConfig.supportedLanguages
            .find { it.displayName.equals(targetName, ignoreCase = true) }?.code ?: targetName
        Log.d("STT", "final result = $clean")
        Log.d("GEMINI", "source=$sourceCode target=$targetCode")
        _translationError.value = null

        translationRequestJob?.cancel()
        translationRequestJob = viewModelScope.launch {
            try {
                translatorViewModel.translate(
                    clean, source = sourceCode, target = targetCode,
                    model = aiModel, speed = translationSpeed
                ) { translatedText ->
                    val out = translatedText.trim()
                    if (out.isEmpty()) {
                        Log.e("GEMINI", "empty translation, not sending")
                        _translationError.value = "Translation returned no text."
                        return@translate
                    }
                    Log.d("GEMINI", "result=$out")
                    if (_aiMode.value == AiMode.TRANSLATION && _callState.value == CallState.CONNECTED) {
                        signalingClient.sendTranslation(_roomId.value, out, isCaller)

                        if (!privateMode) {
                            viewModelScope.launch {
                                try {
                                    historyRepository.saveTranslation(
                                        TranslationHistoryItem(
                                            sourceLanguage = detected,
                                            targetLanguage = targetName,
                                            sourceLanguageCode = sourceCode,
                                            targetLanguageCode = targetCode,
                                            originalText = clean,
                                            translatedText = out,
                                            direction = "$detected->$targetName"
                                        )
                                    )
                                } catch (e: Exception) {
                                    Log.e("History", "Async save failed: ${e.message}")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GEMINI", "translation failed: ${e.message}")
                _translationError.value = when {
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                        "No internet connection. Check network and backend URL."
                    else -> "Translation failed. Please try again."
                }
            }
        }
    }

    fun clearTranslationError() { _translationError.value = null }

    fun endCall() {
        cleanupCall()
        updateCallState(CallState.ENDED)
    }

    private fun cleanupCall() {
        if (hasCleanedUp) return
        hasCleanedUp = true

        Log.d("CALL_STATE", "ending - cleaning up translation pipeline")
        saveCallRecord()
        callTimeoutJob?.cancel()
        callTimeoutJob = null
        translationRequestJob?.cancel()
        translationRequestJob = null
        lastSentText = ""
        stopTimer()
        try {
            speechManager?.stopListening()
            speechManager?.destroy()
        } catch (_: Exception) {}
        speechManager = null
        try {
            ttsManager?.stop()
            ttsManager?.shutdown()
        } catch (_: Exception) {}
        ttsManager = null
        if (_roomId.value.isNotEmpty()) {
            try {
                signalingClient.updateInviteStatus(_roomId.value, "CANCELLED")
                signalingClient.endCall(_roomId.value)
            } catch (_: Exception) {}
        }
        try {
            webRtcManager.close()
        } catch (_: Exception) {}
        try { signalingClient.clearListeners() } catch (_: Exception) {}
        listenersRegisteredForRoom = null
        CallStateHolder.updateState(CallState.ENDED)
        Log.d("CALL_STATE", "cleanup complete")
    }

    private fun saveCallRecord() {
        if (privateMode) return
        viewModelScope.launch {
            try {
                val finalType = when {
                    _callState.value == CallState.CONNECTED -> callType
                    _failureMessage.value == "Call Declined" -> "Missed"
                    _failureMessage.value == "No Answer" -> "Missed"
                    isCaller -> "Outgoing"
                    else -> "Missed"
                }
                callHistoryRepo.saveCall(
                    CallHistoryItem(
                        name = contactName,
                        phoneNumber = contactNumber,
                        type = finalType,
                        duration = _callDuration.value,
                        aiEnabled = _aiMode.value == AiMode.TRANSLATION
                    )
                )
            } catch (e: Exception) {
                Log.e("CallHistory", "Failed to save call history: ${e.message}")
            }
        }
    }
    
    fun toggleMic(enabled: Boolean) {
        userMicMuted = !enabled
        val aiOn = _aiMode.value == AiMode.TRANSLATION &&
            _callState.value == CallState.CONNECTED
        // Effective WebRTC mic = user-unmuted AND translation-off.
        webRtcManager.setTranslationMode(aiOn, userMicMuted)
    }

    fun toggleSpeaker() {
        val newState = !_isSpeakerOn.value
        _isSpeakerOn.value = newState
        webRtcManager.toggleSpeaker(newState)
    }

    private fun startTimer() {
        if (timer != null) return
        secondsElapsed = 0
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                secondsElapsed++
                val minutes = secondsElapsed / 60
                val seconds = secondsElapsed % 60
                _callDuration.value = String.format("%02d:%02d", minutes, seconds)
            }
        }, 1000, 1000)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupCall()
    }
}
