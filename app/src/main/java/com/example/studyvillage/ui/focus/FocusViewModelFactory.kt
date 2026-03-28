package com.example.studyvillage.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.studyvillage.data.user.UserRepository

class FocusViewModelFactory(
    private val userRepo: UserRepository,
    private val uid: String?,
    private val email: String?
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
            return FocusViewModel(userRepo, uid, email) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

