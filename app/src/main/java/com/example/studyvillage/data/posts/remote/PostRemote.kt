package com.example.studyvillage.data.posts.remote

import com.example.studyvillage.data.posts.local.PostEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class PostRemote(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun fetchPosts(): List<PostEntity> {
        val snapshot = firestore.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc -> doc.toPostEntity() }
    }

    suspend fun fetchPostsByUser(uid: String): List<PostEntity> {
        val snapshot = firestore.collection("posts")
            .whereEqualTo("createdBy", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc -> doc.toPostEntity() }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toPostEntity(): PostEntity? {
        val title = getString("title")?.trim().orEmpty()
        val content = getString("content")?.trim().orEmpty()
        if (title.isBlank() || content.isBlank()) return null
        return PostEntity(
            id = id,
            title = title,
            content = content,
            image = getString("image")?.trim().orEmpty(),
            createdBy = getString("createdBy")?.trim().orEmpty().ifBlank { "unknown" },
            createdAt = getLong("createdAt") ?: System.currentTimeMillis()
        )
    }

    suspend fun createPost(
        title: String,
        content: String,
        image: String,
        createdBy: String
    ): PostEntity {
        val document = firestore.collection("posts").document()
        val now = System.currentTimeMillis()
        val post = PostEntity(
            id = document.id,
            title = title.trim(),
            content = content.trim(),
            image = image.trim(),
            createdBy = createdBy.trim(),
            createdAt = now
        )

        document.set(
            mapOf(
                "title" to post.title,
                "content" to post.content,
                "image" to post.image,
                "createdBy" to post.createdBy,
                "createdAt" to post.createdAt
            )
        ).await()

        return post
    }
}

