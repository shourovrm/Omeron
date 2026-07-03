package com.omeron.data.model

import com.omeron.data.model.db.Profile

sealed class ProfileItem {

    data class UserProfile(val profile: Profile) : ProfileItem()

    object NewProfile : ProfileItem()
}
