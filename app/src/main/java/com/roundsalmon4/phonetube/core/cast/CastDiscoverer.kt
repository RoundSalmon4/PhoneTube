package com.roundsalmon4.phonetube.core.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds PhoneTV receivers on the local network via mDNS (Network Service
 * Discovery). Each PhoneTV box advertises "_phonetv._tcp." on its WebSocket
 * port, so resolving a service yields the exact address a cast needs anyway.
 */
@Singleton
class CastDiscoverer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var nsdManager: NsdManager? = null
    private val resolving = mutableSetOf<String>()

    private val _nearby = MutableStateFlow<List<CastDevice>>(emptyList())
    val nearby: StateFlow<List<CastDevice>> = _nearby.asStateFlow()

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.i(TAG, "mDNS discovery started for $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceType != SERVICE_TYPE) return
            resolveService(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val gone = serviceInfo.host?.hostAddress
            if (gone != null) {
                _nearby.value = _nearby.value.filterNot { it.host == gone }
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "mDNS discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "mDNS start discovery failed (code $errorCode)")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "mDNS stop discovery failed (code $errorCode)")
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            resolving.remove(serviceInfo.serviceName)
            Log.w(TAG, "mDNS resolve failed for ${serviceInfo.serviceName} (code $errorCode)")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            resolving.remove(serviceInfo.serviceName)
            val address = serviceInfo.host?.hostAddress ?: return
            val device = CastDevice(
                name = serviceInfo.serviceName,
                host = address,
                port = serviceInfo.port.takeIf { it > 0 } ?: 8484
            )
            val current = _nearby.value
            if (current.none { it.host == device.host && it.port == device.port }) {
                _nearby.value = current + device
                Log.i(TAG, "mDNS resolved ${device.name} at ${device.host}:${device.port}")
            }
        }
    }

    fun start() {
        if (nsdManager != null) return
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            nsdManager = manager
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices failed", e)
        }
    }

    fun stop() {
        try {
            nsdManager?.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "stopServiceDiscovery failed", e)
        }
        nsdManager = null
        _nearby.value = emptyList()
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        if (serviceInfo.serviceName in resolving) return
        resolving += serviceInfo.serviceName
        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            resolving.remove(serviceInfo.serviceName)
            Log.w(TAG, "resolveService failed for ${serviceInfo.serviceName}", e)
        }
    }

    companion object {
        private const val TAG = "CastDiscoverer"
        const val SERVICE_TYPE = "_phonetv._tcp."
    }
}