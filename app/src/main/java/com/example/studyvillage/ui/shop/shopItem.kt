package com.studyvillage.app.ui.shop

data class ShopItem(
    val id: String,
    val name: String,
    val imageRes: Int,
    val price: Long,
    val canBuy: Boolean
)