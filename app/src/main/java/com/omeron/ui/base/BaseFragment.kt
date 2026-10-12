package com.omeron.ui.base

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.omeron.NavigationGraphDirections
import com.omeron.R
import com.omeron.data.model.MediaType
import com.omeron.data.model.db.PostEntity
import com.omeron.ui.common.widget.RedditView
import com.omeron.ui.filmstrip.FilmstripFeedHolder
import com.omeron.ui.filmstrip.FilmstripFeedLink
import com.omeron.ui.filmstrip.FilmstripViewerFragment
import com.omeron.ui.linkmenu.LinkMenuFragment
import com.omeron.ui.postdetails.PostDetailsFragment
import com.omeron.ui.postlist.PostListAdapter
import com.omeron.ui.postmenu.PostMenuFragment
import com.omeron.util.LinkHandler
import com.omeron.util.extension.applyWindowInsets
import com.omeron.util.extension.currentNavigationFragment
import com.omeron.util.extension.normalizeRedditLink
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class BaseFragment : Fragment(), PostListAdapter.PostClickListener,
    RedditView.OnLinkClickListener {

    protected open val viewModel: BaseViewModel? = null

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private val navOptions: NavOptions by lazy {
        NavOptions.Builder()
            .setEnterAnim(R.anim.nav_enter_anim)
            .setExitAnim(R.anim.nav_exit_anim)
            .setPopEnterAnim(R.anim.nav_enter_anim)
            .setPopExitAnim(R.anim.nav_exit_anim)
            .build()
    }

    @Inject
    lateinit var linkHandler: LinkHandler

    @Inject
    lateinit var filmstripFeedHolder: FilmstripFeedHolder

    /**
     * Which of the feed's posts the Filmstrip viewer shows. A screen with a post list that opens
     * the viewer overrides this once; null (the default) means the screen has no feed viewer and
     * its media taps open the viewer on just the tapped post.
     */
    protected open val filmstripMediaPredicate: ((PostEntity) -> Boolean)? = null

    /**
     * True for the page of a single post. The viewer opened from it is told to return to the page
     * instead of stacking a second copy of it.
     */
    protected open val isPostPage: Boolean = false

    /**
     * On a post page: the Filmstrip session of the screen the page was opened from, or null when
     * there is none. The viewer opened from the page borrows it to reach that screen's other posts.
     */
    protected open val borrowedFeedSessionId: Int? = null

    /**
     * The manager that owns the screen container. A fragment nested in a pager (the tabs of a
     * user or search screen) has a parent manager that belongs to the pager, so full-screen
     * pages must go on the manager of the current navigation destination.
     */
    protected val screenFragmentManager: FragmentManager
        get() = activity?.currentNavigationFragment?.parentFragmentManager ?: parentFragmentManager

    private var filmstripFeedLink: FilmstripFeedLink? = null

    // Remembered across view recreation (opening a subreddit from the viewer destroys this
    // fragment's view while the viewer stays open on the back stack).
    private var filmstripSessionId: Int? = null

    // The session begun for the last post page opened from this feed. No viewer ends it, so this
    // feed does, when it begins another session or goes away.
    private var lentFeedSessionId: Int? = null

    // The feed's list, set while its view exists.
    private var filmstripFeedAdapter: PostListAdapter? = null
    private var filmstripFeedList: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) {
            onBackPressed()
        }
        lentFeedSessionId = savedInstanceState?.getInt(KEY_LENT_FEED_SESSION_ID)?.takeIf { it != 0 }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Kept across rotation so the recreated feed still ends the session it lent.
        lentFeedSessionId?.let { outState.putInt(KEY_LENT_FEED_SESSION_ID, it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets(view)
    }

    protected open fun applyInsets(view: View) {
        view.applyWindowInsets(bottom = false)
    }

    protected open fun onBackPressed() {
        onBackPressedCallback.isEnabled = false
        findNavController().navigateUp()
    }

    /**
     * Opens the Filmstrip viewer on top of this feed. The viewer gets its posts from
     * [adapter] through the activity-wide feed holder and asks it for more pages as the user
     * nears the end; the feed stays in place underneath.
     */
    protected fun openFilmstripViewer(
        post: PostEntity,
        adapter: PostListAdapter,
        list: RecyclerView,
        // A list nested in a tab pager must pass the manager that owns the screen container;
        // its own parent manager belongs to the pager and has no such container.
        fragmentManager: FragmentManager = parentFragmentManager
    ) {
        val isFilmstripMedia = filmstripMediaPredicate ?: return
        val link = beginFeedLink(adapter, list, isFilmstripMedia)

        addViewer(
            fragmentManager,
            FilmstripViewerFragment.newInstance(link.sessionId, post.id, returnsToPostPage = false)
        )
    }

    private fun beginFeedLink(
        adapter: PostListAdapter,
        list: RecyclerView,
        isFilmstripMedia: (PostEntity) -> Boolean
    ): FilmstripFeedLink {
        endLentFeedSession()
        filmstripFeedLink?.dispose()
        val link = FilmstripFeedLink.begin(
            filmstripFeedHolder,
            adapter,
            list,
            isFilmstripMedia,
            viewLifecycleOwner
        )
        filmstripFeedLink = link
        filmstripSessionId = link.sessionId
        return link
    }

    /**
     * The Filmstrip session to lend to a post page opened from this screen, or null when the
     * screen has no feed of media posts. A feed begins a session of its own here.
     */
    protected open fun feedSessionForPostPage(): Int? {
        val isFilmstripMedia = filmstripMediaPredicate ?: return null
        val adapter = filmstripFeedAdapter ?: return null
        val list = filmstripFeedList ?: return null

        val link = beginFeedLink(adapter, list, isFilmstripMedia)
        lentFeedSessionId = link.sessionId
        return link.sessionId
    }

    private fun endLentFeedSession() {
        // The viewers that borrowed it have already told the list which post to reveal.
        lentFeedSessionId?.let { filmstripFeedHolder.endSession(it, lastPostId = null) }
        lentFeedSessionId = null
    }

    /**
     * Opens the viewer for a media tap on a screen that shows a post without a feed of its own.
     * A post page that was lent a feed's session opens the viewer on that feed, so the viewer can
     * swipe on to its other posts; otherwise the viewer shows just [post].
     */
    private fun openSinglePostViewer(post: PostEntity) {
        val feedSessionId = borrowedFeedSessionId?.takeIf { sessionId ->
            filmstripFeedHolder.currentPostsOf(sessionId).any { it.id == post.id }
        }
        if (feedSessionId != null) {
            addViewer(
                screenFragmentManager,
                FilmstripViewerFragment.newInstance(
                    feedSessionId,
                    post.id,
                    returnsToPostPage = true,
                    ownsSession = false
                )
            )
            return
        }

        val sessionId = filmstripFeedHolder.beginSession(listOf(post), canLoadMore = false)
        addViewer(
            screenFragmentManager,
            FilmstripViewerFragment.newInstance(sessionId, post.id, returnsToPostPage = isPostPage)
        )
    }

    /**
     * Opens the viewer on a fixed list of [posts] (a saved or history tab) at [post]. The posts
     * the viewer cannot show are left out. The viewer ends the session when it closes, as it does
     * for a single post.
     */
    protected fun openFilmstripViewerOnPosts(post: PostEntity, posts: List<PostEntity>) {
        val viewerPosts = posts.filter(PostEntity::hasFilmstripMedia)
        if (viewerPosts.none { it.id == post.id }) {
            openSinglePostViewer(post)
            return
        }

        val sessionId = filmstripFeedHolder.beginSession(viewerPosts, canLoadMore = false)
        addViewer(
            screenFragmentManager,
            FilmstripViewerFragment.newInstance(sessionId, post.id, returnsToPostPage = false)
        )
    }

    /** Opens the viewer on a media link that has no post behind it. */
    fun openMediaLink(link: String, mediaType: MediaType) {
        addViewer(screenFragmentManager, FilmstripViewerFragment.newInstance(link, mediaType))
    }

    private fun addViewer(fragmentManager: FragmentManager, viewer: FilmstripViewerFragment) {
        fragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.nav_enter_anim,
                R.anim.nav_exit_anim,
                R.anim.nav_enter_anim,
                R.anim.nav_exit_anim
            )
            .add(R.id.fragment_container, viewer, FilmstripViewerFragment.TAG)
            .addToBackStack(null)
            .commit()
    }

    /**
     * Call after the list's adapter exists. A post page opened from this feed is lent a session on
     * this list, and a viewer opened earlier keeps loading pages.
     */
    protected fun resumeFilmstripFeed(adapter: PostListAdapter, list: RecyclerView) {
        filmstripFeedAdapter = adapter
        filmstripFeedList = list

        val sessionId = filmstripSessionId ?: return
        if (!filmstripFeedHolder.isOpen(sessionId)) return
        val isFilmstripMedia = filmstripMediaPredicate ?: return

        filmstripFeedLink = FilmstripFeedLink.resume(
            filmstripFeedHolder,
            sessionId,
            adapter,
            list,
            isFilmstripMedia,
            viewLifecycleOwner
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        filmstripFeedLink?.dispose()
        filmstripFeedLink = null
        filmstripFeedAdapter = null
        filmstripFeedList = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Rotation recreates the feed while the post page it lent the session to stays open.
        if (activity?.isChangingConfigurations != true) endLentFeedSession()
    }

    protected fun navigate(directions: NavDirections, navOptions: NavOptions = this.navOptions) {
        findNavController().navigate(directions, navOptions)
    }

    protected fun navigate(deepLink: Uri, navOptions: NavOptions = this.navOptions) {
        findNavController().navigate(deepLink, navOptions)
    }

    override fun onClick(post: PostEntity) {
        onClick(parentFragmentManager, post)
    }

    protected open fun onClick(fragmentManager: FragmentManager, post: PostEntity) {
        fragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.nav_enter_anim,
                R.anim.nav_exit_anim,
                R.anim.nav_enter_anim,
                R.anim.nav_exit_anim
            )
            .add(
                R.id.fragment_container,
                PostDetailsFragment.newInstance(post, feedSessionForPostPage()),
                PostDetailsFragment.TAG
            )
            .addToBackStack(null)
            .commit()
    }

    // A feed without a Filmstrip viewer opens the post like a tap on any other layout.
    override fun onFilmstripClick(post: PostEntity) {
        onClick(post)
    }

    override fun onLongClick(post: PostEntity) {
        PostMenuFragment.show(parentFragmentManager, post)
    }

    override fun onMenuClick(post: PostEntity) {
        PostMenuFragment.show(parentFragmentManager, post)
    }

    override fun onUserClick(user: String) {
        openUser(user)
    }

    override fun onSubredditClick(subreddit: String) {
        openSubreddit(subreddit.removePrefix("r/"))
    }

    /**
     * Opens [post] in the Filmstrip viewer when this screen has one and the post is media it
     * shows; returns false so the caller can fall back to the viewer on the single post.
     */
    protected fun openInFilmstripIfMedia(post: PostEntity): Boolean {
        val isFilmstripMedia = filmstripMediaPredicate ?: return false
        if (!isFilmstripMedia(post)) return false

        onFilmstripClick(post)
        return true
    }

    // The viewer records the post in the history itself when it shows the post.
    override fun onImageClick(post: PostEntity) {
        if (!openInFilmstripIfMedia(post)) openSinglePostViewer(post)
    }

    override fun onVideoClick(post: PostEntity) {
        if (!openInFilmstripIfMedia(post)) openSinglePostViewer(post)
    }

    override fun onLinkClick(post: PostEntity) {
        viewModel?.insertPostInHistory(post)
        onLinkClick(post.url)
    }

    override fun onLinkClick(link: String) {
        linkHandler.handleLink(link)
    }

    override fun onLinkLongClick(link: String) {
        LinkMenuFragment.show(parentFragmentManager, link)
    }

    override fun onSaveClick(post: PostEntity) {
        viewModel?.toggleSavePost(post)
    }

    open fun openSubreddit(subreddit: String) {
        navigate(NavigationGraphDirections.openSubreddit(subreddit))
    }

    open fun openUser(user: String) {
        navigate(NavigationGraphDirections.openUser(user))
    }

    open fun openRedditLink(link: String) {
        try {
            navigate(Uri.parse(link).normalizeRedditLink())
        } catch (e: IllegalArgumentException) {
            linkHandler.openBrowser(link)
        }
    }

    companion object {
        private const val KEY_LENT_FEED_SESSION_ID = "KEY_LENT_FEED_SESSION_ID"
    }
}
