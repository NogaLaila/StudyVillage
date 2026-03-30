package com.example.studyvillage.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.Patterns
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
import com.example.studyvillage.databinding.DialogEditProfileBinding
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
    private var btnEditProfile: View? = null
    private var rvMyPosts: RecyclerView? = null
    private var tvEmptyMyPosts: TextView? = null
    private var progressMyPosts: ProgressBar? = null
    private val rawPostsById = mutableMapOf<String, PostEntity>()

    private lateinit var postRepository: PostRepository
    private lateinit var userRepository: UserRepository
    private val postAdapter = PostAdapter(onEditClick = { displayPost ->
        showEditPostDialog(rawPostsById[displayPost.id] ?: displayPost)
    }, onDeleteClick = { displayPost ->
        confirmDeletePost(rawPostsById[displayPost.id] ?: displayPost)
    })

    private var activeDialogBinding: DialogCreatePostBinding? = null
    private var isImageUploading = false
    private var pendingPickedImageBase64: String? = null

    private var activeProfileDialogBinding: DialogEditProfileBinding? = null
    private var isProfileImageUploading = false
    private var pendingProfileImageBase64: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        uploadPickedImage(uri)
    }

    private val pickProfileImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        uploadPickedProfileImage(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvName = view.findViewById(R.id.tvProfileName)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        tvCoins = view.findViewById(R.id.tvProfileCoins)
        tvTopCoins = view.findViewById(R.id.txtCoins)
        ivAvatar = view.findViewById(R.id.ivProfileAvatar)
        btnEditProfile = view.findViewById(R.id.btnEditProfile)
        rvMyPosts = view.findViewById(R.id.rvMyPosts)
        tvEmptyMyPosts = view.findViewById(R.id.tvEmptyMyPosts)
        progressMyPosts = view.findViewById(R.id.progressMyPosts)

        val db = DataBaseProvider.get(requireContext())
        postRepository = PostRepository(db.postDao(), PostRemote())
        userRepository = UserRepository(db.userDao(), UserRemote())

        rvMyPosts?.layoutManager = LinearLayoutManager(requireContext())
        rvMyPosts?.adapter = postAdapter
        btnEditProfile?.setOnClickListener { showEditProfileDialog() }

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
            val shownEmail = localUser?.email?.takeIf { it.isNotBlank() }
                ?: email
                ?: getString(R.string.profile_email_placeholder)
            val shownPhoto = localUser?.photoUrl

            tvName?.text = name
            tvEmail?.text = shownEmail
            renderProfileAvatar(shownPhoto)
            localUser?.coins?.let { coins ->
                tvCoins?.text = coins.toString()
                tvTopCoins?.text = coins.toString()
            }

            runCatching { userRepository.syncUser(uid, email, displayName) }
            val refreshed = userRepository.getLocalUser(uid)
            if (refreshed != null) {
                tvName?.text = refreshed.name?.takeIf { it.isNotBlank() }
                    ?: displayName
                    ?: getString(R.string.profile_name_placeholder)
                tvEmail?.text = refreshed.email?.takeIf { it.isNotBlank() }
                    ?: email
                    ?: getString(R.string.profile_email_placeholder)
                tvCoins?.text = refreshed.coins.toString()
                tvTopCoins?.text = refreshed.coins.toString()
                renderProfileAvatar(refreshed.photoUrl)
            }
        }
    }

    private fun showEditProfileDialog() {
        val uid = UserSession.currentUid ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val currentUser = userRepository.getLocalUser(uid)
            val dialogBinding = DialogEditProfileBinding.inflate(LayoutInflater.from(requireContext()))
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.root)
                .create()

            activeProfileDialogBinding = dialogBinding
            pendingProfileImageBase64 = null

            dialogBinding.etEditName.setText(
                currentUser?.name?.takeIf { it.isNotBlank() }
                    ?: UserSession.currentName
                    ?: ""
            )
            dialogBinding.etEditEmail.setText(
                currentUser?.email?.takeIf { it.isNotBlank() }
                    ?: UserSession.currentEmail
                    ?: ""
            )

            val existingImage = currentUser?.photoUrl?.trim().orEmpty()
            if (isLikelyUrl(existingImage)) {
                dialogBinding.etEditImage.setText(existingImage)
            } else if (existingImage.isNotBlank() && existingImage != UserRepository.DEFAULT_PROFILE_PHOTO) {
                pendingProfileImageBase64 = existingImage
                dialogBinding.etEditImage.setText("")
            }

            dialogBinding.etEditImage.doAfterTextChanged {
                dialogBinding.inputEditImage.error = null
                updateEditProfilePreviewFromInput(dialogBinding)
            }
            updateEditProfilePreviewFromInput(dialogBinding)

            dialogBinding.btnPickProfileImage.setOnClickListener {
                pickProfileImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }

            dialogBinding.btnCancelEditProfile.setOnClickListener { dialog.dismiss() }

            dialogBinding.btnSaveEditProfile.setOnClickListener {
                val name = dialogBinding.etEditName.text?.toString()?.trim().orEmpty()
                val email = dialogBinding.etEditEmail.text?.toString()?.trim().orEmpty()
                val typedImage = dialogBinding.etEditImage.text?.toString()?.trim().orEmpty()
                val image = if (typedImage.isNotBlank()) typedImage else pendingProfileImageBase64

                dialogBinding.inputEditName.error = null
                dialogBinding.inputEditEmail.error = null

                var hasError = false
                if (name.isBlank()) {
                    dialogBinding.inputEditName.error = getString(R.string.social_field_required)
                    hasError = true
                }
                if (email.isBlank()) {
                    dialogBinding.inputEditEmail.error = getString(R.string.social_field_required)
                    hasError = true
                } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    dialogBinding.inputEditEmail.error = getString(R.string.profile_email_invalid)
                    hasError = true
                }
                if (hasError) return@setOnClickListener

                if (isProfileImageUploading) {
                    Toast.makeText(requireContext(), R.string.profile_image_uploading_wait, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                dialogBinding.btnSaveEditProfile.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        userRepository.updateUserProfile(
                            uid = uid,
                            name = name,
                            email = email,
                            photoUrl = image
                        )
                    }
                        .onSuccess { updated ->
                            tvName?.text = updated.name?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.profile_name_placeholder)
                            tvEmail?.text = updated.email?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.profile_email_placeholder)
                            renderProfileAvatar(updated.photoUrl)
                            Toast.makeText(requireContext(), R.string.profile_edit_updated, Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                        .onFailure { error ->
                            Log.e(TAG, "Profile update failed", error)
                            Toast.makeText(requireContext(), R.string.profile_edit_failed, Toast.LENGTH_SHORT).show()
                            dialogBinding.btnSaveEditProfile.isEnabled = true
                        }
                }
            }

            dialog.setOnDismissListener {
                activeProfileDialogBinding = null
                pendingProfileImageBase64 = null
                isProfileImageUploading = false
            }

            dialog.show()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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

    private fun confirmDeletePost(post: PostEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_delete_post_title)
            .setMessage(R.string.profile_delete_post_message)
            .setNegativeButton(R.string.social_cancel_action, null)
            .setPositiveButton(R.string.profile_delete_post_confirm) { _, _ ->
                deletePost(post.id)
            }
            .show()
    }

    private fun deletePost(postId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { postRepository.deletePost(postId) }
                .onSuccess {
                    Toast.makeText(requireContext(), R.string.profile_post_deleted, Toast.LENGTH_SHORT).show()
                    loadMyPosts()
                }
                .onFailure { error ->
                    Log.e(TAG, "Post delete failed", error)
                    Toast.makeText(requireContext(), R.string.profile_post_delete_failed, Toast.LENGTH_SHORT).show()
                }
        }
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

    private fun uploadPickedProfileImage(uri: Uri) {
        val dialogBinding = activeProfileDialogBinding ?: return
        if (isProfileImageUploading) return

        isProfileImageUploading = true
        dialogBinding.btnPickProfileImage.isEnabled = false
        dialogBinding.tvProfileImageStatus.isVisible = true
        dialogBinding.tvProfileImageStatus.text = getString(R.string.profile_image_uploading)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { uploadImageToBase64(uri) }
                .onSuccess { imageBase64 ->
                    pendingProfileImageBase64 = imageBase64
                    dialogBinding.etEditImage.setText("")
                    updateEditProfilePreviewFromInput(dialogBinding)
                    dialogBinding.tvProfileImageStatus.text = getString(R.string.profile_image_selected_from_phone)
                }
                .onFailure { error ->
                    Log.e(TAG, "Profile image upload failed for uri=$uri", error)
                    dialogBinding.tvProfileImageStatus.text = getUploadErrorMessage(error)
                    Toast.makeText(requireContext(), dialogBinding.tvProfileImageStatus.text, Toast.LENGTH_SHORT).show()
                }

            isProfileImageUploading = false
            dialogBinding.btnPickProfileImage.isEnabled = true
        }
    }

    private fun updateDialogPreviewFromInput(dialogBinding: DialogCreatePostBinding) {
        val typedImage = dialogBinding.etImage.text?.toString()?.trim().orEmpty()
        val selectedImage = if (typedImage.isNotBlank()) typedImage else pendingPickedImageBase64.orEmpty()
        renderDialogImagePreview(dialogBinding, selectedImage)
    }

    private fun updateEditProfilePreviewFromInput(dialogBinding: DialogEditProfileBinding) {
        val typedImage = dialogBinding.etEditImage.text?.toString()?.trim().orEmpty()
        val selectedImage = if (typedImage.isNotBlank()) typedImage else pendingProfileImageBase64.orEmpty()
        renderEditProfileImagePreview(dialogBinding, selectedImage)
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

    private fun renderEditProfileImagePreview(dialogBinding: DialogEditProfileBinding, imageValue: String) {
        val normalized = imageValue.trim()
        if (normalized.isBlank() || normalized == UserRepository.DEFAULT_PROFILE_PHOTO) {
            dialogBinding.ivProfileImagePreview.setImageDrawable(null)
            dialogBinding.ivProfileImagePreview.isVisible = false
            return
        }

        if (isLikelyUrl(normalized)) {
            dialogBinding.ivProfileImagePreview.isVisible = true
            Picasso.get()
                .load(normalized)
                .fit()
                .centerCrop()
                .into(dialogBinding.ivProfileImagePreview, object : Callback {
                    override fun onSuccess() {
                        dialogBinding.ivProfileImagePreview.isVisible = true
                    }

                    override fun onError(e: Exception?) {
                        dialogBinding.ivProfileImagePreview.setImageDrawable(null)
                        dialogBinding.ivProfileImagePreview.isVisible = false
                    }
                })
            return
        }

        val bitmap = decodeBase64ToBitmap(extractBase64Payload(normalized))
        if (bitmap == null) {
            dialogBinding.ivProfileImagePreview.setImageDrawable(null)
            dialogBinding.ivProfileImagePreview.isVisible = false
        } else {
            dialogBinding.ivProfileImagePreview.setImageBitmap(bitmap)
            dialogBinding.ivProfileImagePreview.isVisible = true
        }
    }

    private fun renderProfileAvatar(photoValue: String?) {
        val avatar = ivAvatar ?: return
        val normalized = photoValue?.trim().orEmpty()

        if (normalized.isBlank() || normalized == UserRepository.DEFAULT_PROFILE_PHOTO) {
            avatar.setImageResource(R.drawable.profile_panda)
            return
        }

        if (isLikelyUrl(normalized)) {
            Picasso.get()
                .load(normalized)
                .fit()
                .centerCrop()
                .into(avatar, object : Callback {
                    override fun onSuccess() = Unit

                    override fun onError(e: Exception?) {
                        avatar.setImageResource(R.drawable.profile_panda)
                    }
                })
            return
        }

        val bitmap = decodeBase64ToBitmap(extractBase64Payload(normalized))
        if (bitmap != null) {
            avatar.setImageBitmap(bitmap)
        } else {
            avatar.setImageResource(R.drawable.profile_panda)
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
        btnEditProfile = null
        rvMyPosts = null
        tvEmptyMyPosts = null
        progressMyPosts = null
        activeDialogBinding = null
        pendingPickedImageBase64 = null
        isImageUploading = false
        activeProfileDialogBinding = null
        pendingProfileImageBase64 = null
        isProfileImageUploading = false
    }
}
