package com.luizeduardobrandao.obra.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

fun Context.isConnectedToInternet(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java)
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    // INTERNET + VALIDATED garante internet de fato
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

fun Context.registerConnectivityCallback(
    onAvailable: () -> Unit,
    onLost: () -> Unit
): ConnectivityManager.NetworkCallback {
    val cm = getSystemService(ConnectivityManager::class.java)
    val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network)  = onAvailable()
        override fun onLost(network: Network)       = onLost()
    }
    cm.registerDefaultNetworkCallback(cb)
    return cb
}

fun Context.unregisterConnectivityCallback(cb: ConnectivityManager.NetworkCallback?) {
    if (cb == null) return
    val cm = getSystemService(ConnectivityManager::class.java)
    runCatching { cm.unregisterNetworkCallback(cb) }
}