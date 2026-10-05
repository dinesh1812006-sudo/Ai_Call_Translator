package com.example.calltranslator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calltranslator.model.CallHistoryItem
import com.example.calltranslator.repository.CallHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CallHistoryState {
    object Loading : CallHistoryState()
    data class Success(val items: List<CallHistoryItem>) : CallHistoryState()
    data class Error(val message: String) : CallHistoryState()
}

class CallHistoryViewModel : ViewModel() {
    private val repository = CallHistoryRepository()

    private val _historyState = MutableStateFlow<CallHistoryState>(CallHistoryState.Loading)
    val historyState = _historyState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.getCallHistoryFlow().collect { items ->
                _historyState.value = CallHistoryState.Success(items)
            }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteCall(id)
            } catch (_: Exception) {}
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                repository.clearAllCalls()
            } catch (_: Exception) {}
        }
    }
}
