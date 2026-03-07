package com.example.studyvillage.ui.village

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.studyvillage.data.owned.OwnedRepository
import com.example.studyvillage.data.shop.ShopDao
import com.example.studyvillage.data.user.UserRepository

class MyVillageViewModelFactory(
    private val userRepo: UserRepository,
    private val ownedRepo: OwnedRepository,
    private val shopDao: ShopDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyVillageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyVillageViewModel(userRepo, ownedRepo, shopDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}