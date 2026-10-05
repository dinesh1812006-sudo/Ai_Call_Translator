package com.example.calltranslator.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcManager(
    private val context: Context,
    private val observer: PeerConnection.Observer
) {
    private var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource
    private var localAudioTrack: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioDeviceModule: JavaAudioDeviceModule? = null

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // Initialize Audio Device Module
        audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .createAudioDeviceModule()

        val factoryOptions = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        val audioConstraints = MediaConstraints()
        localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints)
    }

    fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        // Add SDP semantics for better audio/video handling
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        
        // Request Audio Focus
        requestAudioFocus()

        // Setup Audio for Call - Explicitly set mode and speaker
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        
        // Ensure volume is at a reasonable level
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVol * 0.8).toInt(), 0)
        
        Log.d("AudioDebug", "AudioManager: mode=MODE_IN_COMMUNICATION, speaker=true, volume=80%")

        // Create and add local audio track
        localAudioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", localAudioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack, listOf("local_stream"))
        Log.d("AudioDebug", "Local audio track: created and added to PeerConnection")
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    fun createOffer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createOffer(sdpObserver, constraints)
    }

    fun createAnswer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createAnswer(sdpObserver, constraints)
    }

    fun setLocalDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        peerConnection?.setLocalDescription(sdpObserver, sdp)
    }

    fun setRemoteDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        peerConnection?.setRemoteDescription(sdpObserver, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun toggleMic(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        Log.d("WEBRTC_AUDIO", if (enabled) "original microphone ENABLED" else "original microphone DISABLED")
    }

    /**
     * AI Translation audio mode (Requirement 9).
     *
     * Architecture note: WebRTC's JavaAudioDeviceModule captures directly from the
     * device microphone into the outgoing AudioTrack. There is no supported API on
     * this WebRTC version (150.7871.01) to push Android TTS PCM bytes into the
     * outgoing track without replacing the entire AudioDeviceModule with a custom
     * mixer (high risk, high latency, echo-prone).
     *
     * The app's designed (and working) translated-audio path is therefore:
     *   Mic -> STT (local) -> Gemini via FastAPI -> translated TEXT via Firebase
     *   -> TTS spoken on the REMOTE phone.
     * The remote user ACTUALLY HEARS the translated voice through their own
     * speaker/earpiece. The original voice is muted on the WebRTC track so the
     * remote hears ONLY the translation, not a double (original + translation).
     *
     * @param translationOn true = mute original WebRTC mic, translated audio ENABLED via signaling+remote TTS.
     */
    fun setTranslationMode(translationOn: Boolean, userMicMuted: Boolean = false) {
        val micEnabled = !userMicMuted && !translationOn
        localAudioTrack?.setEnabled(micEnabled)
        if (translationOn) {
            Log.d("WEBRTC_AUDIO", "original microphone disabled")
            Log.d("WEBRTC_AUDIO", "translated audio enabled (via signaling + remote TTS)")
        } else {
            Log.d("WEBRTC_AUDIO", if (micEnabled) "original microphone ENABLED, translated audio DISABLED"
                else "original microphone DISABLED by user mute")
        }
    }

    fun toggleSpeaker(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    fun close() {
        peerConnection?.close()
        peerConnection = null
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        Log.d("WebRTC", "PeerConnection closed, AudioManager reset to NORMAL")
    }
}
