package com.example.studyvillage.data.shop.local

import android.content.Context
import androidx.room.Room

object DataBaseProvider {

    @Volatile private var INSTANCE: AppDataBase? = null

    fun get(context: Context): AppDataBase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDataBase::class.java,
                "study_village.db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}