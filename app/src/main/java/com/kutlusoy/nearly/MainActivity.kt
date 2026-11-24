package com.kutlusoy.nearly

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kutlusoy.nearly.notify.AndroidNotifier
import com.kutlusoy.nearly.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val core = AppCore.get(application)
        setContent { AppRoot(core) }
        open(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    // A tap on a notification opens the chat of the person
    private fun open(intent: Intent?) {
        intent?.getStringExtra(AndroidNotifier.EXTRA_PEER)?.takeIf { it.length == 64 }?.let { AppCore.get(application).openFromNotification(it) }
    }
}
