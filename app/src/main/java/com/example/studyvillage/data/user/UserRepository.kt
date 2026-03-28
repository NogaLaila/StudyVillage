package com.example.studyvillage.data.user

import com.example.studyvillage.data.user.local.UserDao
import com.example.studyvillage.data.user.local.UserEntity
import com.example.studyvillage.data.user.remote.UserRemote

class UserRepository(
    private val userDao: UserDao,
    private val remote: UserRemote
) {

    suspend fun syncUser(uid: String, email: String?, name: String? = null) {
        val remoteUser = remote.getOrCreateUser(uid, email, name)
        userDao.insert(remoteUser)
    }

    suspend fun getLocalUser(uid: String): UserEntity? {
        return userDao.getUserById(uid)
    }

    suspend fun addCoins(uid: String, amount: Long): Long {
        if (amount <= 0L) {
            return userDao.getUserById(uid)?.coins ?: 0L
        }

        val baseUser = userDao.getUserById(uid) ?: remote.getOrCreateUser(uid, null, null).also {
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