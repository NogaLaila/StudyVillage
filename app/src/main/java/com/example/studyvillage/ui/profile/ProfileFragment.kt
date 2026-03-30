package com.example.studyvillage.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studyvillage.R
import com.example.studyvillage.data.posts.PostRepository
import com.example.studyvillage.data.posts.remote.PostRemote
import com.example.studyvillage.data.shop.local.DataBaseProvider
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.remote.UserRemote
import com.example.studyvillage.ui.social.PostAdapter
import com.example.studyvillage.util.UserSession
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var tvName: TextView? = null
    private var tvHandle: TextView? = null
    private var tvEmail: TextView? = null
    private var tvCoins: TextView? = null
    private var tvTopCoins: TextView? = null
    private var ivAvatar: ImageView? = null
    private var rvMyPosts: RecyclerView? = null
    private var tvEmptyMyPosts: TextView? = null
    private var progressMyPosts: ProgressBar? = null

    private val postAdapter = PostAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvName = view.findViewById(R.id.tvProfileName)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        tvCoins = view.findViewById(R.id.tvProfileCoins)
        tvTopCoins = view.findViewById(R.id.txtCoins)
        ivAvatar = view.findViewById(R.id.ivProfileAvatar)
        rvMyPosts = view.findViewById(R.id.rvMyPosts)
        tvEmptyMyPosts = view.findViewById(R.id.tvEmptyMyPosts)
        progressMyPosts = view.findViewById(R.id.progressMyPosts)

        rvMyPosts?.layoutManager = LinearLayoutManager(requireContext())
        rvMyPosts?.adapter = postAdapter

        loadProfile()
        loadMyPosts()
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

    private fun loadMyPosts() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = UserSession.currentUid ?: return@launch

            val db = DataBaseProvider.get(requireContext())
            val postRepo = PostRepository(db.postDao(), PostRemote())
            val userRepo = UserRepository(db.userDao(), UserRemote())

            // Resolve the current user's display name once
            val localUser = userRepo.getLocalUser(uid)
            val displayName = localUser?.name?.takeIf { it.isNotBlank() }
                ?: UserSession.currentName
                ?: "Adventurer"

            fun resolveNames(posts: List<com.example.studyvillage.data.posts.local.PostEntity>) =
                posts.map { it.copy(createdBy = displayName) }

            // Show cached posts immediately
            val cached = runCatching { postRepo.getCachedUserPosts(uid) }.getOrDefault(emptyList())
            postAdapter.submit(resolveNames(cached))
            tvEmptyMyPosts?.isVisible = cached.isEmpty()

            // Refresh from remote
            progressMyPosts?.isVisible = true
            runCatching { postRepo.refreshUserPosts(uid) }
                .onSuccess { fresh ->
                    postAdapter.submit(resolveNames(fresh))
                    tvEmptyMyPosts?.isVisible = fresh.isEmpty()
                }
            progressMyPosts?.isVisible = false
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
        rvMyPosts = null
        tvEmptyMyPosts = null
        progressMyPosts = null
    }
}
