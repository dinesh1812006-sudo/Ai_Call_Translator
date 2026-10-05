package com.example.calltranslator.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme { LIGHT, DARK, PURPLE, MIDNIGHT, AMOLED }
enum class FontSize { SMALL, MEDIUM, LARGE }

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // Theme Setting
    private val _appTheme = MutableStateFlow(
        try {
            AppTheme.valueOf(prefs.getString("app_theme", AppTheme.DARK.name) ?: AppTheme.DARK.name)
        } catch (_: Exception) {
            AppTheme.DARK
        }
    )
    val appTheme = _appTheme.asStateFlow()

    // Font Size Setting
    private val _fontSize = MutableStateFlow(
        try {
            FontSize.valueOf(prefs.getString("font_size", FontSize.MEDIUM.name) ?: FontSize.MEDIUM.name)
        } catch (_: Exception) {
            FontSize.MEDIUM
        }
    )
    val fontSize = _fontSize.asStateFlow()

    // Ringtone Setting
    private val _ringtone = MutableStateFlow(prefs.getString("ringtone_choice", "Default Ringtone") ?: "Default Ringtone")
    val ringtone = _ringtone.asStateFlow()

    // Vibration Setting
    private val _vibration = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val vibration = _vibration.asStateFlow()

    // Private Mode Setting
    private val _privateMode = MutableStateFlow(prefs.getBoolean("private_mode", false))
    val privateMode = _privateMode.asStateFlow()

    private val _saveCallHistory = MutableStateFlow(prefs.getBoolean("save_call_history", true))
    val saveCallHistory = _saveCallHistory.asStateFlow()

    private val _saveTranslationHistory = MutableStateFlow(prefs.getBoolean("save_trans_history", true))
    val saveTranslationHistory = _saveTranslationHistory.asStateFlow()

    // Notifications Settings
    private val _incomingNotif = MutableStateFlow(prefs.getBoolean("incoming_notif", true))
    val incomingNotif = _incomingNotif.asStateFlow()

    private val _missedNotif = MutableStateFlow(prefs.getBoolean("missed_notif", true))
    val missedNotif = _missedNotif.asStateFlow()

    private val _statusNotif = MutableStateFlow(prefs.getBoolean("status_notif", true))
    val statusNotif = _statusNotif.asStateFlow()

    // Language Settings (Hear In Language)
    private val _targetLanguage = MutableStateFlow(prefs.getString("target_lang", "Tamil") ?: "Tamil")
    val targetLanguage = _targetLanguage.asStateFlow()

    // Translation Settings
    private val _aiModel = MutableStateFlow(prefs.getString("ai_model", "Gemini 1.5 Flash") ?: "Gemini 1.5 Flash")
    val aiModel = _aiModel.asStateFlow()

    private val _translationSpeed = MutableStateFlow(prefs.getString("trans_speed", "Balanced") ?: "Balanced")
    val translationSpeed = _translationSpeed.asStateFlow()

    // Backend API URL Setting
    private val _backendUrl = MutableStateFlow(prefs.getString("backend_url", "http://10.0.2.2:8000/") ?: "http://10.0.2.2:8000/")
    val backendUrl = _backendUrl.asStateFlow()

    // Call Settings
    private val _audioQuality = MutableStateFlow(prefs.getString("audio_quality", "High") ?: "High")
    val audioQuality = _audioQuality.asStateFlow()

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        prefs.edit().putString("app_theme", theme.name).apply()
    }

    fun setFontSize(size: FontSize) {
        _fontSize.value = size
        prefs.edit().putString("font_size", size.name).apply()
    }

    fun setRingtone(ringtoneName: String) {
        _ringtone.value = ringtoneName
        prefs.edit().putString("ringtone_choice", ringtoneName).apply()
    }

    fun setVibration(enabled: Boolean) {
        _vibration.value = enabled
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
    }

    fun setPrivateMode(enabled: Boolean) {
        _privateMode.value = enabled
        prefs.edit().putBoolean("private_mode", enabled).apply()
        if (enabled) {
            setSaveCallHistory(false)
            setSaveTranslationHistory(false)
        }
    }

    fun setSaveCallHistory(enabled: Boolean) {
        _saveCallHistory.value = enabled
        prefs.edit().putBoolean("save_call_history", enabled).apply()
    }

    fun setSaveTranslationHistory(enabled: Boolean) {
        _saveTranslationHistory.value = enabled
        prefs.edit().putBoolean("save_trans_history", enabled).apply()
    }

    fun setIncomingNotif(enabled: Boolean) {
        _incomingNotif.value = enabled
        prefs.edit().putBoolean("incoming_notif", enabled).apply()
    }

    fun setMissedNotif(enabled: Boolean) {
        _missedNotif.value = enabled
        prefs.edit().putBoolean("missed_notif", enabled).apply()
    }

    fun setStatusNotif(enabled: Boolean) {
        _statusNotif.value = enabled
        prefs.edit().putBoolean("status_notif", enabled).apply()
    }

    fun setTargetLanguage(lang: String) {
        _targetLanguage.value = lang
        prefs.edit().putString("target_lang", lang).apply()
    }

    fun setAiModel(model: String) {
        _aiModel.value = model
        prefs.edit().putString("ai_model", model).apply()
    }

    fun setTranslationSpeed(speed: String) {
        _translationSpeed.value = speed
        prefs.edit().putString("trans_speed", speed).apply()
    }

    fun setBackendUrl(url: String) {
        // Single central backend URL (emulator default, LAN IP, or cloud HTTPS).
        val clean = url.trim().trimEnd()
        _backendUrl.value = clean
        prefs.edit().putString("backend_url", clean).apply()
    }

    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
        prefs.edit().putString("audio_quality", quality).apply()
    }
}
