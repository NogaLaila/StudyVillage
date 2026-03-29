package com.example.studyvillage.ui.auth

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.studyvillage.data.user.UserRepository

class AuthViewModelFactory(
    private val application: Application,
    private val userRepo: UserRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            return AuthViewModel(application, userRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}