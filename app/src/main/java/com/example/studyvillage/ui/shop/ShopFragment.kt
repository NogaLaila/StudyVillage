package com.studyvillage.app.ui.shop

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.studyvillage.R
import com.example.studyvillage.databinding.FragmentShopBinding
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.example.studyvillage.data.local.DatabaseProvider
import com.example.studyvillage.data.shop.ShopRepository
import kotlinx.coroutines.launch

class ShopFragment : Fragment(R.layout.fragment_shop) {

    private var _binding: FragmentShopBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ShopAdapter
    private lateinit var repository: ShopRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentShopBinding.bind(view)

        binding.txtTitle.text = "Shop"
        binding.txtCoins.text = "1"

        val db = DatabaseProvider.get(requireContext())
        repository = ShopRepository(db.shopDao(), FirebaseFirestore.getInstance())

        adapter = ShopAdapter(emptyList())
        binding.rvShop.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvShop.setPadding(12, 12, 12, 12)
        binding.rvShop.clipToPadding = false
        binding.rvShop.adapter = adapter

        loadCategory("Buildings")

        binding.shopTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val category = when (tab.position) {
                    0 -> "Buildings"
                    1 -> "Decor"
                    else -> "Pandas"
                }
                loadCategory(category)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun loadCategory(category: String) {
        lifecycleScope.launch {
            val ctx = requireContext()

            // 1) show cached immediately
            val cached = repository.getShopItems(category, forceRefresh = false)
            adapter.submit(cached.map { it.toUi(ctx) })

            // 2) refresh from firestore and update UI
            val fresh = repository.getShopItems(category, forceRefresh = true)
            adapter.submit(fresh.map { it.toUi(ctx) })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}