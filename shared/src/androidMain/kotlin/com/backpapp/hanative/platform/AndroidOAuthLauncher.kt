package com.backpapp.hanative.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidOAuthLauncher(private val context: Context) : OAuthLauncher {
    override fun launch(authorizeUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
