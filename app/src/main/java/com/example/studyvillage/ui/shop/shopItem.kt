package com.studyvillage.app.ui.shop

import androidx.annotation.DrawableRes

data class ShopItem(
    val name: String,
    @DrawableRes val imageRes: Int,
    val price: String
)