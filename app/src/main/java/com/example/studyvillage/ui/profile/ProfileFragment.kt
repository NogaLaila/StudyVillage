package com.example.studyvillage.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.studyvillage.R
import com.example.studyvillage.data.shop.local.DataBaseProvider
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.remote.UserRemote
import com.example.studyvillage.util.UserSession
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var tvName: TextView? = null
    private var tvHandle: TextView? = null
    private var tvEmail: TextView? = null
    private var tvCoins: TextView? = null
    private var tvTopCoins: TextView? = null
    private var ivAvatar: ImageView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvName = view.findViewById(R.id.tvProfileName)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        tvCoins = view.findViewById(R.id.tvProfileCoins)
        tvTopCoins = view.findViewById(R.id.txtCoins)
        ivAvatar = view.findViewById(R.id.ivProfileAvatar)

        loadProfile()
    }

    private fun loadProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = UserSession.currentUid ?: return@launch
            val email = UserSession.currentEmail
            val displayName = UserSession.currentName

            val db = DataBaseProvider.get(requireContext())
            val userRepo = UserRepository(db.userDao(), UserRemote())

            // 1. Read local DB immediately (no network, no flash)
            val localUser = userRepo.getLocalUser(uid)
            val name = localUser?.name?.takeIf { it.isNotBlank() }
                ?: displayName
                ?: "Adventurer"

            tvEmail?.text = email?.takeIf { it.isNotBlank() } ?: "—"
            tvName?.text = name
            tvHandle?.text = "@${name.replace(" ", "").lowercase()}"
            localUser?.coins?.let { coins ->
                tvCoins?.text = coins.toString()
                tvTopCoins?.text = coins.toString()
            }

            // 2. Sync with remote in the background; update only if coins changed
            runCatching { userRepo.syncUser(uid, email, displayName) }
            val refreshed = userRepo.getLocalUser(uid)
            if (refreshed != null && refreshed.coins != localUser?.coins) {
                tvCoins?.text = refreshed.coins.toString()
                tvTopCoins?.text = refreshed.coins.toString()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvName = null
        tvHandle = null
        tvEmail = null
        tvCoins = null
        tvTopCoins = null
        ivAvatar = null
    }
}

