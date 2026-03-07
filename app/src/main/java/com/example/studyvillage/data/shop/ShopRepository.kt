package com.example.studyvillage.data.shop

import com.google.firebase.firestore.FirebaseFirestore
import com.example.studyvillage.data.shop.ShopDao
import com.example.studyvillage.data.shop.local.ShopItemEntity
import com.example.studyvillage.data.shop.remote.ShopItemRemote
import kotlinx.coroutines.tasks.await

class ShopRepository(
    private val dao: ShopDao,
    private val firestore: FirebaseFirestore
) {
    suspend fun getShopItems(category: String, forceRefresh: Boolean = true): List<ShopItemEntity> {
        val cached = dao.getItemsByCategory(category)

        if (cached.isNotEmpty() && !forceRefresh) return cached

        val snap = firestore.collection("shop_items")
            .whereEqualTo("category", category)
            .get()
            .await()
        val remoteItems = snap.documents.mapNotNull { doc ->
            val remote = doc.toObject(ShopItemRemote::class.java) ?: return@mapNotNull null
            ShopItemEntity(
                id = doc.id,
                name = remote.name,
                imageName = remote.imageName,
                category = remote.category,
                price = remote.price
            )
        }

        dao.clearCategory(category)
        dao.insertAll(remoteItems)

        return remoteItems
    }
}