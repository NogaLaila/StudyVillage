package com.example.studyvillage.data.user

import com.example.studyvillage.data.user.local.UserDao
import com.example.studyvillage.data.user.local.UserEntity
import com.example.studyvillage.data.user.remote.UserRemote

class UserRepository(
    private val userDao: UserDao,
    private val remote: UserRemote
) {

    companion object {
        const val DEFAULT_PROFILE_PHOTO = "default_profile_panda"
    }

    suspend fun getDisplayNameByUid(uid: String): String? {
        val localName = userDao.getUserById(uid)?.name?.trim().orEmpty()
        if (localName.isNotBlank()) return localName

        val remoteUser = remote.getUser(uid) ?: return null
        userDao.insert(remoteUser)
        return remoteUser.name?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun syncUser(
        uid: String,
        email: String?,
        name: String? = null,
        photoUrl: String? = null
    ) {
        val safePhotoUrl = photoUrl ?: DEFAULT_PROFILE_PHOTO
        val remoteUser = remote.getOrCreateUser(uid, email, name, safePhotoUrl)
        userDao.insert(remoteUser)
    }

    suspend fun getLocalUser(uid: String): UserEntity? {
        return userDao.getUserById(uid)
    }

    suspend fun updateUserProfile(
        uid: String,
        name: String,
        email: String,
        photoUrl: String?
    ): UserEntity {
        val baseUser = userDao.getUserById(uid)
            ?: remote.getOrCreateUser(
                uid = uid,
                email = email,
                name = name,
                photoUrl = photoUrl ?: DEFAULT_PROFILE_PHOTO
            )

        val finalPhotoUrl = when {
            !photoUrl.isNullOrBlank() -> photoUrl
            !baseUser.photoUrl.isNullOrBlank() -> baseUser.photoUrl
            else -> DEFAULT_PROFILE_PHOTO
        }

        val updatedUser = baseUser.copy(
            name = name,
            email = email,
            photoUrl = finalPhotoUrl
        )

        remote.updateUserProfile(updatedUser)
        userDao.insert(updatedUser)
        return updatedUser
    }

    suspend fun addCoins(uid: String, amount: Long): Long {
        if (amount <= 0L) {
            return userDao.getUserById(uid)?.coins ?: 0L
        }

        val baseUser = userDao.getUserById(uid)
            ?: remote.getOrCreateUser(
                uid = uid,
                email = null,
                name = null,
                photoUrl = DEFAULT_PROFILE_PHOTO
            ).also {
                userDao.insert(it)
            }

        val newCoins = baseUser.coins + amount

        remote.updateCoins(uid, newCoins)
        userDao.insert(baseUser.copy(coins = newCoins))

        return newCoins
    }

    suspend fun clearLocalUsers() {
        userDao.clearAll()
    }
}