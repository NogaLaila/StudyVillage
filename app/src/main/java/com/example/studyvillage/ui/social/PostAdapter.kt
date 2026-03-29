package com.example.studyvillage.ui.social

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.studyvillage.R
import com.example.studyvillage.data.posts.local.PostEntity
import com.example.studyvillage.databinding.ItemPostBinding
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
            binding.ivPostImage.contentDescription = post.title
            binding.ivPostImage.isVisible = post.image.isNotBlank()

            if (post.image.isNotBlank()) {
                Picasso.get()
                    .load(post.image)
                    .placeholder(R.drawable.ic_social)
                    .error(R.drawable.ic_social)
                    .fit()
                    .centerCrop()
                    .into(binding.ivPostImage)
            } else {
                binding.ivPostImage.setImageResource(R.drawable.ic_social)
            }
        }
    }
}

