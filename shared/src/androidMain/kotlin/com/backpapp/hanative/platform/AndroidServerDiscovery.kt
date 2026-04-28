package com.backpapp.hanative.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.backpapp.hanative.domain.model.HaServerInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidServerDiscovery(private val context: Context) : ServerDiscovery {

    override fun startDiscovery(): Flow<List<HaServerInfo>> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val discovered = mutableListOf<HaServerInfo>()

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                discovered.removeAll { it.name == info.serviceName }
                discovered.add(HaServerInfo(info.serviceName, host, info.port))
                trySend(discovered.toList())
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                try { nsdManager.resolveService(info, resolveListener) } catch (_: Exception) {}
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                discovered.removeAll { it.name == info.serviceName }
                trySend(discovered.toList())
            }
        }

        nsdManager.discoverServices("_home-assistant._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
        }
    }

    override fun stopDiscovery() {}
}
