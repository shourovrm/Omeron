package com.omeron

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.omeron.MainActivity.BottomNavigationState.LEFT_HANDED
import com.omeron.MainActivity.BottomNavigationState.NOT_INITIALIZED
import com.omeron.MainActivity.BottomNavigationState.RIGHT_HANDED
import com.omeron.data.model.db.Profile
import com.omeron.databinding.ActivityMainBinding
import com.omeron.databinding.LayoutDrawerHeaderBinding
import com.omeron.ui.policydisclaimer.PolicyDisclaimerDialogFragment
import com.omeron.ui.postlist.PostListFragment
import com.omeron.ui.profilemanager.ProfileManagerDialogFragment
import com.omeron.util.HideBottomViewBehavior
import com.omeron.util.ShareLinkResolver
import com.omeron.util.UpdateChecker
import com.omeron.util.extension.clearWindowInsetsListener
import com.omeron.util.extension.currentNavigationFragment
import com.omeron.util.extension.isPast
import com.omeron.util.extension.launchRepeat
import com.omeron.util.extension.normalizeRedditLink
import com.omeron.util.extension.unredditApplication
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: UiViewModel by viewModels()

    private lateinit var navController: NavController

    private var bottomNavigationState: BottomNavigationState = NOT_INITIALIZED

    private var policyDisclaimerSnackbar: Snackbar? = null

    private var currentProfile: Profile? = null

    @Inject
    lateinit var updateChecker: UpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(unredditApplication.appTheme)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initNavigation()

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
            checkForUpdate()
        }

        launchRepeat(Lifecycle.State.STARTED) {
            launch {
                viewModel.navigationVisibility
                    // Drop the first item to let initBottomNavigationView manage the visibility
                    .drop(1)
                    .collect(this@MainActivity::showNavigation)
            }

            launch {
                viewModel.leftHandedMode.collect { leftHandedMode ->
                    when (bottomNavigationState) {
                        NOT_INITIALIZED -> initBottomNavigationView(leftHandedMode)
                        RIGHT_HANDED -> if (leftHandedMode) initBottomNavigationView(true)
                        LEFT_HANDED -> if (!leftHandedMode) initBottomNavigationView(false)
                    }
                }
            }

            launch {
                viewModel.policyDisclaimerShown.collect { shown ->
                    if (!shown && POLICY_DISCLAIMER_DATE.isPast) {
                        // Don't show the snackbar right away
                        delay(POLICY_DISCLAIMER_DELAY)
                        showPolicyDisclaimerSnackbar()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val update = updateChecker.check() ?: return@launch

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(getString(R.string.update_available, update.version))
                .setMessage(update.changelog.ifBlank { getString(R.string.update_message) })
                .setPositiveButton(R.string.update_now) { _, _ -> openReleasesPage() }
                .setNegativeButton(R.string.update_later, null)
                .show()
        }
    }

    private fun openReleasesPage() {
        val uri = Uri.parse(RELEASES_PAGE_URL)
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    /**
     * Routes an externally received VIEW (tapped link) or SEND (share sheet) intent to the
     * matching reddit destination, reusing the same deep links [BaseFragment.openRedditLink]
     * relies on for in-app link taps (see navigation_graph.xml / subreddit.xml / user.xml /
     * post.xml deepLinks).
     */
    private fun handleIncomingIntent(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.takeIf { it.type == "text/plain" }
                ?.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { REDDIT_URL_REGEX.find(it)?.value }
                ?.let(Uri::parse)
            else -> null
        } ?: return

        if (ShareLinkResolver.isShareLink(uri)) {
            // /r/{sub}/s/{id} share links are a server-side redirect to the real permalink;
            // resolve it off the main thread, then feed it through the same nav-deepLink path.
            lifecycleScope.launch { navigateToRedditUri(ShareLinkResolver.resolve(uri) ?: uri) }
        } else {
            navigateToRedditUri(uri)
        }
    }

    private fun navigateToRedditUri(uri: Uri) {
        try {
            navController.navigate(uri.normalizeRedditLink())
        } catch (e: IllegalArgumentException) {
            // Not a URL our deep links cover (e.g. wiki/live/chat pages) -> just open to home.
        }
    }

    private fun initNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                as NavHostFragment
        navController = navHostFragment.navController.apply {
            addOnDestinationChangedListener(this@MainActivity)
        }

        binding.bottomNavigation.run {
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home, R.id.popular, R.id.multis -> {
                        viewModel.setHomeTab(HOME_TAB_ITEMS.indexOf(item.itemId))
                        if (navController.currentDestination?.id != R.id.postListFragment) {
                            NavigationUI.onNavDestinationSelected(
                                menu.findItem(R.id.home),
                                navController
                            )
                        }
                        true
                    }

                    else -> NavigationUI.onNavDestinationSelected(item, navController)
                }
            }
            setOnItemReselectedListener {
                when (it.itemId) {
                    R.id.home, R.id.popular, R.id.multis ->
                        (currentNavigationFragment as? PostListFragment)?.scrollToTop()
                    else -> {
                        // Ignore
                    }
                }
            }
        }

        initDrawer()
    }

    private fun initDrawer() {
        binding.navigationView.setupWithNavController(navController)

        val header = LayoutDrawerHeaderBinding.bind(binding.navigationView.getHeaderView(0))
        header.root.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            currentProfile?.let { profile ->
                ProfileManagerDialogFragment.show(supportFragmentManager, profile)
            }
        }

        launchRepeat(Lifecycle.State.STARTED) {
            viewModel.currentProfile.collect { profile ->
                currentProfile = profile
                header.profileName.text = profile.name
                header.profileAvatar.setText(profile.name)
            }
        }
    }

    fun openNavigationDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun closeNavigationDrawer(): Boolean {
        return if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        } else {
            false
        }
    }

    private fun initBottomNavigationView(leftHandedMode: Boolean) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.run {
                updatePadding(bottom = insets.bottom)
                clearWindowInsetsListener()
            }

            windowInsets
        }

        binding.bottomNavigation.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            gravity = Gravity.BOTTOM
            // leftHandedMode only picks the slide-out direction of the full-width bar
            behavior = HideBottomViewBehavior<BottomNavigationView>(leftHandedMode)
        }

        // Wait for the view to be ready to show/hide it (otherwise width could be 0)
        binding.bottomNavigation.post {
            showNavigation(viewModel.navigationVisibility.value, false)
        }

        bottomNavigationState = if (leftHandedMode) LEFT_HANDED else RIGHT_HANDED
    }

    private fun showNavigation(show: Boolean, animate: Boolean = true) {
        val layoutParams = binding.bottomNavigation.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = layoutParams.behavior as HideBottomViewBehavior?

        if (show) {
            behavior?.run {
                enabled = true
                slideIn(binding.bottomNavigation, animate)
            }
        } else {
            behavior?.run {
                enabled = false
                slideOut(binding.bottomNavigation, animate)
            }
        }
    }

    private fun showPolicyDisclaimerSnackbar() {
        policyDisclaimerSnackbar = Snackbar
            .make(
                binding.root,
                getString(
                    R.string.snackbar_policy_disclaimer_message,
                    getString(R.string.app_name)
                ),
                Snackbar.LENGTH_INDEFINITE
            )
            .setAction(R.string.snackbar_policy_disclaimer_action) {
                PolicyDisclaimerDialogFragment.show(supportFragmentManager)
                policyDisclaimerSnackbar = null
            }
            .setActionTextColor(ContextCompat.getColor(this, R.color.white))
            .apply { show() }
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        when (destination.id) {
            R.id.postListFragment,
            R.id.subscriptionsFragment -> {
                viewModel.setNavigationVisibility(true)
            }

            else -> viewModel.setNavigationVisibility(false)
        }

        // The navigation drawer is only reachable from the home screen
        binding.drawerLayout.setDrawerLockMode(
            if (destination.id == R.id.postListFragment) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }
        )

        // Keep the checked bottom bar item in sync on back navigation
        when (destination.id) {
            R.id.postListFragment -> HOME_TAB_ITEMS[viewModel.homeTab.value]
            R.id.subscriptionsFragment -> R.id.subscriptions
            else -> null
        }?.let { binding.bottomNavigation.menu.findItem(it)?.isChecked = true }
    }

    override fun onDestroy() {
        super.onDestroy()
        policyDisclaimerSnackbar = null
        bottomNavigationState = NOT_INITIALIZED
    }

    private enum class BottomNavigationState {
        NOT_INITIALIZED, RIGHT_HANDED, LEFT_HANDED
    }

    companion object {
        private val POLICY_DISCLAIMER_DATE = Calendar
            .getInstance()
            .apply { set(1900 + 123, 5, 10) }
            .timeInMillis

        private const val POLICY_DISCLAIMER_DELAY: Long = 5000

        private const val RELEASES_PAGE_URL = "https://github.com/shourovrm/Omeron/releases/latest"

        private val REDDIT_URL_REGEX = Regex("""https?://\S+""")

        // Index = home tab position (Feed/Popular/Multis)
        private val HOME_TAB_ITEMS = listOf(R.id.home, R.id.popular, R.id.multis)
    }
}
