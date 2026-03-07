package com.example.studyvillage.data.owned

import com.example.studyvillage.data.owned.local.OwnedInstanceDao
import com.example.studyvillage.data.owned.local.OwnedInstanceEntity
import com.example.studyvillage.data.owned.remote.OwnedRemote

class OwnedRepository(
    private val ownedDao: OwnedInstanceDao,
    private val remote: OwnedRemote
) {

    suspend fun syncOwned(uid: String) {
        val remoteItems = remote.fetchOwned(uid)

        val mapped = remoteItems.map {
            OwnedInstanceEntity(
                instanceId = it["instanceId"] as String,
                uid = uid,
                itemId = it["itemId"] as String,
                state = it["state"] as String,
                x = it["x"] as Int?,
                y = it["y"] as Int?
            )
        }

        ownedDao.clearUser(uid)
        ownedDao.insertAll(mapped)
    }

    suspend fun getPlaced(uid: String) =
        ownedDao.getPlaced(uid)

    suspend fun getInventory(uid: String) =
        ownedDao.getInventory(uid)

    suspend fun place(uid: String, instanceId: String, x: Int, y: Int) {
        remote.place(uid, instanceId, x, y)
        syncOwned(uid)
    }

    suspend fun delete(uid: String, instanceId: String) {
        remote.delete(uid, instanceId)
        syncOwned(uid)
    }

    suspend fun unplaceToInventory(uid: String, instanceId: String) {
        remote.unplaceToInventory(uid, instanceId)
        syncOwned(uid)
    }

}