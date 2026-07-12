package com.iponlove.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Whether the device currently has an internet-capable network. Reusable app-wide (per the
 * scalability principle) — the first consumer is Reset finances (ADR-0037), which blocks its
 * confirm while offline; sync-status surfaces (v1.6.5 Item 9) will want it too.
 *
 * "Capable" (NET_CAPABILITY_INTERNET), not "validated" — this only distinguishes "no
 * connection at all" from "connected." Actual server reachability stays the network call's
 * job (e.g. the reset's re-auth `signIn`), which is the real guarantee behind this UX gate.
 */
interface ConnectivityObserver {
    /** Emits the current online state immediately, then on every network change. */
    fun observe(): Flow<Boolean>

    /** One-shot read of the current online state. */
    fun isOnline(): Boolean
}

class AndroidConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectivityObserver {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val network = manager?.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun observe(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline()) }
            override fun onLost(network: Network) { trySend(isOnline()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(isOnline())
            }
        }
        trySend(isOnline()) // seed the current state before any change fires
        manager?.registerDefaultNetworkCallback(callback)
        awaitClose { manager?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
