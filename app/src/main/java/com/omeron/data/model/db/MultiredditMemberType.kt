package com.omeron.data.model.db

enum class MultiredditMemberType(val value: Int) {
    SUBREDDIT(0),
    USER(1);

    companion object {
        fun fromValue(value: Int): MultiredditMemberType {
            return values().first { it.value == value }
        }
    }
}
