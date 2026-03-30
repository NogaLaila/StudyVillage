package com.example.studyvillage.data.posts

import com.example.studyvillage.data.posts.local.PostEntity
import com.example.studyvillage.data.posts.remote.PostRemote

class PostRepository(
    private val postDao: PostDao,
    private val remote: PostRemote
) {

    suspend fun getCachedPosts(): List<PostEntity> = postDao.getAll()

    suspend fun getCachedUserPosts(uid: String): List<PostEntity> = postDao.getByCreatedBy(uid)

    suspend fun refreshUserPosts(uid: String): List<PostEntity> {
        val remotePosts = remote.fetchPostsByUser(uid)
        postDao.insertAll(remotePosts)
        return postDao.getByCreatedBy(uid)
    }

    suspend fun refreshPosts(): List<PostEntity> {
        val remotePosts = remote.fetchPosts()
        postDao.clearAll()
        postDao.insertAll(remotePosts)
        return postDao.getAll()
    }

    suspend fun addPost(
        title: String,
        content: String,
        image: String,
        createdBy: String
    ): PostEntity {
        val post = remote.createPost(title, content, image, createdBy)
        postDao.insert(post)
        return post
    }

    suspend fun updatePost(post: PostEntity): PostEntity {
        val updated = remote.updatePost(post)
        postDao.insert(updated)
        return updated
    }

    suspend fun clearLocalPosts() {
        postDao.clearAll()
    }
}
