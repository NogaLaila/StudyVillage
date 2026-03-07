package com.example.studyvillage.data.owned.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class OwnedRemote {

    private val db = FirebaseFirestore.getInstance()

    suspend fun fetchOwned(uid: String): List<Map<String, Any?>> {
        val snap = db.collection("users")
            .document(uid)
            .collection("ownedInstances")
            .get()
            .await()

        return snap.documents.map { d ->
            mapOf(
                "instanceId" to d.id,
                "itemId" to d.getString("itemId"),
                "state" to d.getString("state"),
                "x" to d.getLong("x")?.toInt(),
                "y" to d.getLong("y")?.toInt()
            )
        }
    }

    suspend fun place(uid: String, instanceId: String, x: Int, y: Int) {
        db.collection("users")
            .document(uid)
            .collection("ownedInstances")
            .document(instanceId)
            .update(
                mapOf(
                    "state" to "PLACED",
                    "x" to x,
                    "y" to y,
                    "placedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun createOwnedInstance(uid: String, itemId: String) {
        db.collection("users")
            .document(uid)
            .collection("ownedInstances")
            .add(
                mapOf(
                    "itemId" to itemId,
                    "state" to "IN_INVENTORY",
                    "x" to null,
                    "y" to null,
                    "boughtAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun move(uid: String, instanceId: String, x: Int, y: Int) {
        place(uid, instanceId, x, y)
    }

    suspend fun delete(uid: String, instanceId: String) {
        db.collection("users")
            .document(uid)
            .collection("ownedInstances")
            .document(instanceId)
            .delete()
            .await()
    }

    suspend fun unplaceToInventory(uid: String, instanceId: String) {
        db.collection("users")
            .document(uid)
            .collection("ownedInstances")
            .document(instanceId)
            .update(
                mapOf(
                    "state" to "IN_INVENTORY",
                    "x" to null,
                    "y" to null
                )
            )
            .await()
    }
}