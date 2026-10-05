package com.example.calltranslator.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import com.example.calltranslator.model.AuthState
import com.example.calltranslator.util.PhoneNumberUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(
        if (auth.currentUser != null) AuthState.Authenticated else AuthState.Idle
    )
    val authState = _authState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    fun clearToast() { _toastMessage.value = null }

    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _authState.value = AuthState.Error("Email and password cannot be empty")
            return
        }
        _authState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState.Authenticated
                } else {
                    val error = task.exception?.message ?: "Login failed"
                    _authState.value = AuthState.Error(mapFirebaseError(error))
                }
            }
    }

    fun resetPassword(email: String) {
        if (email.isEmpty()) {
            _toastMessage.value = "Please enter your email"
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _toastMessage.value = "Invalid email format"
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _toastMessage.value = "Password reset email sent. Please check your email."
                } else {
                    _toastMessage.value = task.exception?.message ?: "Failed to send reset email"
                }
            }
    }

    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (task.result.additionalUserInfo?.isNewUser == true) {
                        createProfile(user)
                    } else {
                        _authState.value = AuthState.Authenticated
                    }
                } else {
                    _authState.value = AuthState.Error(task.exception?.message ?: "Google Sign-In failed")
                }
            }
    }

    private fun createProfile(user: FirebaseUser?) {
        val uid = user?.uid ?: ""
        val normalizedNumber = PhoneNumberUtils.normalize(user?.phoneNumber ?: "")
        val userData = hashMapOf(
            "uid" to uid,
            "email" to (user?.email ?: ""),
            "phoneNumber" to normalizedNumber,
            "displayName" to (user?.displayName ?: "Google User"),
            "aiCallEnabled" to true
        )
        db.collection("users").document(uid).set(userData)
            .addOnSuccessListener {
                _authState.value = AuthState.Authenticated
            }
            .addOnFailureListener { e ->
                _authState.value = AuthState.Error("Profile creation failed: ${e.message}")
            }
    }

    fun register(email: String, password: String, confirmPass: String, phoneNumber: String, displayName: String) {
        if (email.isEmpty() || password.isEmpty() || phoneNumber.isEmpty() || displayName.isEmpty()) {
            _authState.value = AuthState.Error("All fields are required")
            return
        }
        if (password != confirmPass) {
            _authState.value = AuthState.Error("Passwords do not match")
            return
        }
        _authState.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    val normalizedNumber = PhoneNumberUtils.normalize(phoneNumber)
                    val user = hashMapOf(
                        "uid" to uid,
                        "email" to email,
                        "phoneNumber" to normalizedNumber,
                        "displayName" to displayName,
                        "aiCallEnabled" to true
                    )
                    db.collection("users").document(uid).set(user)
                        .addOnSuccessListener {
                            _authState.value = AuthState.Authenticated
                        }
                        .addOnFailureListener { e ->
                            _authState.value = AuthState.Error("Profile creation failed: ${e.message}")
                        }
                } else {
                    val error = task.exception?.message ?: "Registration failed"
                    _authState.value = AuthState.Error(mapFirebaseError(error))
                }
            }
    }

    fun logout() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }

    private fun mapFirebaseError(error: String): String {
        return when {
            error.contains("badly formatted") -> "Invalid email address."
            error.contains("already in use") -> "Email is already registered."
            error.contains("wrong password") -> "Incorrect password."
            error.contains("user not found") -> "No account found with this email."
            error.contains("network-request-failed") -> "Network error. Please try again."
            error.contains("API key not valid") -> "Firebase API Key is invalid. Please update your google-services.json file in the app directory."
            else -> error
        }
    }
}
