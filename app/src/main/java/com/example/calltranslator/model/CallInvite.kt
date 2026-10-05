package com.example.calltranslator.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class CallInvite(
    val id: String = "", // Room ID
    val callerUid: String = "",
    val callerName: String = "",
    val callerNumber: String = "",
    val targetNumber: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED, ENDED
    @ServerTimestamp val timestamp: Date? = null,
    val callerHearingLang: String = "English",
    val receiverHearingLang: String = "Tamil"
)
