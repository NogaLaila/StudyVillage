package com.example.studyvillage.data.owned.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OwnedInstanceDao {

    @Query("SELECT * FROM owned_instances WHERE uid = :uid AND state = 'PLACED'")
    suspend fun getPlaced(uid: String): List<OwnedInstanceEntity>

    @Query("SELECT * FROM owned_instances WHERE uid = :uid AND state = 'IN_INVENTORY'")
    suspend fun getInventory(uid: String): List<OwnedInstanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<OwnedInstanceEntity>)

    @Query("DELETE FROM owned_instances WHERE uid = :uid")
    suspend fun clearUser(uid: String)

    @Query("SELECT * FROM owned_instances WHERE uid = :uid")
    suspend fun getAllByUid(uid: String): List<OwnedInstanceEntity>

    @Query("""
        UPDATE owned_instances 
        SET x = :x, y = :y, state = :state 
        WHERE instanceId = :instanceId
    """)
    suspend fun updatePlacement(instanceId: String, x: Int, y: Int, state: String)
}