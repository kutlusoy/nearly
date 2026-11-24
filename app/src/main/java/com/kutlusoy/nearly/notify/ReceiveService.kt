package com.kutlusoy.nearly.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.kutlusoy.nearly.AppCore

// Keeps the process alive so the relay connections and the chat keep running while the app is not on screen.
// It needs no Google service. The permanent notification is the price the system asks for.
class ReceiveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = AndroidNotifier(this).serviceNotification()
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING) else startForeground(ID, notification)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        AppCore.get(application)
        return START_STICKY
    }

    companion object {
        private const val ID = 4711

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, ReceiveService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReceiveService::class.java))
        }
    }
}
