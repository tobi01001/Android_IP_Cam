package com.example.ipcam

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

/**
 * NetworkMonitor - Monitor network connectivity changes
 * 
 * Monitors WiFi connectivity and notifies CameraService
 * to restart the HTTP server when network reconnects.
 */
class NetworkMonitor(
    private val context: Context,
    private val onNetworkAvailable: () -> Unit
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkReceiver: BroadcastReceiver? = null

    fun start() {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Use NetworkCallback for Android 7.0+
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available")
                    onNetworkAvailable()
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost")
                }
            }

            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } else {
            // Use BroadcastReceiver for older Android versions
            networkReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (isNetworkAvailable(context)) {
                        Log.d(TAG, "Network available")
                        onNetworkAvailable()
                    }
                }
            }

            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            context.registerReceiver(networkReceiver, filter)
        }

        Log.d(TAG, "Network monitor started")
    }

    fun stop() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                networkCallback?.let {
                    connectivityManager?.unregisterNetworkCallback(it)
                }
            } else {
                networkReceiver?.let {
                    context.unregisterReceiver(it)
                }
            }
            Log.d(TAG, "Network monitor stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping network monitor", e)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo?.isConnected == true
        }
    }
}
