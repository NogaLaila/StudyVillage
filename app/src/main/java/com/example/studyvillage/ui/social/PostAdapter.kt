package com.example.studyvillage.ui.social

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.studyvillage.R
import com.example.studyvillage.data.posts.local.PostEntity
import com.example.studyvillage.databinding.ItemPostBinding
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso

class PostAdapter(
    posts: List<PostEntity> = emptyList(),
    private val onEditClick: ((PostEntity) -> Unit)? = null,
    private val onDeleteClick: ((PostEntity) -> Unit)? = null
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val items = posts.toMutableList()

    fun submit(posts: List<PostEntity>) {
        items.clear()
        items.addAll(posts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(items[position], onEditClick, onDeleteClick)
    }

    override fun onViewRecycled(holder: PostViewHolder) {
        super.onViewRecycled(holder)
        holder.clearImage()
    }

    override fun getItemCount(): Int = items.size

    class PostViewHolder(
        private val binding: ItemPostBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun clearImage() {
            Picasso.get().cancelRequest(binding.ivPostImage)
            binding.ivPostImage.setImageDrawable(null)
            binding.ivPostImage.isVisible = false
        }

        fun bind(
            post: PostEntity,
            onEditClick: ((PostEntity) -> Unit)?,
            onDeleteClick: ((PostEntity) -> Unit)?
        ) {
            clearImage()

            binding.tvPostTitle.text = post.title
            binding.tvPostContent.text = post.content
            val createdByValue = post.createdBy.trim().ifBlank { "unknown" }
            val handle = if (createdByValue.startsWith("@")) createdByValue else "@$createdByValue"
            binding.tvPostCreatedBy.text =
                itemView.context.getString(R.string.social_post_created_by, handle)
            binding.ivPostImage.contentDescription = post.title

            binding.btnEditPost.isVisible = onEditClick != null
            binding.btnEditPost.setOnClickListener {
                onEditClick?.invoke(post)
            }

            binding.btnDeletePost.isVisible = onDeleteClick != null
            binding.btnDeletePost.setOnClickListener {
                onDeleteClick?.invoke(post)
            }

            val image = post.image.trim()
            val hasNoImage = image.isBlank() || image.equals("null", ignoreCase = true)

            when {
                hasNoImage -> Unit
                isLikelyUrl(image) -> {
                    binding.ivPostImage.isVisible = true
                    Picasso.get()
                        .load(image)
                        .fit()
                        .centerCrop()
                        .into(binding.ivPostImage, object : Callback {
                            override fun onSuccess() {
                                binding.ivPostImage.isVisible = true
                            }

                            override fun onError(e: Exception?) {
                                binding.ivPostImage.setImageDrawable(null)
                                binding.ivPostImage.isVisible = false
                            }
                        })
                }
                else -> {
                    val base64Payload = extractBase64Payload(image)
                    val bitmap = decodeBase64ToBitmap(base64Payload)
                    if (bitmap != null) {
                        binding.ivPostImage.setImageBitmap(bitmap)
                        binding.ivPostImage.isVisible = true
                    } else {
                        binding.ivPostImage.setImageDrawable(null)
                        binding.ivPostImage.isVisible = false
                    }
                }
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
    }
}
