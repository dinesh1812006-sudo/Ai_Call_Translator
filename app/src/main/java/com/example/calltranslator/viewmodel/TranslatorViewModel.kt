package com.example.calltranslator.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calltranslator.model.ParticipantState
import com.example.calltranslator.model.TranslationRequest
import com.example.calltranslator.network.ApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

enum class ActiveSpeaker { NONE, PERSON_A, PERSON_B }

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val rawUrl = prefs.getString("backend_url", "http://10.0.2.2:8000/") ?: "http://10.0.2.2:8000/"
    private val baseUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    private var activeTranslationJob: Job? = null

    // Separate states for both participants
    private val _personAState = MutableStateFlow(ParticipantState("Person A"))
    val personAState = _personAState.asStateFlow()

    private val _personBState = MutableStateFlow(ParticipantState("Person B"))
    val personBState = _personBState.asStateFlow()

    private val _activeSpeaker = MutableStateFlow(ActiveSpeaker.NONE)
    val activeSpeaker = _activeSpeaker.asStateFlow()

    private val _isTwoWayMode = MutableStateFlow(false)
    val isTwoWayMode = _isTwoWayMode.asStateFlow()

    fun setTwoWayMode(enabled: Boolean) {
        _isTwoWayMode.value = enabled
        if (!enabled) {
            _activeSpeaker.value = ActiveSpeaker.NONE
            _personAState.update { ParticipantState("Person A") }
            _personBState.update { ParticipantState("Person B") }
        }
    }

    fun setActiveSpeaker(speaker: ActiveSpeaker) {
        _activeSpeaker.value = speaker
    }

    // Existing Single Mode state
    private val _translatedText = MutableStateFlow("")
    val translatedText = _translatedText.asStateFlow()
    private val _isTranslating = MutableStateFlow(false)
    val isTranslating = _isTranslating.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun translate(
        text: String, 
        source: String = "auto", 
        target: String, 
        model: String = "Gemini 1.5 Flash",
        speed: String = "Balanced",
        speaker: ActiveSpeaker = ActiveSpeaker.NONE,
        onComplete: (String) -> Unit = {}
    ) {
        if (text.isBlank()) return

        Log.d("AI_CALL_TRANSLATION", "AI_CALL_TRANSLATION_STARTED: text='$text', target='$target'")
        activeTranslationJob?.cancel()

        activeTranslationJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            Log.d("Performance", "TRANSLATION_REQUEST_STARTED")

            updateState(speaker, isTranslating = true, status = "Translating...")
            if (speaker == ActiveSpeaker.NONE) _isTranslating.value = true
            
            var retryCount = 0
            val maxRetries = 1
            var success = false

            while (retryCount <= maxRetries && !success) {
                try {
                    val response = apiService.translateText(
                        TranslationRequest(text, source, target, model, speed)
                    )
                    // Handle backend-reported errors and empty/invalid responses (Req 4)
                    val backendError = response.error
                    val result = response.translated_text.trim()
                    if (!backendError.isNullOrBlank()) {
                        Log.e("GEMINI", "backend error: $backendError (source=$source target=$target)")
                        throw IllegalStateException(backendError)
                    }
                    if (result.isEmpty()) {
                        Log.e("GEMINI", "empty response (source=$source target=$target)")
                        throw IllegalStateException("Empty translation response")
                    }
                    Log.d("GEMINI", "source=$source target=$target")
                    Log.d("GEMINI", "result=$result")
                    
                    val duration = System.currentTimeMillis() - startTime
                    Log.d("Performance", "Translation COMPLETE in ${duration}ms")

                    if (speaker == ActiveSpeaker.NONE) {
                        _translatedText.value = result
                    } else {
                        updateState(speaker, translatedText = result, recognizedText = text, status = "Translated")
                    }
                    onComplete(result)
                    success = true
                } catch (e: Exception) {
                    if (retryCount >= maxRetries) {
                        Log.e("Translation", "TRANSLATION_FAILED: ${e.message}")
                        if (speaker == ActiveSpeaker.NONE) _error.value = "AI Service Error: ${e.message}"
                        else updateState(speaker, error = "AI Error", status = "Error")
                    } else {
                        Log.w("Translation", "Transient error, retrying... ${e.message}")
                        retryCount++
                    }
                }
            }
            
            updateState(speaker, isTranslating = false)
            if (speaker == ActiveSpeaker.NONE) _isTranslating.value = false
        }
    }

    private fun updateState(
        speaker: ActiveSpeaker,
        recognizedText: String? = null,
        translatedText: String? = null,
        isTranslating: Boolean? = null,
        status: String? = null,
        error: String? = null
    ) {
        val updater = { old: ParticipantState ->
            old.copy(
                recognizedText = recognizedText ?: old.recognizedText,
                translatedText = translatedText ?: old.translatedText,
                isTranslating = isTranslating ?: old.isTranslating,
                status = status ?: old.status,
                error = error
            )
        }
        when (speaker) {
            ActiveSpeaker.PERSON_A -> _personAState.update(updater)
            ActiveSpeaker.PERSON_B -> _personBState.update(updater)
            else -> {}
        }
    }

    fun updateSpeechState(speaker: ActiveSpeaker, isListening: Boolean = false, isSpeaking: Boolean = false, text: String = "") {
        val updater = { old: ParticipantState -> 
            old.copy(
                isListening = isListening, 
                isSpeaking = isSpeaking, 
                recognizedText = if(text.isNotEmpty()) text else old.recognizedText,
                status = if(isListening) "Listening..." else if(isSpeaking) "Speaking..." else old.status
            ) 
        }
        when (speaker) {
            ActiveSpeaker.PERSON_A -> _personAState.update(updater)
            ActiveSpeaker.PERSON_B -> _personBState.update(updater)
            else -> {}
        }
    }
}
