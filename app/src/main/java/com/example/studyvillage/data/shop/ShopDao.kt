package com.example.studyvillage.data.shop

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.studyvillage.data.shop.local.ShopItemEntity

@Dao
interface ShopDao {

    @Query("SELECT * FROM shop_items WHERE category = :category")
    suspend fun getItemsByCategory(category: String): List<ShopItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShopItemEntity>)

    @Query("DELETE FROM shop_items WHERE category = :category")
    suspend fun clearCategory(category: String): Int
}