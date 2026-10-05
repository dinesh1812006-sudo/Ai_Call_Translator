package com.example.calltranslator

data class LanguageOption(
    val displayName: String,
    val code: String,          // ISO code for Gemini
    val speechLocale: String,  // For Microphone (STT)
    val ttsLocale: String      // For Voice output (TTS)
)

object LanguageConfig {
    val supportedLanguages = listOf(
        LanguageOption("English", "en", "en-IN", "en-IN"),
        LanguageOption("Tamil", "ta", "ta-IN", "ta-IN"),
        LanguageOption("Hindi", "hi", "hi-IN", "hi-IN"),
        LanguageOption("Telugu", "te", "te-IN", "te-IN"),
        LanguageOption("Malayalam", "ml", "ml-IN", "ml-IN"),
        LanguageOption("Kannada", "kn", "kn-IN", "kn-IN"),
        LanguageOption("Spanish", "es", "es-ES", "es-ES"),
        LanguageOption("French", "fr", "fr-FR", "fr-FR"),
        LanguageOption("German", "de", "de-DE", "de-DE")
    )

    fun getEnglish() = supportedLanguages[0]
    fun getTamil() = supportedLanguages[1]
}
