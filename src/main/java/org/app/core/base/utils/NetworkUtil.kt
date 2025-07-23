package org.app.core.base.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.IntRange

class NetworkUtil {
    companion object {
        private const val NETWORK_STATUS_NOT_CONNECTED = 0
        private const val NETWORK_STATUS_WIFI = 1
        private const val NETWORK_STATUS_MOBILE = 2
        private const val NETWORK_STATUS_VPN = 3
        
        @IntRange(from = 0, to = 3)
        fun getConnectivityStatus(context: Context): Int {
            var result =
                NETWORK_STATUS_NOT_CONNECTED // Returns connection type. 0: none; 1: mobile data; 2: wifi
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (manager != null) {
                val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        result = NETWORK_STATUS_WIFI
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        result = NETWORK_STATUS_MOBILE
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        result = NETWORK_STATUS_VPN
                    }
                }
            }
            return result
        }
        
        fun isNetworkAvailable(context: Context): Boolean {
            return try {
                getConnectivityStatus(context) != NETWORK_STATUS_NOT_CONNECTED
            } catch (_: Exception) {
                false
            }
        }
        
        fun isNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm ?: return false

            return cm.activeNetworkInfo != null && cm.activeNetworkInfo!!.isConnected
        }
    }
}