package com.example.calltranslator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _recognizedText = MutableStateFlow("")
    val recognizedText = _recognizedText.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var lastProcessedText = ""
    private var currentLanguage: LanguageOption? = null
    private var shouldListen = false

    var onSpeechFinal: ((String) -> Unit)? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            _error.value = "Speech recognition is not available on this device"
            Log.e("AI_TRANSLATION", "STT_FAILED: Speech recognition not available")
        }
    }

    fun startListening(language: LanguageOption) {
        currentLanguage = language
        shouldListen = true

        if (_isListening.value) return

        Log.d("AI_CALL_TRANSLATION", "AI_CALL_TRANSLATION_STARTED")
        Log.d("AI_TRANSLATION", "STT_STARTED: Language=${language.displayName} (${language.speechLocale})")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _error.value = null
                _isListening.value = true
                _recognizedText.value = ""
                lastProcessedText = ""
                Log.d("AI_TRANSLATION", "STT_READY")
            }

            override fun onBeginningOfSpeech() {
                Log.d("AI_TRANSLATION", "STT Beginning of speech")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _isListening.value = false
                Log.d("AI_TRANSLATION", "STT End of speech")
            }

            override fun onError(errorCode: Int) {
                _isListening.value = false
                val message = when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error."
                    SpeechRecognizer.ERROR_CLIENT -> {
                        initRecognizer()
                        "Recognizer error."
                    }
                    else -> "Mic error: $errorCode"
                }
                Log.e("AI_TRANSLATION", "STT_FAILED: Code $errorCode: $message")
                _error.value = message

                if (shouldListen && currentLanguage != null) {
                    Handler(context.mainLooper).postDelayed({
                        if (shouldListen && !_isListening.value) {
                            startListening(currentLanguage!!)
                        }
                    }, 500L)
                }
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0].trim()
                    if (text.isNotEmpty() && text != lastProcessedText) {
                        _recognizedText.value = text
                        lastProcessedText = text
                        Log.d("STT", "final result = $text")
                        Log.d("AI_TRANSLATION", "STT_RESULT_RECEIVED")
                        onSpeechFinal?.invoke(text)
                    }
                }

                if (shouldListen && currentLanguage != null) {
                    Handler(context.mainLooper).postDelayed({
                        if (shouldListen && !_isListening.value) {
                            startListening(currentLanguage!!)
                        }
                    }, 300L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    _recognizedText.value = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("AI_TRANSLATION", "STT_FAILED: Failed to start: ${e.message}")
            _error.value = "Failed to start microphone."
        }
    }

    fun stopListening() {
        shouldListen = false
        speechRecognizer?.stopListening()
        _isListening.value = false
        Log.d("AI_TRANSLATION", "STT_STOPPED")
    }
    
    fun destroy() {
        shouldListen = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d("AI_TRANSLATION", "STT_STOPPED: Destroyed")
    }
}
