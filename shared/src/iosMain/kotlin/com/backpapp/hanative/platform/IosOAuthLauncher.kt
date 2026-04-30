package com.backpapp.hanative.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class IosOAuthLauncher : OAuthLauncher {
    override fun launch(authorizeUrl: String) {
        val url = NSURL(string = authorizeUrl)
        UIApplication.sharedApplication.openURL(
            url = url,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }
}
