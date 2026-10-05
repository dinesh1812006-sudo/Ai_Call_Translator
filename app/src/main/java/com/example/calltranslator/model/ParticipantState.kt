package com.example.calltranslator.model

data class ParticipantState(
    val name: String,
    val recognizedText: String = "",
    val translatedText: String = "",
    val status: String = "Idle",
    val isListening: Boolean = false,
    val isTranslating: Boolean = false,
    val isSpeaking: Boolean = false,
    val error: String? = null
)
