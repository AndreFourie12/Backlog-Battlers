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

    // these screens paint artwork behind the status bar, so they keep the top inset themselves
    private val fullBleedDestinations = setOf(R.id.homeFragment)
    private var contentDrawsBehindStatusBar = false

    //------------------------------
    // Sets up activity layout, edge/edge insets, bottom nav, destination change listeners.\
    override fun onCreate(savedInstanceState: Bundle?) {
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
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val navDivider = findViewById<View>(R.id.navDivider)

        bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            contentDrawsBehindStatusBar = destination.id in fullBleedDestinations
            ViewCompat.requestApplyInsets(root)

            when (destination.id) {
                R.id.splashFragment, R.id.loginFragment -> {
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