package com.omeron.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.omeron.data.local.RedditDatabase
import com.omeron.data.model.Comment
import com.omeron.data.model.Sorting
import com.omeron.data.model.db.FollowedUser
import com.omeron.data.model.db.History
import com.omeron.data.model.db.Multireddit
import com.omeron.data.model.db.MultiredditMember
import com.omeron.data.model.db.MultiredditMemberType
import com.omeron.data.model.db.MultiredditWithMembers
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.db.Profile
import com.omeron.data.model.db.Subscription
import com.omeron.data.remote.api.reddit.model.AboutChild
import com.omeron.data.remote.api.reddit.model.AboutUserChild
import com.omeron.data.remote.api.reddit.model.Child
import com.omeron.data.remote.api.reddit.model.Listing
import com.omeron.data.remote.api.reddit.model.MoreChildren
import com.omeron.data.remote.api.reddit.source.CurrentSource
import com.omeron.data.remote.datasource.CommentsDataSource
import com.omeron.data.remote.datasource.MultiredditDataSource
import com.omeron.data.remote.datasource.SearchPostDataSource
import com.omeron.data.remote.datasource.SearchSubredditDataSource
import com.omeron.data.remote.datasource.SearchUserDataSource
import com.omeron.data.remote.datasource.SmartPostListDataSource
import com.omeron.data.remote.datasource.SubredditSearchPostDataSource
import com.omeron.data.remote.datasource.UserPostsDataSource
import com.omeron.di.DispatchersModule.DefaultDispatcher
import com.omeron.di.DispatchersModule.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostListRepository @Inject constructor(
    private val source: CurrentSource,
    private val redditDatabase: RedditDatabase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @MainImmediateDispatcher private val mainImmediateDispatcher: CoroutineDispatcher
) {

    fun getPost(permalink: String, sorting: Sorting): Flow<List<Listing>> = flow {
        emit(source.getPost(permalink, sort = sorting.generalSorting))
    }

    fun getMoreChildren(children: String, linkId: String, depth: Int = 0): Flow<MoreChildren> =
        flow {
            emit(source.getMoreChildren(children, linkId, depth))
        }

    //region Subreddit

    fun getPosts(
        subreddit: String,
        sorting: Sorting,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Child>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            SmartPostListDataSource(
                source,
                listOf(subreddit),
                sorting,
                defaultDispatcher,
                mainImmediateDispatcher
            )
        }.flow
    }

    fun getPosts(
        subreddit: List<String>,
        sorting: Sorting,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Child>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            SmartPostListDataSource(
                source,
                subreddit,
                sorting,
                defaultDispatcher,
                mainImmediateDispatcher
            )
        }.flow
    }

    fun getSubredditInfo(subreddit: String): Flow<AboutChild> = flow {
        emit(source.getSubredditInfo(subreddit) as AboutChild)
    }

    //endregion

    //region Subscriptions

    fun getSubscriptions(profileId: Int): Flow<List<Subscription>> = redditDatabase
        .subscriptionDao().getSubscriptionsFromProfile(profileId).distinctUntilChanged()

    fun getSubscriptionsNames(profileId: Int): Flow<List<String>> {
        return redditDatabase.subscriptionDao().getSubscriptionsNamesFromProfile(profileId)
    }

    fun getVisibleSubscriptionsNames(profileId: Int): Flow<List<String>> {
        return redditDatabase.subscriptionDao().getVisibleSubscriptionsNamesFromProfile(profileId)
    }

    suspend fun subscribe(name: String, profileId: Int, icon: String? = null) {
        redditDatabase.subscriptionDao().insert(
            Subscription(name, System.currentTimeMillis(), icon, profileId)
        )
    }

    suspend fun unsubscribe(name: String, profileId: Int) {
        redditDatabase.subscriptionDao().deleteFromNameAndProfile(name, profileId)
    }

    suspend fun setSubscriptionHidden(name: String, profileId: Int, hidden: Boolean) {
        redditDatabase.subscriptionDao().setHidden(name, profileId, hidden)
    }

    //endregion

    //region Followed users

    fun getFollowedUsers(profileId: Int): Flow<List<FollowedUser>> {
        return redditDatabase.followedUserDao().getFromProfile(profileId)
    }

    fun getVisibleFollowedUserNames(profileId: Int): Flow<List<String>> {
        return redditDatabase.followedUserDao().getVisibleNamesFromProfile(profileId)
    }

    fun isUserFollowed(name: String, profileId: Int): Flow<Boolean> {
        return redditDatabase.followedUserDao().isFollowed(name, profileId)
    }

    suspend fun followUser(name: String, profileId: Int, icon: String? = null) {
        redditDatabase.followedUserDao().insert(
            FollowedUser(name, icon, System.currentTimeMillis(), profileId = profileId)
        )
    }

    suspend fun unfollowUser(name: String, profileId: Int) {
        redditDatabase.followedUserDao().deleteFromNameAndProfile(name, profileId)
    }

    suspend fun setUserHidden(name: String, profileId: Int, hidden: Boolean) {
        redditDatabase.followedUserDao().setHidden(name, profileId, hidden)
    }

    //endregion

    //region Multireddits

    fun getMultireddits(profileId: Int): Flow<List<MultiredditWithMembers>> {
        return redditDatabase.multiredditDao().getMultisWithMembersFromProfile(profileId)
    }

    fun getVisibleMultireddits(profileId: Int): Flow<List<MultiredditWithMembers>> {
        return redditDatabase.multiredditDao().getVisibleMultisWithMembersFromProfile(profileId)
    }

    suspend fun createMultireddit(name: String, profileId: Int): Long {
        return redditDatabase.multiredditDao().insert(Multireddit(name = name, profileId = profileId))
    }

    suspend fun renameMultireddit(id: Long, name: String) {
        redditDatabase.multiredditDao().rename(id, name)
    }

    suspend fun deleteMultireddit(id: Long) {
        redditDatabase.multiredditDao().deleteFromId(id)
    }

    suspend fun setMultiredditHidden(id: Long, hidden: Boolean) {
        redditDatabase.multiredditDao().setHidden(id, hidden)
    }

    suspend fun addMember(multiId: Long, targetName: String, type: MultiredditMemberType) {
        redditDatabase.multiredditDao().addMember(
            MultiredditMember(multiredditId = multiId, targetName = targetName, type = type.value)
        )
    }

    suspend fun removeMember(multiId: Long, targetName: String, type: MultiredditMemberType) {
        redditDatabase.multiredditDao().removeMember(multiId, targetName, type.value)
    }

    fun getMultiredditPosts(
        subreddits: List<String>,
        users: List<String>,
        sorting: Sorting,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Child>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            MultiredditDataSource(
                source,
                subreddits,
                users,
                sorting,
                defaultDispatcher,
                mainImmediateDispatcher
            )
        }.flow
    }

    //endregion

    //region User

    fun getUserPosts(
        user: String,
        sorting: Sorting,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Child>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            UserPostsDataSource(source, user, sorting)
        }.flow
    }

    fun getUserComments(
        user: String,
        sorting: Sorting,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Child>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            CommentsDataSource(source, user, sorting)
        }.flow
    }

    fun getUserInfo(user: String): Flow<AboutUserChild> = flow {
        emit(source.getUserInfo(user) as AboutUserChild)
    }

    //endregion

    //region Search

    fun searchPost(
        query: String,
        sorting: Sorting,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Child>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            SearchPostDataSource(source, query, sorting)
        }.flow
    }

    fun searchUser(
        query: String,
        sorting: Sorting,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Child>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            SearchUserDataSource(source, query, sorting)
        }.flow
    }

    fun searchSubreddit(
        query: String,
        sorting: Sorting,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Child>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            SearchSubredditDataSource(source, query, sorting)
        }.flow
    }

    fun searchInSubreddit(
        query: String,
        subreddit: String,
        sorting: Sorting,
        pageSize: Int = DEFAULT_LIMIT
    ): Flow<PagingData<Child>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            SubredditSearchPostDataSource(source, subreddit, query, sorting)
        }.flow
    }

    //endregion

    //region History

    fun getHistoryIds(profileId: Int): Flow<List<String>> {
        return redditDatabase.historyDao().getHistoryIdsFromProfile(profileId)
    }

    suspend fun insertPostInHistory(postId: String, profileId: Int) {
        redditDatabase.historyDao().upsert(History(postId, System.currentTimeMillis(), profileId))
    }

    //endregion

    //region Profile

    suspend fun addProfile(name: String) {
        redditDatabase.profileDao().insert(Profile(name = name))
    }

    suspend fun getProfile(id: Int): Profile {
        return redditDatabase.profileDao().getProfileFromId(id)
            ?: redditDatabase.profileDao().getFirstProfile()
    }

    fun getAllProfiles(): Flow<List<Profile>> {
        return redditDatabase.profileDao().getAllProfiles()
    }

    suspend fun deleteProfile(profileId: Int) {
        redditDatabase.profileDao().deleteFromId(profileId)
    }

    suspend fun updateProfile(profile: Profile) {
        redditDatabase.profileDao().update(profile)
    }

    //endregion

    //region Save

    suspend fun savePost(post: PostEntity, profileId: Int) {
        post.run {
            this.profileId = profileId
            this.time = System.currentTimeMillis()
            redditDatabase.postDao().upsert(this)
        }
    }

    suspend fun unsavePost(post: PostEntity, profileId: Int) {
        redditDatabase.postDao().deleteFromIdAndProfile(post.id, profileId)
    }

    fun getSavedPosts(profileId: Int): Flow<List<PostEntity>> {
        return redditDatabase.postDao().getSavedPostsFromProfile(profileId)
    }

    fun getSavedPostIds(profileId: Int): Flow<List<String>> {
        return redditDatabase.postDao().getSavedPostIdsFromProfile(profileId)
    }

    suspend fun saveComment(comment: Comment.CommentEntity, profileId: Int) {
        comment.run {
            this.profileId = profileId
            this.time = System.currentTimeMillis()
            redditDatabase.commentDao().upsert(comment)
        }
    }

    suspend fun unsaveComment(comment: Comment.CommentEntity, profileId: Int) {
        redditDatabase.commentDao().deleteFromIdAndProfile(comment.name, profileId)
    }

    fun getSavedComments(profileId: Int): Flow<List<Comment.CommentEntity>> {
        return redditDatabase.commentDao().getSavedCommentsFromProfile(profileId)
    }

    fun getSavedCommentIds(profileId: Int): Flow<List<String>> {
        return redditDatabase.commentDao().getSavedCommentIdsFromProfile(profileId)
    }

    //endregion

    companion object {
        private const val DEFAULT_LIMIT = 25
    }
}
