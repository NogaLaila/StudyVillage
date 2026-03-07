package com.example.studyvillage.data.user.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val email: String?,
    val name: String? = null,
    val photoUrl: String? = null,
    val coins: Long = 0L
)