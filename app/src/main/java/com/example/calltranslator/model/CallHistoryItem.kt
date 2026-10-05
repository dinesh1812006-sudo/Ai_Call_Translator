package com.example.calltranslator.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class CallHistoryItem(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val type: String = "Incoming", // Incoming, Outgoing, Missed
    val duration: String = "00:00",
    val aiEnabled: Boolean = true,
    @ServerTimestamp
    val timestamp: Timestamp? = null
)
