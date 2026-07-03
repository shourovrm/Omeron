package com.omeron.data.local.mapper

import com.omeron.data.model.User
import com.omeron.data.remote.api.reddit.model.AboutUserData
import com.omeron.di.DispatchersModule
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserMapper2 @Inject constructor(
    @DispatchersModule.DefaultDispatcher defaultDispatcher: CoroutineDispatcher
) : Mapper<AboutUserData, User>(defaultDispatcher) {

    override suspend fun toEntity(from: AboutUserData): User {
        with(from) {
            return User(
                isSuspended,
                name,
                subreddit?.title,
                subreddit?.over18 ?: false,
                iconImg,
                subreddit?.url,
                subreddit?.publicDescription,
                linkKarma,
                commentKarma,
                getTimeInMillis()
            )
        }
    }
}
