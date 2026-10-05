package com.example.calltranslator.repository

import com.example.calltranslator.model.CallHistoryItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CallHistoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val userId: String? get() = auth.currentUser?.uid

    fun getCallHistoryFlow(): Flow<List<CallHistoryItem>> = callbackFlow {
        val uid = userId ?: run {
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users")
            .document(uid)
            .collection("callHistory")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CallHistoryItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveCall(item: CallHistoryItem) {
        val uid = userId ?: return
        firestore.collection("users")
            .document(uid)
            .collection("callHistory")
            .add(item.copy(userId = uid))
            .await()
    }

    suspend fun deleteCall(id: String) {
        val uid = userId ?: return
        firestore.collection("users")
            .document(uid)
            .collection("callHistory")
            .document(id)
            .delete()
            .await()
    }

    suspend fun clearAllCalls() {
        val uid = userId ?: return
        val collection = firestore.collection("users")
            .document(uid)
            .collection("callHistory")
        val docs = collection.get().await()
        for (doc in docs) {
            doc.reference.delete().await()
        }
    }
}
