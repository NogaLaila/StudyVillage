package com.example.studyvillage.ui.social

import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
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
import com.example.studyvillage.databinding.DialogCreatePostBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.studyvillage.util.UserSession
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.max

class SocialFragment : Fragment(R.layout.fragment_social) {
	private companion object {
		const val TAG = "SocialFragment"
		const val MAX_IMAGE_DIMENSION = 1080
		const val MAX_BASE64_CHARS = 700_000
		val UID_PATTERN = Regex("^[A-Za-z0-9]{20,}$")
	}

	private var postsView: RecyclerView? = null
	private var emptyStateView: TextView? = null
	private var progressView: ProgressBar? = null
	private var coinsView: TextView? = null
	private var createPostButton: MaterialButton? = null

	private lateinit var postRepository: PostRepository
	private lateinit var userRepository: UserRepository
	private val postAdapter = PostAdapter()
	private var activeDialogBinding: DialogCreatePostBinding? = null
	private var isImageUploading = false
	private var pendingPickedImageBase64: String? = null

	private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
		if (uri == null) {
			Log.d(TAG, "No image selected")
			return@registerForActivityResult
		}
		Log.d(TAG, "Image selected: $uri")
		uploadPickedImage(uri)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		postsView = view.findViewById(R.id.rvPosts)
		emptyStateView = view.findViewById(R.id.tvEmptySocial)
		progressView = view.findViewById(R.id.progressSocial)
		coinsView = view.findViewById(R.id.txtCoins)
		createPostButton = view.findViewById(R.id.btnCreatePost)

		val db = DataBaseProvider.get(requireContext())
		postRepository = PostRepository(db.postDao(), PostRemote())
		userRepository = UserRepository(db.userDao(), UserRemote())

		postsView?.layoutManager = LinearLayoutManager(requireContext())
		postsView?.adapter = postAdapter

		createPostButton?.setOnClickListener {
			showCreatePostDialog()
		}

