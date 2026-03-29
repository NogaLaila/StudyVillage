package com.example.studyvillage.data.posts

import com.example.studyvillage.data.posts.local.PostEntity
import com.example.studyvillage.data.posts.remote.PostRemote

class PostRepository(
    private val postDao: PostDao,
    private val remote: PostRemote
) {

    suspend fun getCachedPosts(): List<PostEntity> = postDao.getAll()

    suspend fun refreshPosts(): List<PostEntity> {
        val remotePosts = remote.fetchPosts()
        postDao.clearAll()
        postDao.insertAll(remotePosts)
        return postDao.getAll()
    }

    suspend fun addPost(
        title: String,
        content: String,
        image: String
    ): PostEntity {
        val post = remote.createPost(title, content, image)
        postDao.insert(post)
        return post
    }

    suspend fun clearLocalPosts() {
        postDao.clearAll()
    }
}

