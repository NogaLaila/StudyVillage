package com.example.studyvillage.ui.village

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studyvillage.databinding.ItemOwnedBinding

data class InventoryUiItem(
    val instanceId: String,
    val itemId: String,
    val title: String,
    val imageRes: Int
)

class InventoryAdapter(
    private val onClick: (InventoryUiItem) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.VH>() {

    private val data = mutableListOf<InventoryUiItem>()

    fun submit(items: List<InventoryUiItem>) {
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOwnedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(data[position])
    override fun getItemCount(): Int = data.size

    class VH(
        private val binding: ItemOwnedBinding,
        private val onClick: (InventoryUiItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InventoryUiItem) {
            binding.imgItem.setImageResource(item.imageRes)
            binding.txtName.text = item.title
            binding.txtCount.text = item.itemId
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}