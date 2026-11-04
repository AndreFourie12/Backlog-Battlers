package com.backlogbattlers.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import com.backlogbattlers.app.notifications.NotificationPermission
import com.backlogbattlers.app.notifications.PushNotifications
import com.backlogbattlers.app.notifications.PushType
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.backlogbattlers.app.core.ServiceLocator
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

// main activity of the application hosting fragment navigation and bottom navigation bar
class MainActivity : AppCompatActivity() {

    private val fullBleedDestinations = setOf(R.id.homeFragment)
    private var contentDrawsBehindStatusBar = false

    // screens shown before the user is signed in and unlocked. a tapped notification waits until
    // the user is past them, then opens its screen
    private val beforeSignedIn = setOf(R.id.splashFragment, R.id.loginFragment, R.id.biometricLockFragment)

    private lateinit var root: View
    private lateinit var navController: NavController

    // the push whose notification was tapped, until its screen has been opened
    private var pendingPush: PushType? = null
    private var notificationPermissionChecked = false

    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "Notification permission ${if (granted) "granted" else "refused"}")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // has to run before super.onCreate so the launch theme hands over to the app theme
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // only on a fresh start: recreating the activity (a theme or language change) must not
        // open the notification's screen a second time
        if (savedInstanceState == null) pendingPush = PushNotifications.typeFrom(intent)
        PushNotifications.clear(intent)

        root = findViewById(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val top = if (contentDrawsBehindStatusBar) 0 else systemBars.top
            v.setPadding(systemBars.left, top, systemBars.right, systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        // the logo stays up while SplashFragment works out whether to go to login,
        // the fingerprint lock or home, so the user never sees an empty screen in between
        splashScreen.setKeepOnScreenCondition {
            navController.currentDestination?.id == R.id.splashFragment
        }
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val navDivider = findViewById<View>(R.id.navDivider)

        bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d(TAG, "Showing ${resources.getResourceEntryName(destination.id)}")
            contentDrawsBehindStatusBar = destination.id in fullBleedDestinations
            ViewCompat.requestApplyInsets(root)

            when (destination.id) {
                R.id.splashFragment,
                R.id.loginFragment,
                R.id.settingsFragment,
                R.id.editProfileFragment,
                R.id.notificationSettingsFragment,
                R.id.appearanceFragment,
                R.id.privacyDataFragment,
                R.id.helpSupportFragment,
                R.id.aboutFragment,
                R.id.searchFragment,
                R.id.gameDetailFragment,
                -> {
                    bottomNav.visibility = View.GONE
                    navDivider.visibility = View.GONE
                }
                else -> {
                    bottomNav.visibility = View.VISIBLE
                    navDivider.visibility = View.VISIBLE
                }
            }

            if (destination.id == R.id.homeFragment) askForNotificationPermissionOnce()
            openPendingPush()
        }
    }

    //------------------------------
    // a notification was tapped while the app was already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val push = PushNotifications.typeFrom(intent) ?: return
        PushNotifications.clear(intent)
        pendingPush = push
        openPendingPush()
    }

    //------------------------------
    // opens the screen for the tapped notification, once the user is signed in and past the
    // fingerprint lock. home stays underneath, so back returns there
    private fun openPendingPush() {
        val push = pendingPush ?: return
        val current = navController.currentDestination?.id ?: return
        if (current in beforeSignedIn) return
        pendingPush = null

        Log.i(TAG, "Opening the screen for a tapped $push notification")
        // posted, because this can run from inside a navigation callback
        root.post {
            navController.navigate(
                PushNotifications.destinationOf(push),
                PushNotifications.argumentsOf(push),
                navOptions {
                    popUpTo(R.id.homeFragment) { inclusive = false }
                    launchSingleTop = true
                },
            )
        }
    }

    //------------------------------
    // on android 13+, the first time home is reached, explains what notifications are for and
    // then shows the system prompt. never asked again after that (see NotificationPermission)
    private fun askForNotificationPermissionOnce() {
        if (notificationPermissionChecked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        notificationPermissionChecked = true

        lifecycleScope.launch {
            val shouldAsk = withContext(Dispatchers.IO) { NotificationPermission.shouldAsk(this@MainActivity) }
            if (!shouldAsk) return@launch
            withContext(Dispatchers.IO) { NotificationPermission.markAsked(this@MainActivity) }

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_body)
                .setNegativeButton(R.string.notification_permission_decline) { _, _ ->
                    Log.i(TAG, "Notifications declined before the system prompt")
                }
                .setPositiveButton(R.string.notification_permission_allow) { _, _ ->
                    notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .show()
        }

        // the backend stopped accepting the session (its refresh token was rejected): back to login
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { ServiceLocator.sessionEnded.collect { backToLogin(navController) } }

                // a session can also end while the app is in the background, when nothing is listening.
                // login and splash are the only screens a signed out user belongs on; the storage is
                // only touched once past them, because it is slow to build and splash avoids that
                val destination = navController.currentDestination?.id
                if (destination != R.id.loginFragment && destination != R.id.splashFragment &&
                    ServiceLocator.tokenStorage.getUserId() == null
                ) {
                    backToLogin(navController)
                }
            }
        }
    }

    //------------------------------
    // clears the back stack down to the login screen and says why the user is there
    private fun backToLogin(navController: NavController) {
        val destination = navController.currentDestination?.id
        if (destination == R.id.loginFragment || destination == R.id.splashFragment) return

        Log.i(TAG, "The session has ended, going back to login")
        Toast.makeText(this, R.string.session_expired, Toast.LENGTH_LONG).show()
        navController.navigate(R.id.action_global_loginFragment)
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
//------------------------------EOF------------------------------\\
