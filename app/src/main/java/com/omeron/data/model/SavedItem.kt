package com.omeron.data.model

import com.omeron.data.model.db.PostEntity

sealed class SavedItem(val timestamp: Long) {
    data class Post(val post: PostEntity) : SavedItem(post.time)

    data class Comment(
        val comment: com.omeron.data.model.Comment.CommentEntity
    ) : SavedItem(comment.time)
}
