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

    suspend fun clearLocalUsers() {
        userDao.clearAll()
    }
}