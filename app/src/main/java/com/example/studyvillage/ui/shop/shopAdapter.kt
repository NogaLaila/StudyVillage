package com.studyvillage.app.ui.shop

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studyvillage.databinding.ItemShopGridBinding

class ShopAdapter(
    items: List<ShopItem>,
    private val onBuyClick: (ShopItem) -> Unit
) : RecyclerView.Adapter<ShopAdapter.VH>() {

    private val data = items.toMutableList()

    fun submit(newItems: List<ShopItem>) {
        data.clear()
        data.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemShopGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding, onBuyClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    class VH(
        private val binding: ItemShopGridBinding,
        private val onBuyClick: (ShopItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShopItem) {
            binding.imgItem.setImageResource(item.imageRes)
            binding.txtItemName.text = item.name
            binding.txtPrice.text = "${item.price} coins"

            binding.btnBuy.isEnabled = item.canBuy
            binding.btnBuy.alpha = if (item.canBuy) 1f else 0.5f

            binding.btnBuy.setOnClickListener {
                onBuyClick(item)
            }
        }
    }
}