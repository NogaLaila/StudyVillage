package com.example.studyvillage.ui.village

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MyVillageViewModel : ViewModel() {

    private val _coins = MutableLiveData(1)
    val coins: LiveData<Int> = _coins

    // later: load from Room / Firebase
    fun setCoins(value: Int) {
        _coins.value = value
    }
}
