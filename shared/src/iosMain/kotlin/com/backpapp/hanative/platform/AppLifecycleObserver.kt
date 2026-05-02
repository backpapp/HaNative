package com.backpapp.hanative.platform

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

actual class AppLifecycleObserver actual constructor() : LifecycleForegrounder {
    private var token: Any? = null

    actual override fun onForeground(callback: () -> Unit) {
        token?.let { NSNotificationCenter.defaultCenter().removeObserver(it) }
        token = NSNotificationCenter.defaultCenter().addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue(),
            usingBlock = { _ -> callback() },
        )
    }
}
