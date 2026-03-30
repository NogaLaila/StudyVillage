package com.example.studyvillage.data.posts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.studyvillage.data.posts.local.PostEntity

@Dao
interface PostDao {

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    suspend fun getAll(): List<PostEntity>

    @Query("SELECT * FROM posts WHERE createdBy = :uid ORDER BY createdAt DESC")
    suspend fun getByCreatedBy(uid: String): List<PostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: PostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    @Query("DELETE FROM posts")
    suspend fun clearAll(): Int
}

