package com.example.studyvillage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.studyvillage.data.shop.ShopDao
import com.example.studyvillage.data.shop.local.ShopItemEntity

@Database(
    entities = [ShopItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shopDao(): ShopDao
}