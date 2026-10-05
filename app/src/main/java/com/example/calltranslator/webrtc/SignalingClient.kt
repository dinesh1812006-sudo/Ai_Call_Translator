package com.example.calltranslator.webrtc

import android.util.Log
import com.example.calltranslator.model.CallInvite
import com.example.calltranslator.util.PhoneNumberUtils
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SignalingClient(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val registrations = mutableListOf<ListenerRegistration>()

    fun createRoom(roomId: String, offer: SessionDescription, onAnswerReceived: (SessionDescription) -> Unit, onIceCandidateReceived: (IceCandidate) -> Unit) {
        val roomRef = db.collection("calls").document(roomId)
        
        val data = hashMapOf(
            "offer" to hashMapOf(
                "sdp" to offer.description,
                "type" to offer.type.canonicalForm()
            )
        )
        
        roomRef.set(data)
        
        // Listen for Answer
        val answerListener = roomRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            
            val answer = snapshot.get("answer") as? Map<String, String>
            if (answer != null) {
                val sdp = answer["sdp"] ?: ""
                val type = SessionDescription.Type.fromCanonicalForm(answer["type"] ?: "answer")
                onAnswerReceived(SessionDescription(type, sdp))
            }
        }
        registrations.add(answerListener)
        
        // Listen for Remote ICE candidates (from Callee)
        val candidateListener = roomRef.collection("calleeCandidates").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            for (change in snapshot.documentChanges) {
                if (change.type == DocumentChange.Type.ADDED) {
                    val data = change.document.data
                    val candidate = IceCandidate(
                        data["sdpMid"] as String,
                        (data["sdpMLineIndex"] as Long).toInt(),
                        data["candidate"] as String
                    )
                    onIceCandidateReceived(candidate)
                }
            }
        }
        registrations.add(candidateListener)
    }

    fun joinRoom(roomId: String, onOfferReceived: (SessionDescription) -> Unit, onIceCandidateReceived: (IceCandidate) -> Unit) {
        val roomRef = db.collection("calls").document(roomId)
        
        roomRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val offer = snapshot.get("offer") as? Map<String, String>
                if (offer != null) {
                    val sdp = offer["sdp"] ?: ""
                    val type = SessionDescription.Type.fromCanonicalForm(offer["type"] ?: "offer")
                    onOfferReceived(SessionDescription(type, sdp))
                }
            }
        }
        
        // Listen for Remote ICE candidates (from Caller)
        val candidateListener = roomRef.collection("callerCandidates").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            for (change in snapshot.documentChanges) {
                if (change.type == DocumentChange.Type.ADDED) {
                    val data = change.document.data
                    val candidate = IceCandidate(
                        data["sdpMid"] as String,
                        (data["sdpMLineIndex"] as Long).toInt(),
                        data["candidate"] as String
                    )
                    onIceCandidateReceived(candidate)
                }
            }
        }
        registrations.add(candidateListener)
    }

    fun sendAnswer(roomId: String, answer: SessionDescription) {
        val roomRef = db.collection("calls").document(roomId)
        val data = hashMapOf(
            "answer" to hashMapOf(
                "sdp" to answer.description,
                "type" to answer.type.canonicalForm()
            )
        )
        roomRef.update(data as Map<String, Any>)
    }

    fun sendIceCandidate(roomId: String, candidate: IceCandidate, isCaller: Boolean) {
        val collectionName = if (isCaller) "callerCandidates" else "calleeCandidates"
        val data = hashMapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "candidate" to candidate.sdp
        )
        db.collection("calls").document(roomId).collection(collectionName).add(data)
    }

    fun sendTranslation(roomId: String, text: String, isCaller: Boolean) {
        Log.d("AudioDebug", "Signaling: Sending translation: $text (from caller: $isCaller)")
        val data = hashMapOf(
            "text" to text,
            "senderIsCaller" to isCaller,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("calls").document(roomId).collection("translations").add(data)
    }

    fun updateHearingLanguage(roomId: String, languageName: String, isCaller: Boolean) {
        val field = if (isCaller) "callerHearingLang" else "calleeHearingLang"
        db.collection("calls").document(roomId).update(field, languageName)
    }

    fun listenForHearingLanguages(roomId: String, onUpdate: (callerLang: String, calleeLang: String) -> Unit) {
        val listener = db.collection("calls").document(roomId).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val callerLang = snapshot.getString("callerHearingLang") ?: "English"
            val calleeLang = snapshot.getString("calleeHearingLang") ?: "Tamil"
            onUpdate(callerLang, calleeLang)
        }
        registrations.add(listener)
    }

    fun listenForTranslations(roomId: String, onTranslationReceived: (String, Boolean) -> Unit) {
        val translationListener = db.collection("calls").document(roomId).collection("translations")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val text = data["text"] as? String ?: ""
                        val senderIsCaller = data["senderIsCaller"] as? Boolean ?: false
                        Log.d("AudioDebug", "Signaling: Incoming translation: $text (from caller: $senderIsCaller)")
                        if (text.isNotEmpty()) {
                            onTranslationReceived(text, senderIsCaller)
                        }
                    }
                }
            }
        registrations.add(translationListener)
    }
    
    fun clearListeners() {
        for (reg in registrations) {
            reg.remove()
        }
        registrations.clear()
    }

    fun endCall(roomId: String) {
        clearListeners()
        db.collection("calls").document(roomId).delete()
    }

    // --- CALL INVITATION LOGIC ---

    fun checkUserRegistered(phoneNumber: String, onResult: (Boolean) -> Unit) {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        
        if (normalized.length < 10) {
            Log.w("RegistrationCheck", "Invalid phone number for check: $phoneNumber")
            onResult(false)
            return
        }

        Log.d("RegistrationCheck", "Searching Firestore for: $normalized")
        
        db.collection("users")
            .whereEqualTo("phoneNumber", normalized)
            .get()
            .addOnSuccessListener { snapshot ->
                val exists = !snapshot.isEmpty
                Log.d("RegistrationCheck", "Found ${snapshot.size()} users with number $normalized. Registration status: $exists")
                onResult(exists)
            }
            .addOnFailureListener { e ->
                Log.e("RegistrationCheck", "Firestore query failed for $normalized: ${e.message}")
                onResult(false)
            }
    }

    fun sendCallInvite(invite: CallInvite) {
        db.collection("invites").document(invite.id).set(invite)
    }

    fun listenForIncomingCalls(myUid: String, myNumber: String, onInvite: (CallInvite) -> Unit) {
        val listener = db.collection("invites")
            .whereEqualTo("targetNumber", myNumber)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val invite = change.document.toObject(CallInvite::class.java).copy(id = change.document.id)
                        db.collection("users").document(myUid).collection("blockedContacts")
                            .document(PhoneNumberUtils.normalize(invite.callerNumber))
                            .get()
                            .addOnSuccessListener { blockedDoc ->
                                if (blockedDoc.exists() && blockedDoc.getBoolean("blocked") == true) {
                                    updateInviteStatus(invite.id, "DECLINED")
                                    Log.d("BlockCheck", "Blocked incoming call from ${invite.callerNumber}")
                                } else {
                                    onInvite(invite)
                                }
                            }
                    }
                }
            }
        registrations.add(listener)
    }

    fun blockContact(myUid: String, phoneNumber: String, onComplete: (Boolean) -> Unit) {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        db.collection("users").document(myUid).collection("blockedContacts").document(normalized)
            .set(hashMapOf("blocked" to true, "phoneNumber" to normalized))
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun unblockContact(myUid: String, phoneNumber: String, onComplete: (Boolean) -> Unit) {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        db.collection("users").document(myUid).collection("blockedContacts").document(normalized)
            .delete()
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun checkContactBlocked(myUid: String, phoneNumber: String, onResult: (Boolean) -> Unit) {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        db.collection("users").document(myUid).collection("blockedContacts").document(normalized)
            .get()
            .addOnSuccessListener { doc ->
                onResult(doc.exists() && doc.getBoolean("blocked") == true)
            }
            .addOnFailureListener { onResult(false) }
    }

    fun updateInviteStatus(roomId: String, status: String) {
        db.collection("invites").document(roomId).update("status", status)
    }

    fun listenForInviteStatus(roomId: String, onStatusChange: (String) -> Unit) {
        val listener = db.collection("invites").document(roomId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val status = snapshot.getString("status") ?: "PENDING"
                onStatusChange(status)
            }
        registrations.add(listener)
    }
}
