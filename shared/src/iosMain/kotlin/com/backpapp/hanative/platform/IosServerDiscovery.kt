package com.backpapp.hanative.platform

import com.backpapp.hanative.domain.model.HaServerInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSObject
import platform.Foundation.NSOperationQueue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
class IosServerDiscovery : ServerDiscovery {

    override fun startDiscovery(): Flow<List<HaServerInfo>> = callbackFlow {
        val discovered = mutableListOf<HaServerInfo>()
        val browser = platform.Foundation.NSNetServiceBrowser()
        val services = mutableListOf<platform.Foundation.NSNetService>()

        val delegate = IosNetServiceBrowserDelegate(
            onFound = { service ->
                services.add(service)
                val serviceDelegate = IosNetServiceDelegate(
                    onResolved = { resolved ->
                        val host = resolved.hostName ?: return@IosNetServiceDelegate
                        discovered.removeAll { it.name == resolved.name }
                        discovered.add(HaServerInfo(resolved.name, host, resolved.port.toInt()))
                        trySend(discovered.toList())
                    }
                )
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

private class IosNetServiceBrowserDelegate(
    private val onFound: (platform.Foundation.NSNetService) -> Unit,
    private val onRemoved: (platform.Foundation.NSNetService) -> Unit,
) : NSObject(), platform.Foundation.NSNetServiceBrowserDelegateProtocol {

    override fun netServiceBrowser(
        browser: platform.Foundation.NSNetServiceBrowser,
        didFindService: platform.Foundation.NSNetService,
        moreComing: Boolean,
    ) {
        onFound(didFindService)
    }

    override fun netServiceBrowser(
        browser: platform.Foundation.NSNetServiceBrowser,
        didRemoveService: platform.Foundation.NSNetService,
        moreComing: Boolean,
    ) {
        onRemoved(didRemoveService)
    }
}

private class IosNetServiceDelegate(
    private val onResolved: (platform.Foundation.NSNetService) -> Unit,
) : NSObject(), platform.Foundation.NSNetServiceDelegateProtocol {

    override fun netServiceDidResolveAddress(sender: platform.Foundation.NSNetService) {
        onResolved(sender)
    }

    override fun netService(sender: platform.Foundation.NSNetService, didNotResolve: Map<Any?, *>) {}
}
