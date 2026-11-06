package com.backlogbattlers.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

//------------------------------
// tells the rest of the app whether the phone has a working internet connection, and when that
// changes. it listens to the system's default network, so switching airplane mode on or off, or
// wifi to mobile data, is seen straight away without polling.
// "working" means the system has checked the network really reaches the internet (VALIDATED), not
// just that it is connected: a wifi with no internet behind it counts as offline.
// pattern: https://developer.android.com/training/monitoring-device-state/connectivity-status-type
class ConnectivityMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(currentlyOnline())

    // true while there is a working connection
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var started = false

    //------------------------------
    // starts listening. safe to call more than once. the callback lives as long as the app does
    fun start() {
        if (started) return
        started = true
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                update(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }

            override fun onLost(network: Network) {
                // the default network went away. if another one takes over, onCapabilitiesChanged follows
                update(false)
            }
        })
    }

    private fun update(online: Boolean) {
        if (_isOnline.value != online) {
            Log.i(TAG, "Connection is now ${if (online) "online" else "offline"}")
            _isOnline.value = online
        }
    }

    private fun currentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private companion object {
        const val TAG = "ConnectivityMonitor"
    }
}
//------------------------------EOF------------------------------\\
