package com.example.calltranslator.model

import com.example.calltranslator.viewmodel.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallStateHolder {
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState = _callState.asStateFlow()

    fun updateState(state: CallState) {
        _callState.value = state
    }
}
