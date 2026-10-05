package com.example.calltranslator.repository

import com.example.calltranslator.model.TranslationHistoryItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class TranslationHistoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val userId: String? get() = auth.currentUser?.uid

    fun getHistoryFlow(): Flow<List<TranslationHistoryItem>> = callbackFlow {
        val uid = userId ?: run {
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users")
            .document(uid)
            .collection("translationHistory")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(TranslationHistoryItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveTranslation(item: TranslationHistoryItem) {
        val uid = userId ?: return
        if (item.originalText.isBlank() || item.translatedText.isBlank()) return
        
        firestore.collection("users")
            .document(uid)
            .collection("translationHistory")
            .add(item.copy(userId = uid))
            .await()
    }

    suspend fun deleteItem(id: String) {
        val uid = userId ?: return
        firestore.collection("users")
            .document(uid)
            .collection("translationHistory")
            .document(id)
            .delete()
            .await()
    }

    suspend fun clearAllHistory() {
        val uid = userId ?: return
        val collection = firestore.collection("users")
            .document(uid)
            .collection("translationHistory")
        
        val documents = collection.get().await()
        for (doc in documents) {
            doc.reference.delete().await()
        }
    }
}
