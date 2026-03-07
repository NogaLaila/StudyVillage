package com.example.studyvillage.data.shop.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.studyvillage.data.shop.ShopDao
import com.example.studyvillage.data.shop.local.ShopItemEntity
import com.example.studyvillage.data.owned.local.OwnedInstanceDao
import com.example.studyvillage.data.owned.local.OwnedInstanceEntity
import com.example.studyvillage.data.user.local.UserDao
import com.example.studyvillage.data.user.local.UserEntity

@Database(
    entities = [
        ShopItemEntity::class,
        UserEntity::class,
        OwnedInstanceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDataBase : RoomDatabase() {

    abstract fun shopDao(): ShopDao

    abstract fun userDao(): UserDao
    abstract fun ownedInstanceDao(): OwnedInstanceDao
}