package com.example.calltranslator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calltranslator.model.TranslationHistoryItem
import com.example.calltranslator.repository.TranslationHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HistoryState {
    object Loading : HistoryState()
    data class Success(val items: List<TranslationHistoryItem>) : HistoryState()
    data class Error(val message: String) : HistoryState()
}

class HistoryViewModel : ViewModel() {
    private val repository = TranslationHistoryRepository()

    private val _historyState = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val historyState = _historyState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.getHistoryFlow().collect { items ->
                _historyState.value = HistoryState.Success(items)
            }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteItem(id)
            } catch (e: Exception) {
                // Optionally handle error
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                repository.clearAllHistory()
            } catch (e: Exception) {
                // Optionally handle error
            }
        }
    }
}
