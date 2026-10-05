package com.example.calltranslator

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupProgressListener()
            
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(attributes)
            Log.d("TTS", "TTS_INITIALIZED: Routed to USAGE_VOICE_COMMUNICATION")
        } else {
            _error.value = "TTS Initialization failed."
            Log.e("TTS", "TTS_FAILED: Initialization status: $status")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                Log.d("TTS", "started")
                Log.d("AudioDebug", "TTS_STARTED")
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                Log.d("TTS", "completed")
                Log.d("AudioDebug", "TTS_SUCCESS")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                Log.e("AudioDebug", "TTS_FAILED")
                _error.value = "Speech playback error."
            }
        })
    }

    fun speak(text: String, language: LanguageOption) {
        if (!isInitialized || text.isBlank()) {
            Log.w("TTS", "TTS_FAILED: Speak failed: init=$isInitialized, textEmpty=${text.isBlank()}")
            return
        }

        val locale = Locale.forLanguageTag(language.ttsLocale)
        Log.d("TTS", "language=${language.code}")
        Log.d("AudioDebug", "TTS: Attempting to speak in ${language.displayName} (${language.ttsLocale})")
        
        val result = tts?.setLanguage(locale)
        Log.d("AudioDebug", "TTS: setLanguage result=$result")

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val err = "TTS_FAILED: Language ${language.displayName} (${language.ttsLocale}) not supported or missing data."
            Log.e("AudioDebug", err)
            _error.value = err
            return
        }

        _error.value = null
        val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CallTranslatorID")
        if (speakResult == TextToSpeech.SUCCESS) {
            Log.d("TTS", "TTS_STARTED: speak() called successfully")
        } else {
            Log.e("TTS", "TTS_FAILED: speak() returned error code: $speakResult")
        }
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            Log.d("TTS", "TTS_STOPPED: shutdown")
        } catch (_: Exception) {}
    }
}
