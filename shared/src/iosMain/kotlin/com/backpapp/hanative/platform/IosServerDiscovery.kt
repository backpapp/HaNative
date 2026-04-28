package com.backpapp.hanative.platform

import com.backpapp.hanative.domain.model.HaServerInfo
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.darwin.NSObject
import platform.posix.AF_INET
import platform.posix.sockaddr
import platform.posix.sockaddr_in

class IosServerDiscovery : ServerDiscovery {

    override fun startDiscovery(): Flow<List<HaServerInfo>> = callbackFlow {
        val discovered = mutableListOf<HaServerInfo>()
        val browser = NSNetServiceBrowser()
        val services = mutableListOf<NSNetService>()
        val serviceDelegates = mutableListOf<IosNetServiceDelegate>()

        val delegate = IosNetServiceBrowserDelegate(
            onFound = { service ->
                services.add(service)
                val serviceDelegate = IosNetServiceDelegate(
                    onResolved = { resolved ->
                        val host = resolved.resolvedAddress() ?: return@IosNetServiceDelegate
                        discovered.removeAll { it.name == resolved.name }
                        discovered.add(HaServerInfo(resolved.name, host, resolved.port.toInt()))
                        trySend(discovered.toList())
                    }
                )
                serviceDelegates.add(serviceDelegate)
                service.delegate = serviceDelegate
                service.resolveWithTimeout(5.0)
            },
            onRemoved = { service ->
                discovered.removeAll { it.name == service.name }
                trySend(discovered.toList())
            }
        )

        browser.delegate = delegate
        browser.searchForServicesOfType("_home-assistant._tcp.", inDomain = "local.")

        awaitClose { browser.stop() }
    }

    override fun stopDiscovery() {}
}

@OptIn(ExperimentalForeignApi::class)
private fun NSNetService.resolvedAddress(): String? {
    addresses?.forEach { item ->
        val data = item as? NSData ?: return@forEach
        val bytes = data.bytes ?: return@forEach
        val sa = bytes.reinterpret<sockaddr>()
        if (sa.pointed.sa_family.toInt() == AF_INET) {
            val sin = bytes.reinterpret<sockaddr_in>()
            val s = sin.pointed.sin_addr.s_addr  // UInt, network byte order (big-endian)
            return "${(s and 0xFFu).toInt()}.${((s shr 8) and 0xFFu).toInt()}.${((s shr 16) and 0xFFu).toInt()}.${((s shr 24) and 0xFFu).toInt()}"
        }
    }
    return hostName
}

private class IosNetServiceBrowserDelegate(
    private val onFound: (NSNetService) -> Unit,
    private val onRemoved: (NSNetService) -> Unit,
) : NSObject(), platform.Foundation.NSNetServiceBrowserDelegateProtocol {

    @ObjCSignatureOverride
    override fun netServiceBrowser(
        browser: NSNetServiceBrowser,
        didFindService: NSNetService,
        moreComing: Boolean,
    ) {
        onFound(didFindService)
    }

    @ObjCSignatureOverride
    override fun netServiceBrowser(
        browser: NSNetServiceBrowser,
        didRemoveService: NSNetService,
        moreComing: Boolean,
    ) {
        onRemoved(didRemoveService)
    }
}

private class IosNetServiceDelegate(
    private val onResolved: (NSNetService) -> Unit,
) : NSObject(), platform.Foundation.NSNetServiceDelegateProtocol {

    override fun netServiceDidResolveAddress(sender: NSNetService) {
        onResolved(sender)
    }

    override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {}
}
