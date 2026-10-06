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
     * the viewer overrides this once; null (the default) means the screen has no viewer and its
     * media taps open the plain media viewer.
     */
    protected open val filmstripMediaPredicate: ((PostEntity) -> Boolean)? = null

    private var filmstripFeedLink: FilmstripFeedLink? = null

    // Remembered across view recreation (opening a subreddit from the viewer destroys this
    // fragment's view while the viewer stays open on the back stack).
    private var filmstripSessionId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) {
            onBackPressed()
        }
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

        fragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.nav_enter_anim,
                R.anim.nav_exit_anim,
                R.anim.nav_enter_anim,
                R.anim.nav_exit_anim
            )
            .add(
                R.id.fragment_container,
                FilmstripViewerFragment.newInstance(post.id),
                FilmstripViewerFragment.TAG
            )
            .addToBackStack(null)
            .commit()
    }

    /** Call after the list's adapter exists, so a viewer opened earlier keeps loading pages. */
    protected fun resumeFilmstripFeed(adapter: PostListAdapter, list: RecyclerView) {
        val sessionId = filmstripSessionId ?: return
        if (sessionId != filmstripFeedHolder.activeSessionId) return
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
                PostDetailsFragment.newInstance(post),
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
     * shows; returns false so the caller can fall back to the plain media viewer.
     */
    protected fun openInFilmstripIfMedia(post: PostEntity): Boolean {
        val isFilmstripMedia = filmstripMediaPredicate ?: return false
        if (!isFilmstripMedia(post)) return false

        onFilmstripClick(post)
        return true
    }

    override fun onImageClick(post: PostEntity) {
        if (openInFilmstripIfMedia(post)) return
        viewModel?.insertPostInHistory(post)
        if (post.gallery.isNotEmpty()) {
            linkHandler.openGallery(post.gallery)
        } else {
            linkHandler.openMedia(post.mediaUrl, post.mediaType)
        }
    }

    override fun onVideoClick(post: PostEntity) {
        if (openInFilmstripIfMedia(post)) return
        viewModel?.insertPostInHistory(post)
        linkHandler.openMedia(post.mediaUrl, post.mediaType)
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
}
