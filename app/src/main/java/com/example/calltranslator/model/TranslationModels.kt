package com.example.calltranslator.model

data class TranslationRequest(
    val text: String,
    val source_lang: String,
    val target_lang: String,
    val model: String = "Gemini 1.5 Flash",
    val speed: String = "Balanced"
)

data class TranslationResponse(
    val translated_text: String,
    val error: String? = null
)
