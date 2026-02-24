package com.studyvillage.app.ui.shop

import android.content.Context
import com.example.studyvillage.R
import com.example.studyvillage.data.shop.local.ShopItemEntity

fun ShopItemEntity.toUi(context: Context): ShopItem {
    val resId = context.resources.getIdentifier(
        imageName,
        "drawable",
        context.packageName
    )

    return ShopItem(
        name = name,
        imageRes = if (resId != 0) resId else R.drawable.small_house,
        price = price.toString()
    )
}