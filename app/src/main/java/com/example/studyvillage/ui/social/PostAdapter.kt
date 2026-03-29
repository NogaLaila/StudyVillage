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
    posts: List<PostEntity> = emptyList()
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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class PostViewHolder(
        private val binding: ItemPostBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: PostEntity) {
            binding.tvPostTitle.text = post.title
            binding.tvPostContent.text = post.content
            val createdByValue = post.createdBy.trim().ifBlank { "unknown" }
            val handle = if (createdByValue.startsWith("@")) createdByValue else "@$createdByValue"
            binding.tvPostCreatedBy.text =
                itemView.context.getString(R.string.social_post_created_by, handle)
            binding.ivPostImage.contentDescription = post.title

            val image = post.image.trim()

            when {
                image.isBlank() -> {
                    binding.ivPostImage.setImageDrawable(null)
                    binding.ivPostImage.isVisible = false
                }
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
                    // Treat as Base64 payload (plain base64 string or data URI).
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
