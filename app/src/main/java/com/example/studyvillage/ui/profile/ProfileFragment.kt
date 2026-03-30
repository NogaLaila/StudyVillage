package com.example.studyvillage.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studyvillage.R
import com.example.studyvillage.data.posts.PostRepository
import com.example.studyvillage.data.posts.local.PostEntity
import com.example.studyvillage.data.posts.remote.PostRemote
import com.example.studyvillage.data.shop.local.DataBaseProvider
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.remote.UserRemote
import com.example.studyvillage.databinding.DialogCreatePostBinding
import com.example.studyvillage.ui.social.PostAdapter
import com.example.studyvillage.util.UserSession
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.max

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private companion object {
        const val TAG = "ProfileFragment"
        const val MAX_IMAGE_DIMENSION = 1080
        const val MAX_BASE64_CHARS = 700_000
    }

    private var tvName: TextView? = null
    private var tvEmail: TextView? = null
    private var tvCoins: TextView? = null
    private var tvTopCoins: TextView? = null
    private var ivAvatar: ImageView? = null
    private var rvMyPosts: RecyclerView? = null
    private var tvEmptyMyPosts: TextView? = null
    private var progressMyPosts: ProgressBar? = null
    private val rawPostsById = mutableMapOf<String, PostEntity>()

    private lateinit var postRepository: PostRepository
    private lateinit var userRepository: UserRepository
    private val postAdapter = PostAdapter(onEditClick = { displayPost ->
        showEditPostDialog(rawPostsById[displayPost.id] ?: displayPost)
    })

    private var activeDialogBinding: DialogCreatePostBinding? = null
    private var isImageUploading = false
    private var pendingPickedImageBase64: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        uploadPickedImage(uri)
    }

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

        val db = DataBaseProvider.get(requireContext())
        postRepository = PostRepository(db.postDao(), PostRemote())
        userRepository = UserRepository(db.userDao(), UserRemote())

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

            val localUser = userRepository.getLocalUser(uid)
            val name = localUser?.name?.takeIf { it.isNotBlank() }
                ?: displayName
                ?: getString(R.string.profile_name_placeholder)

            tvEmail?.text = email?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_email_placeholder)
            tvName?.text = name
            localUser?.coins?.let { coins ->
                tvCoins?.text = coins.toString()
                tvTopCoins?.text = coins.toString()
            }

            runCatching { userRepository.syncUser(uid, email, displayName) }
            val refreshed = userRepository.getLocalUser(uid)
            if (refreshed != null && refreshed.coins != localUser?.coins) {
                tvCoins?.text = refreshed.coins.toString()
                tvTopCoins?.text = refreshed.coins.toString()
            }
        }
    }

    private fun loadMyPosts() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = UserSession.currentUid ?: return@launch

            val cached = runCatching { postRepository.getCachedUserPosts(uid) }.getOrDefault(emptyList())
            postAdapter.submit(resolveProfilePostsForDisplay(cached))
            tvEmptyMyPosts?.isVisible = cached.isEmpty()

            progressMyPosts?.isVisible = true
            runCatching { postRepository.refreshUserPosts(uid) }
                .onSuccess { fresh ->
                    postAdapter.submit(resolveProfilePostsForDisplay(fresh))
                    tvEmptyMyPosts?.isVisible = fresh.isEmpty()
                }
            progressMyPosts?.isVisible = false
        }
    }

    private suspend fun resolveProfilePostsForDisplay(posts: List<PostEntity>): List<PostEntity> {
        if (posts.isEmpty()) {
            rawPostsById.clear()
            return posts
        }

        rawPostsById.clear()
        posts.forEach { rawPostsById[it.id] = it }

        val nameCache = mutableMapOf<String, String?>()
        return posts.map { post ->
            val lookupKey = post.createdBy.removePrefix("@").trim()
            if (lookupKey.isBlank()) return@map post.copy(createdBy = "unknown")

            if (!nameCache.containsKey(lookupKey)) {
                nameCache[lookupKey] = runCatching {
                    userRepository.getDisplayNameByUid(lookupKey)
                }.getOrNull()
            }

            val resolvedName = nameCache[lookupKey]?.trim().orEmpty()
            post.copy(createdBy = resolvedName.ifBlank { lookupKey })
        }
    }

    private fun showEditPostDialog(post: PostEntity) {
        val dialogBinding = DialogCreatePostBinding.inflate(LayoutInflater.from(requireContext()))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        activeDialogBinding = dialogBinding
        pendingPickedImageBase64 = null

        dialogBinding.tvDialogTitle.text = getString(R.string.social_edit_post_title)
        dialogBinding.btnSubmitPost.text = getString(R.string.social_edit_post_action)

        dialogBinding.etTitle.setText(post.title)
        dialogBinding.etContent.setText(post.content)

        if (isLikelyUrl(post.image.trim())) {
            dialogBinding.etImage.setText(post.image)
        } else {
            pendingPickedImageBase64 = post.image.takeIf { it.isNotBlank() }
            dialogBinding.etImage.setText("")
        }

        dialogBinding.etImage.doAfterTextChanged {
            dialogBinding.inputImage.error = null
            updateDialogPreviewFromInput(dialogBinding)
        }
        updateDialogPreviewFromInput(dialogBinding)

        dialog.setOnDismissListener {
            activeDialogBinding = null
            isImageUploading = false
            pendingPickedImageBase64 = null
        }

        dialogBinding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        dialogBinding.btnCancelPost.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSubmitPost.setOnClickListener {
            val title = dialogBinding.etTitle.text?.toString()?.trim().orEmpty()
            val content = dialogBinding.etContent.text?.toString()?.trim().orEmpty()
            val typedImage = dialogBinding.etImage.text?.toString()?.trim().orEmpty()
            val image = if (typedImage.isNotBlank()) typedImage else pendingPickedImageBase64.orEmpty()

            dialogBinding.inputTitle.error = null
            dialogBinding.inputContent.error = null

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
            if (isImageUploading) {
                Toast.makeText(requireContext(), R.string.social_image_uploading_wait, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialogBinding.btnSubmitPost.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val updatedPost = post.copy(
                    title = title,
                    content = content,
                    image = image
                )

                runCatching { postRepository.updatePost(updatedPost) }
                    .onSuccess {
                        Toast.makeText(requireContext(), R.string.social_post_updated, Toast.LENGTH_SHORT).show()
                        loadMyPosts()
                        dialog.dismiss()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Post update failed", error)
                        Toast.makeText(requireContext(), R.string.social_post_update_failed, Toast.LENGTH_SHORT).show()
                        dialogBinding.btnSubmitPost.isEnabled = true
                    }
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun uploadPickedImage(uri: Uri) {
        val dialogBinding = activeDialogBinding ?: return
        if (isImageUploading) return

        isImageUploading = true
        dialogBinding.btnPickImage.isEnabled = false
        dialogBinding.tvImageSourceStatus.visibility = View.VISIBLE
        dialogBinding.tvImageSourceStatus.text = getString(R.string.social_uploading_image)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { uploadImageToBase64(uri) }
                .onSuccess { imageReference ->
                    pendingPickedImageBase64 = imageReference
                    dialogBinding.etImage.setText("")
                    updateDialogPreviewFromInput(dialogBinding)
                    dialogBinding.tvImageSourceStatus.text = getString(R.string.social_image_selected_from_phone)
                }
                .onFailure { error ->
                    Log.e(TAG, "Image upload failed for uri=$uri", error)
                    dialogBinding.tvImageSourceStatus.text = getUploadErrorMessage(error)
                    Toast.makeText(requireContext(), dialogBinding.tvImageSourceStatus.text, Toast.LENGTH_SHORT).show()
                }

            isImageUploading = false
            dialogBinding.btnPickImage.isEnabled = true
        }
    }

    private fun updateDialogPreviewFromInput(dialogBinding: DialogCreatePostBinding) {
        val typedImage = dialogBinding.etImage.text?.toString()?.trim().orEmpty()
        val selectedImage = if (typedImage.isNotBlank()) typedImage else pendingPickedImageBase64.orEmpty()
        renderDialogImagePreview(dialogBinding, selectedImage)
    }

    private fun renderDialogImagePreview(dialogBinding: DialogCreatePostBinding, imageValue: String) {
        val normalized = imageValue.trim()
        if (normalized.isBlank()) {
            dialogBinding.ivImagePreview.setImageDrawable(null)
            dialogBinding.ivImagePreview.isVisible = false
            return
        }

        if (isLikelyUrl(normalized)) {
            dialogBinding.ivImagePreview.isVisible = true
            Picasso.get()
                .load(normalized)
                .fit()
                .centerCrop()
                .into(dialogBinding.ivImagePreview, object : Callback {
                    override fun onSuccess() {
                        dialogBinding.ivImagePreview.isVisible = true
                    }

                    override fun onError(e: Exception?) {
                        dialogBinding.ivImagePreview.setImageDrawable(null)
                        dialogBinding.ivImagePreview.isVisible = false
                    }
                })
            return
        }

        val bitmap = decodeBase64ToBitmap(extractBase64Payload(normalized))
        if (bitmap == null) {
            dialogBinding.ivImagePreview.setImageDrawable(null)
            dialogBinding.ivImagePreview.isVisible = false
        } else {
            dialogBinding.ivImagePreview.setImageBitmap(bitmap)
            dialogBinding.ivImagePreview.isVisible = true
        }
    }

    private fun isLikelyUrl(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("content://") ||
            lower.startsWith("file://")
    }

    private fun extractBase64Payload(value: String): String {
        val commaIndex = value.indexOf(',')
        return if (value.startsWith("data:") && commaIndex != -1) {
            value.substring(commaIndex + 1)
        } else {
            value
        }
    }

    private fun decodeBase64ToBitmap(base64: String) = try {
        val imageBytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (_: IllegalArgumentException) {
        null
    }

    private suspend fun uploadImageToBase64(uri: Uri): String {
        val contentResolver = requireContext().contentResolver
        val fileBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open selected image")

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
            val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            if (base64.length <= MAX_BASE64_CHARS) {
                finalBase64 = base64
                break
            }
            quality -= 7
        }

        if (resized !== decoded) resized.recycle()
        decoded.recycle()
        output.close()

        return finalBase64 ?: throw IllegalStateException("Image is too large. Please choose a smaller photo.")
    }

    private fun getUploadErrorMessage(error: Throwable): String {
        return error.message?.take(100) ?: getString(R.string.social_image_upload_failed)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvName = null
        tvEmail = null
        tvCoins = null
        tvTopCoins = null
        ivAvatar = null
        rvMyPosts = null
        tvEmptyMyPosts = null
        progressMyPosts = null
        activeDialogBinding = null
        pendingPickedImageBase64 = null
        isImageUploading = false
    }
}
