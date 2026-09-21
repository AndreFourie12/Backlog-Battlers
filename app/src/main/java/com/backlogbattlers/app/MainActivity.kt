package com.backlogbattlers.app

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

// main activity of the application hosting fragment navigation and bottom navigation bar
class MainActivity : AppCompatActivity() {

    //------------------------------
    // Sets up activity layout, edge/edge insets, bottom nav, destination change listeners.\
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val navDivider = findViewById<View>(R.id.navDivider)

        bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                // full screen destinations, and the settings stack, own the
                // whole window rather than sitting above the tab bar
                R.id.splashFragment,
                R.id.loginFragment,
                R.id.settingsFragment,
                R.id.editProfileFragment,
                R.id.notificationSettingsFragment,
                R.id.appearanceFragment,
                R.id.privacyDataFragment,
                R.id.helpSupportFragment,
                R.id.aboutFragment,
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
    }
}
//------------------------------EOF------------------------------\\