		loadCoins()
		loadPosts()
	}

	private fun loadCoins() {
		viewLifecycleOwner.lifecycleScope.launch {
			val uid = UserSession.currentUid ?: return@launch
			val email = UserSession.currentEmail

			val localCoins = userRepository.getLocalUser(uid)?.coins ?: 0L
			coinsView?.text = localCoins.toString()

			runCatching { userRepository.syncUser(uid, email, UserSession.currentName) }
			val updatedCoins = userRepository.getLocalUser(uid)?.coins ?: localCoins
			coinsView?.text = updatedCoins.toString()
		}
	}

	private fun loadPosts() {
		viewLifecycleOwner.lifecycleScope.launch {
			progressView?.visibility = View.VISIBLE

			val cachedPosts = runCatching { postRepository.getCachedPosts() }
				.getOrDefault(emptyList())
			postAdapter.submit(resolvePostsForDisplay(cachedPosts))
			emptyStateView?.visibility = if (cachedPosts.isEmpty()) View.VISIBLE else View.GONE

			runCatching { postRepository.refreshPosts() }
				.onSuccess { freshPosts ->
					postAdapter.submit(resolvePostsForDisplay(freshPosts))
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
			.setView(dialogBinding.root)
			.create()
		activeDialogBinding = dialogBinding
		pendingPickedImageBase64 = null

		dialog.setOnDismissListener {
			activeDialogBinding = null
			isImageUploading = false
			pendingPickedImageBase64 = null
		}

		dialog.setOnShowListener {
			dialogBinding.ivSparkleLeft.apply {
				scaleX = 0.7f
				scaleY = 0.7f
				alpha = 0.2f
				animate()
					.alpha(0.85f)
					.scaleX(1f)
					.scaleY(1f)
					.setDuration(260)
					.start()
			}
			dialogBinding.ivSparkleRight.apply {
				scaleX = 0.7f
				scaleY = 0.7f
				alpha = 0.2f
				animate()
					.alpha(0.75f)
					.scaleX(1f)
					.scaleY(1f)
					.setStartDelay(90)
					.setDuration(260)
					.start()
			}
		}

		dialogBinding.btnCancelPost.setOnClickListener {
			dialog.dismiss()
		}

		dialogBinding.btnPickImage.setOnClickListener {
			pickImageLauncher.launch(
				PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
			)
		}

		dialogBinding.btnSubmitPost.setOnClickListener {
			val title = dialogBinding.etTitle.text?.toString()?.trim().orEmpty()
			val content = dialogBinding.etContent.text?.toString()?.trim().orEmpty()
			val typedImage = dialogBinding.etImage.text?.toString()?.trim().orEmpty()
			val image = if (typedImage.isNotBlank()) typedImage else pendingPickedImageBase64.orEmpty()
			val currentUid = UserSession.currentUid

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

			if (hasError) return@setOnClickListener
			if (currentUid.isNullOrBlank()) {
				Toast.makeText(requireContext(), R.string.social_post_failed, Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}
			if (isImageUploading) {
				Toast.makeText(requireContext(), R.string.social_image_uploading_wait, Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}

			dialogBinding.btnSubmitPost.isEnabled = false
			viewLifecycleOwner.lifecycleScope.launch {
				runCatching { postRepository.addPost(title, content, image, currentUid) }
					.onSuccess {
						val posts = runCatching { postRepository.getCachedPosts() }
							.getOrDefault(emptyList())
						postAdapter.submit(resolvePostsForDisplay(posts))
						emptyStateView?.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
						Toast.makeText(requireContext(), R.string.social_post_added, Toast.LENGTH_SHORT).show()
						dialog.dismiss()
					}
					.onFailure { error ->
						Log.e(TAG, "Post creation failed", error)
						val message = error.message?.take(120)
							?: getString(R.string.social_post_failed)
						Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
						dialogBinding.btnSubmitPost.isEnabled = true
					}
			}
		}

		dialog.show()
		dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
	}

	private suspend fun resolvePostsForDisplay(posts: List<com.example.studyvillage.data.posts.local.PostEntity>): List<com.example.studyvillage.data.posts.local.PostEntity> {
		if (posts.isEmpty()) return posts

		val nameCache = mutableMapOf<String, String?>()
		return posts.map { post ->
			val rawCreatedBy = post.createdBy.trim()
			if (rawCreatedBy.isBlank()) return@map post.copy(createdBy = "unknown")

			if (!looksLikeUid(rawCreatedBy)) {
				val normalized = rawCreatedBy.removePrefix("@").trim().ifBlank { "unknown" }
				return@map post.copy(createdBy = normalized)
			}

			if (!nameCache.containsKey(rawCreatedBy)) {
				nameCache[rawCreatedBy] = runCatching {
					userRepository.getDisplayNameByUid(rawCreatedBy)
				}.getOrNull()
			}

			val resolvedName = nameCache[rawCreatedBy]?.trim().orEmpty()
			post.copy(createdBy = resolvedName.ifBlank { rawCreatedBy })
		}
	}

	private fun looksLikeUid(value: String): Boolean {
		return UID_PATTERN.matches(value)
	}

	private fun uploadPickedImage(uri: Uri) {
		val dialogBinding = activeDialogBinding ?: return
		if (isImageUploading) return
		if (UserSession.currentUid.isNullOrBlank()) {
			Toast.makeText(requireContext(), R.string.social_upload_sign_in_required, Toast.LENGTH_SHORT).show()
			return
		}

		isImageUploading = true
		dialogBinding.btnPickImage.isEnabled = false
		dialogBinding.tvImageSourceStatus.visibility = View.VISIBLE
		dialogBinding.tvImageSourceStatus.text = getString(R.string.social_uploading_image)

		viewLifecycleOwner.lifecycleScope.launch {
			runCatching {
				uploadImageToStorage(uri)
			}.onSuccess { imageReference ->
				pendingPickedImageBase64 = imageReference
				// Keep the URL field for optional web links; picked image is stored in-memory.
				dialogBinding.etImage.setText("")
				dialogBinding.tvImageSourceStatus.text = getString(R.string.social_image_selected_from_phone)
			}.onFailure { error ->
				Log.e(TAG, "Image upload failed for uri=$uri", error)
				dialogBinding.tvImageSourceStatus.text = getUploadErrorMessage(error)
				Toast.makeText(requireContext(), dialogBinding.tvImageSourceStatus.text, Toast.LENGTH_SHORT).show()
			}

			isImageUploading = false
			dialogBinding.btnPickImage.isEnabled = true
		}
	}

	private suspend fun uploadImageToStorage(uri: Uri): String {
		try {
			Log.d(TAG, "Converting image to Base64 from URI: $uri")
			
			// Step 1: Read image bytes from picker URI
			val contentResolver = requireContext().contentResolver
			val inputStream = contentResolver.openInputStream(uri) 
				?: throw IllegalStateException("Cannot open input stream for URI: $uri")
			
			val fileBytes = inputStream.use { it.readBytes() }
			Log.d(TAG, "Read ${fileBytes.size} bytes from URI")

			// Step 2: Decode + resize + compress to stay safely below Firestore doc limit.
			val decoded = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
				?: throw IllegalStateException("Could not decode selected image")

			val longestSide = max(decoded.width, decoded.height)
			val resized: Bitmap = if (longestSide > MAX_IMAGE_DIMENSION) {
				val scale = MAX_IMAGE_DIMENSION.toFloat() / longestSide.toFloat()
				Bitmap.createScaledBitmap(
					decoded,
					(decoded.width * scale).toInt().coerceAtLeast(1),
					(decoded.height * scale).toInt().coerceAtLeast(1),
					true
				)
			} else {
				decoded
			}

			val output = ByteArrayOutputStream()
			var quality = 82
			var finalBase64: String? = null

			while (quality >= 45) {
				output.reset()
				resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
				val compressedBytes = output.toByteArray()
				val base64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
				if (base64.length <= MAX_BASE64_CHARS) {
					finalBase64 = base64
					break
				}
				quality -= 7
			}

			if (resized !== decoded) resized.recycle()
			decoded.recycle()
			output.close()

			if (finalBase64 == null) {
				throw IllegalStateException("Image is too large. Please choose a smaller photo.")
			}

			Log.d(TAG, "Converted to Base64 string, length: ${finalBase64.length}")
			return finalBase64
			
		} catch (e: Exception) {
			Log.e(TAG, "Error converting image to Base64", e)
			throw e
		}
	}

	private fun getUploadErrorMessage(error: Throwable): String {
		return error.message?.take(100) ?: "Image upload failed"
	}

	override fun onDestroyView() {
		super.onDestroyView()
		postsView = null
		emptyStateView = null
		progressView = null
		coinsView = null
		createPostButton = null
	}
}
