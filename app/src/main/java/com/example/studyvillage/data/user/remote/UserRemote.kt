package com.example.studyvillage.data.user.remote

import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.local.UserEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
            val existingEmail = existing.email?.trim().orEmpty()
            val existingName = existing.name?.trim().orEmpty()
            val existingPhotoUrl = existing.photoUrl?.trim().orEmpty()

            val updated = existing.copy(
                email = if (existingEmail.isNotBlank()) existing.email else email,
                name = if (existingName.isNotBlank()) existing.name else name,
                photoUrl = when {
                    existingPhotoUrl.isNotBlank() -> existing.photoUrl
                    !photoUrl.isNullOrBlank() -> photoUrl
                    else -> UserRepository.DEFAULT_PROFILE_PHOTO
                }
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

    suspend fun updateUserProfile(user: UserEntity) {
        usersCollection.document(user.uid)
            .set(
                mapOf(
                    "email" to user.email,
                    "name" to user.name,
                    "photoUrl" to user.photoUrl,
                    "coins" to user.coins
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun updateCoins(uid: String, newCoins: Long) {
        usersCollection.document(uid)
            .update("coins", newCoins)
            .await()
    }
}