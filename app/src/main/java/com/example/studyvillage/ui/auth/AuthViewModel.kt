package com.example.studyvillage.ui.auth

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.util.UserSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    application: Application,
    private val userRepo: UserRepository
) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    fun isLoggedIn(): Boolean = UserSession.isLoggedIn()

    fun register(name: String, email: String, password: String, selectedImageUri: Uri?) {
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("User creation failed")

                val finalPhotoValue = if (selectedImageUri != null) {
                    uriToBase64(selectedImageUri)
                } else {
                    UserRepository.DEFAULT_PROFILE_PHOTO
                }

                val profileUpdates = userProfileChangeRequest {
                    displayName = name
                }
                user.updateProfile(profileUpdates).await()

                userRepo.syncUser(
                    uid = user.uid,
                    email = user.email,
                    name = name,
                    photoUrl = finalPhotoValue
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
                    name = user.displayName,
                    photoUrl = null
                )

                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                userRepo.clearLocalUsers()
                auth.signOut()
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Logout failed")
            }
        }
    }

    private fun uriToBase64(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver

        resolver.openInputStream(uri).use { input ->
            val originalBytes = input?.readBytes() ?: throw Exception("Failed to read image")

            val originalBitmap = BitmapFactory.decodeByteArray(
                originalBytes,
                0,
                originalBytes.size
            ) ?: throw Exception("Failed to decode image")

            val outputStream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 35, outputStream)

            val compressedBytes = outputStream.toByteArray()
            return Base64.encodeToString(compressedBytes, Base64.DEFAULT)
        }
    }
}