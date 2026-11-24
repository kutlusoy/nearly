package com.kutlusoy.nearly.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kutlusoy.nearly.AppCore

// After a restart of the phone the app opens the saved login and starts receiving again, when the user wants that
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        AppCore.get(context.applicationContext as android.app.Application)
    }
}
