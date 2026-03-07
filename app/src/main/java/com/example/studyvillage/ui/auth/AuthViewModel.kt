package com.example.studyvillage.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.util.UserSession
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val userRepo: UserRepository
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    fun isLoggedIn(): Boolean = UserSession.isLoggedIn()

    fun register(name: String, email: String, password: String) {
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("User creation failed")

                val profileUpdates =
                    com.google.firebase.auth.userProfileChangeRequest {
                        displayName = name
                    }

                user.updateProfile(profileUpdates).await()

                userRepo.syncUser(
                    uid = user.uid,
                    email = user.email,
                    name = name
                )

                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("Login failed")

                userRepo.syncUser(
                    uid = user.uid,
                    email = user.email,
                    name = user.displayName
                )

                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userRepo.clearLocalUsers()
            auth.signOut()
        }
    }
}