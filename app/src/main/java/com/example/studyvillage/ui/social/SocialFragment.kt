package com.example.studyvillage.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studyvillage.R
import com.example.studyvillage.data.posts.PostRepository
import com.example.studyvillage.data.posts.remote.PostRemote
import com.example.studyvillage.data.shop.local.DataBaseProvider
import com.example.studyvillage.databinding.DialogCreatePostBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SocialFragment : Fragment(R.layout.fragment_social) {

	private var postsView: RecyclerView? = null
	private var emptyStateView: TextView? = null
	private var progressView: ProgressBar? = null
	private var createPostButton: MaterialButton? = null

	private lateinit var postRepository: PostRepository
	private val postAdapter = PostAdapter()

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		postsView = view.findViewById(R.id.rvPosts)
		emptyStateView = view.findViewById(R.id.tvEmptySocial)
		progressView = view.findViewById(R.id.progressSocial)
		createPostButton = view.findViewById(R.id.btnCreatePost)

		val db = DataBaseProvider.get(requireContext())
		postRepository = PostRepository(db.postDao(), PostRemote())

		postsView?.layoutManager = LinearLayoutManager(requireContext())
		postsView?.adapter = postAdapter

		createPostButton?.setOnClickListener {
			showCreatePostDialog()
		}

		loadPosts()
	}

	private fun loadPosts() {
		viewLifecycleOwner.lifecycleScope.launch {
			progressView?.visibility = View.VISIBLE

			val cachedPosts = runCatching { postRepository.getCachedPosts() }
				.getOrDefault(emptyList())
			postAdapter.submit(cachedPosts)
			emptyStateView?.visibility = if (cachedPosts.isEmpty()) View.VISIBLE else View.GONE

			runCatching { postRepository.refreshPosts() }
				.onSuccess { freshPosts ->
					postAdapter.submit(freshPosts)
					emptyStateView?.visibility = if (freshPosts.isEmpty()) View.VISIBLE else View.GONE
				}
				.onFailure {
					Toast.makeText(requireContext(), R.string.social_load_failed, Toast.LENGTH_SHORT).show()
				}

			progressView?.visibility = View.GONE
		}
	}

	private fun showCreatePostDialog() {
		val dialogBinding = DialogCreatePostBinding.inflate(LayoutInflater.from(requireContext()))
		val dialog = MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.social_create_post_title)
			.setView(dialogBinding.root)
			.setNegativeButton(R.string.social_cancel_action, null)
			.setPositiveButton(R.string.social_post_action, null)
			.create()

		dialog.setOnShowListener {
			val positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
			positiveButton.setOnClickListener {
				val title = dialogBinding.etTitle.text?.toString()?.trim().orEmpty()
				val content = dialogBinding.etContent.text?.toString()?.trim().orEmpty()
				val image = dialogBinding.etImage.text?.toString()?.trim().orEmpty()

				dialogBinding.inputTitle.error = null
				dialogBinding.inputContent.error = null
				dialogBinding.inputImage.error = null

				var hasError = false
				if (title.isBlank()) {
					dialogBinding.inputTitle.error = getString(R.string.social_field_required)
					hasError = true
				}
				if (content.isBlank()) {
					dialogBinding.inputContent.error = getString(R.string.social_field_required)
					hasError = true
				}
				if (image.isBlank()) {
					dialogBinding.inputImage.error = getString(R.string.social_field_required)
					hasError = true
				}

				if (hasError) return@setOnClickListener

				positiveButton.isEnabled = false
				viewLifecycleOwner.lifecycleScope.launch {
					runCatching { postRepository.addPost(title, content, image) }
						.onSuccess {
							val posts = runCatching { postRepository.getCachedPosts() }
								.getOrDefault(emptyList())
							postAdapter.submit(posts)
							emptyStateView?.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
							Toast.makeText(requireContext(), R.string.social_post_added, Toast.LENGTH_SHORT).show()
							dialog.dismiss()
						}
						.onFailure {
							Toast.makeText(requireContext(), R.string.social_post_failed, Toast.LENGTH_SHORT).show()
							positiveButton.isEnabled = true
						}
				}
			}
		}

		dialog.show()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		postsView = null
		emptyStateView = null
		progressView = null
		createPostButton = null
	}
}
