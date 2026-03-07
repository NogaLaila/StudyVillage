package com.example.studyvillage.data.owned.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "owned_instances")
data class OwnedInstanceEntity(
    @PrimaryKey val instanceId: String,
    val uid: String,
    val itemId: String,
    val state: String,
    val x: Int?,
    val y: Int?
)