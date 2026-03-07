package com.example.studyvillage.util

import com.google.firebase.auth.FirebaseAuth

object UserSession {

    val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    val currentEmail: String?
        get() = FirebaseAuth.getInstance().currentUser?.email

    val currentName: String?
        get() = FirebaseAuth.getInstance().currentUser?.displayName

    fun isLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    fun logout() {
        FirebaseAuth.getInstance().signOut()
    }
}