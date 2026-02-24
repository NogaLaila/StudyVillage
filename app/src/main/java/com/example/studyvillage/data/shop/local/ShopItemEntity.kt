package com.example.studyvillage.data.shop.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shop_items")
data class ShopItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imageName: String,
    val category: String,
    val price: Int
)