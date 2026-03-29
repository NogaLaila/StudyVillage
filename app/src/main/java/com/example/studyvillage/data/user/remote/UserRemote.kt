package com.example.studyvillage.data.user.remote

import com.example.studyvillage.data.user.local.UserEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRemote(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun getUser(uid: String): UserEntity? {
        val snap = firestore.collection("users").document(uid).get().await()
        if (!snap.exists()) return null

        val coins = snap.getLong("coins") ?: 0L
        val remoteName = snap.getString("name")
        val photoUrl = snap.getString("photoUrl")
        val email = snap.getString("email")

        return UserEntity(
            uid = uid,
            email = email,
            name = remoteName,
            photoUrl = photoUrl,
            coins = coins
        )
    }

    suspend fun getOrCreateUser(
        uid: String,
        email: String?,
        name: String?
    ): UserEntity {
        val docRef = firestore.collection("users").document(uid)
        val snap = docRef.get().await()

        return if (snap.exists()) {
            val coins = snap.getLong("coins") ?: 0L
            val remoteName = snap.getString("name")
            val photoUrl = snap.getString("photoUrl")
            UserEntity(
                uid = uid,
                email = email,
                name = remoteName ?: name,
                photoUrl = photoUrl,
                coins = coins
            )
        } else {
            val newUser = hashMapOf(
                "uid" to uid,
                "email" to email,
                "name" to name,
                "photoUrl" to null,
                "coins" to 0L
            )
            docRef.set(newUser).await()

            UserEntity(
                uid = uid,
                email = email,
                name = name,
                photoUrl = null,
                coins = 0L
            )
        }
    }

    suspend fun updateCoins(uid: String, newCoins: Long) {
        firestore.collection("users")
            .document(uid)
            .update("coins", newCoins)
            .await()
    }
}