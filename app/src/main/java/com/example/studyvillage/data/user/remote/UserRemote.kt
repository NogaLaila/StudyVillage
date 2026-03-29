package com.example.studyvillage.data.user.remote

import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.local.UserEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRemote {

    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    suspend fun getUser(uid: String): UserEntity? {
        val snapshot = usersCollection.document(uid).get().await()
        if (!snapshot.exists()) return null

        return UserEntity(
            uid = uid,
            email = snapshot.getString("email"),
            name = snapshot.getString("name"),
            photoUrl = snapshot.getString("photoUrl") ?: UserRepository.DEFAULT_PROFILE_PHOTO,
            coins = snapshot.getLong("coins") ?: 0L
        )
    }

    suspend fun getOrCreateUser(
        uid: String,
        email: String?,
        name: String?,
        photoUrl: String?
    ): UserEntity {
        val existing = getUser(uid)

        if (existing != null) {
            val updated = existing.copy(
                email = email ?: existing.email,
                name = name ?: existing.name,
                photoUrl = photoUrl ?: existing.photoUrl ?: UserRepository.DEFAULT_PROFILE_PHOTO
            )

            usersCollection.document(uid).set(
                mapOf(
                    "uid" to updated.uid,
                    "email" to updated.email,
                    "name" to updated.name,
                    "photoUrl" to updated.photoUrl,
                    "coins" to updated.coins
                )
            ).await()

            return updated
        }

        val newUser = UserEntity(
            uid = uid,
            email = email,
            name = name,
            photoUrl = photoUrl ?: UserRepository.DEFAULT_PROFILE_PHOTO,
            coins = 0L
        )

        usersCollection.document(uid).set(
            mapOf(
                "uid" to newUser.uid,
                "email" to newUser.email,
                "name" to newUser.name,
                "photoUrl" to newUser.photoUrl,
                "coins" to newUser.coins
            )
        ).await()

        return newUser
    }

    suspend fun updateCoins(uid: String, newCoins: Long) {
        usersCollection.document(uid)
            .update("coins", newCoins)
            .await()
    }
}