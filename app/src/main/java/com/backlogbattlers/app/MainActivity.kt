package com.backlogbattlers.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.backlogbattlers.app.core.ServiceLocator
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

// main activity of the application hosting fragment navigation and bottom navigation bar
class MainActivity : AppCompatActivity() {

    private val fullBleedDestinations = setOf(R.id.homeFragment)
    private var contentDrawsBehindStatusBar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // has to run before super.onCreate so the launch theme hands over to the app theme
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val top = if (contentDrawsBehindStatusBar) 0 else systemBars.top
            v.setPadding(systemBars.left, top, systemBars.right, systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
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