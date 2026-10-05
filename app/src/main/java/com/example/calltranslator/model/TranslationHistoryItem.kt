package com.example.calltranslator.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class TranslationHistoryItem(
    val id: String = "",
    val userId: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "",
    val sourceLanguageCode: String = "",
    val targetLanguageCode: String = "",
    val originalText: String = "",
    val translatedText: String = "",
    val direction: String = "",
    @ServerTimestamp
    val timestamp: Timestamp? = null
)
