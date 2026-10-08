package com.omeron.data.model

import com.omeron.data.model.db.Profile

sealed class ProfileItem {

    data class UserProfile(
        val profile: Profile,
        val communityCount: Int = 0,
        val savedPostCount: Int = 0
    ) : ProfileItem()

    object NewProfile : ProfileItem()
}
