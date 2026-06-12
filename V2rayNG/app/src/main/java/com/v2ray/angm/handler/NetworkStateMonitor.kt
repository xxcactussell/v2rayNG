package com.v2ray.angm.handler

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.v2ray.angm.util.LogUtil

object NetworkStateMonitor {
    private var connectivityManager: ConnectivityManager? = null
    private var defaultNetwork: Network? = null
    private var isNetworkAvailable = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            LogUtil.d("NetworkStateMonitor", "Network available: $network")
            defaultNetwork = network
            isNetworkAvailable = true
            onNetworkChangedListener?.invoke(true, network)
        }

        override fun onLost(network: Network) {
            LogUtil.d("NetworkStateMonitor", "Network lost: $network")
            if (defaultNetwork == network) {
                defaultNetwork = null
                isNetworkAvailable = false
            }
            onNetworkChangedListener?.invoke(false, null)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            LogUtil.d("NetworkStateMonitor", "Network capabilities changed: $network, hasInternet=$hasInternet")
        }
    }

    var onNetworkChangedListener: ((isAvailable: Boolean, network: Network?) -> Unit)? = null

    fun init(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, networkCallback)
    }

    fun release() {
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        connectivityManager = null
        defaultNetwork = null
    }

    fun getDefaultNetwork(): Network? = defaultNetwork
    fun isOnline(): Boolean = isNetworkAvailable
}
