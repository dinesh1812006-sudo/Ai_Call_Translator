package com.example.calltranslator.repository

import com.example.calltranslator.model.FavoriteContact
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FavoritesRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val userId: String? get() = auth.currentUser?.uid

    fun getFavoritesFlow(): Flow<List<FavoriteContact>> = callbackFlow {
        val uid = userId ?: run {
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users")
            .document(uid)
            .collection("favorites")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(FavoriteContact::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addFavorite(name: String, phoneNumber: String) {
        val uid = userId ?: return
        val favorite = FavoriteContact(userId = uid, name = name, phoneNumber = phoneNumber)
        firestore.collection("users")
            .document(uid)
            .collection("favorites")
            .document(phoneNumber)
            .set(favorite)
            .await()
    }

    suspend fun removeFavorite(phoneNumber: String) {
        val uid = userId ?: return
        firestore.collection("users")
            .document(uid)
            .collection("favorites")
            .document(phoneNumber)
            .delete()
            .await()
    }
}
