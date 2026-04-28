package com.backpapp.hanative.platform

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

actual class AppLifecycleObserver actual constructor() {
    actual fun onForeground(callback: () -> Unit) {
        NSNotificationCenter.defaultCenter().addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue(),
            usingBlock = { _ -> callback() },
        )
    }
}
