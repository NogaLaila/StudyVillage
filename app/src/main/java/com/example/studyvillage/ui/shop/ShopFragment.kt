package com.studyvillage.app.ui.shop

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.studyvillage.R
import com.example.studyvillage.databinding.FragmentShopBinding
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.example.studyvillage.data.shop.local.DataBaseProvider
import com.example.studyvillage.data.shop.ShopRepository
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.remote.UserRemote
import com.example.studyvillage.data.owned.OwnedRepository
import com.example.studyvillage.data.owned.remote.OwnedRemote
import com.example.studyvillage.util.UserSession
import kotlinx.coroutines.launch

class ShopFragment : Fragment(R.layout.fragment_shop) {

    private var _binding: FragmentShopBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ShopAdapter
    private lateinit var shopRepo: ShopRepository

    private lateinit var userRepo: UserRepository
    private lateinit var ownedRepo: OwnedRepository

    private var currentCategory: String = "Buildings"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentShopBinding.bind(view)

        binding.txtTitle.text = "Shop"
        binding.txtCoins.text = "0"

        val db = DataBaseProvider.get(requireContext())
        val firestore = FirebaseFirestore.getInstance()

        shopRepo = ShopRepository(db.shopDao(), firestore)
        userRepo = UserRepository(db.userDao(), UserRemote())
        ownedRepo = OwnedRepository(db.ownedInstanceDao(), OwnedRemote())

        adapter = ShopAdapter(emptyList()) { item ->
            onBuyClicked(item)
        }

        binding.rvShop.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvShop.setPadding(12, 12, 12, 12)
        binding.rvShop.clipToPadding = false
        binding.rvShop.adapter = adapter

        if (!UserSession.isLoggedIn()) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

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
        currentCategory = category

        viewLifecycleOwner.lifecycleScope.launch {
            val b = _binding ?: return@launch

            val uid = UserSession.currentUid
            val email = UserSession.currentEmail
            val ctx = requireContext()

            if (uid == null) {
                Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val localUser = userRepo.getLocalUser(uid)
            val localCoins = localUser?.coins ?: 0L
            b.txtCoins.text = localCoins.toString()

            val cached = shopRepo.getShopItems(category, forceRefresh = false)
            adapter.submit(cached.map { it.toUi(ctx, localCoins) })

            runCatching { userRepo.syncUser(uid, email) }

            val freshResult = runCatching { shopRepo.getShopItems(category, forceRefresh = true) }

            val updatedCoins = userRepo.getLocalUser(uid)?.coins ?: localCoins
            val b2 = _binding ?: return@launch
            b2.txtCoins.text = updatedCoins.toString()

            freshResult.getOrNull()?.let { fresh ->
                adapter.submit(fresh.map { it.toUi(ctx, updatedCoins) })
            }
        }
    }

    private fun onBuyClicked(item: ShopItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val b = _binding ?: return@launch

            val uid = UserSession.currentUid
            val email = UserSession.currentEmail

            if (uid == null) {
                Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val localUser = userRepo.getLocalUser(uid)
                val localCoins = localUser?.coins ?: 0L
                b.txtCoins.text = localCoins.toString()

                runCatching { userRepo.syncUser(uid, email) }

                val coins = userRepo.getLocalUser(uid)?.coins ?: localCoins

                if (coins < item.price) {
                    Toast.makeText(requireContext(), "Not enough coins", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val newCoins = coins - item.price

                UserRemote().updateCoins(uid, newCoins)
                OwnedRemote().createOwnedInstance(uid, item.id)

                runCatching { userRepo.syncUser(uid, email) }
                runCatching { ownedRepo.syncOwned(uid) }

                val updatedCoins = userRepo.getLocalUser(uid)?.coins ?: newCoins
                val b2 = _binding ?: return@launch
                b2.txtCoins.text = updatedCoins.toString()

                loadCategory(currentCategory)

                Toast.makeText(requireContext(), "Purchased!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Buy failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}