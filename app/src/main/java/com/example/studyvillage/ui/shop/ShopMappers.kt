package com.studyvillage.app.ui.shop

import android.content.Context
import com.example.studyvillage.data.shop.local.ShopItemEntity

fun ShopItemEntity.toUi(context: Context, coins: Long): ShopItem {
    val resId = context.resources.getIdentifier(imageName, "drawable", context.packageName)

    val priceLong = price.toLong()

    return ShopItem(
        id = id,
        name = name,
        imageRes = resId,
        price = priceLong,
        canBuy = coins >= priceLong
    )
}