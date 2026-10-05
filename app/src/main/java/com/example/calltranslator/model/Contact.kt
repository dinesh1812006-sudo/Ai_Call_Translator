package com.example.calltranslator.model

import android.net.Uri

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: Uri? = null,
    val isAppUser: Boolean = false
)
