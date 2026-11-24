package com.kutlusoy.nearly.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kutlusoy.nearly.MainActivity
import com.kutlusoy.nearly.R

class AndroidNotifier(private val context: Context) : Notifier {
    private val posted = java.util.Collections.synchronizedSet(HashSet<Int>())
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.notify_channel_messages), NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SERVICE, context.getString(R.string.notify_channel_service), NotificationManager.IMPORTANCE_MIN))
    }

    override fun permitted(): Boolean = manager.areNotificationsEnabled()

    override fun message(peer: String, name: String?, text: String?, photo: Boolean) {
        val content = text?.takeIf { it.isNotBlank() } ?: context.getString(if (photo && name != null) R.string.notify_photo else R.string.notify_new_message)
        post(peer, name ?: context.getString(R.string.app_name), content, peer, context.getString(R.string.notify_new_message))
    }

    override fun match(peer: String, name: String?) {
        val content = if (name != null) context.getString(R.string.match_text, name) else context.getString(R.string.notify_new_match)
        post("match:$peer", context.getString(R.string.match_title), content, peer, context.getString(R.string.notify_new_match))
    }

    // A private version without the name and the text is what the lock screen shows when the phone is set to hide content
    private fun post(key: String, title: String, text: String, peer: String, generic: String) {
        if (!permitted()) return
        ensureChannels()
        val open = PendingIntent.getActivity(
            context, key.hashCode(), Intent(context, MainActivity::class.java).putExtra(EXTRA_PEER, peer).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = Notification.Builder(context, CHANNEL_MESSAGES).setSmallIcon(R.drawable.ic_stat_nearly).setContentTitle(context.getString(R.string.app_name)).setContentText(generic).build()
        val notification = Notification.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_nearly).setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
            .build()
        manager.notify(key.hashCode(), notification)
        posted.add(key.hashCode())
    }

    override fun clear(peer: String) {
        manager.cancel(peer.hashCode())
        manager.cancel("match:$peer".hashCode())
        posted.remove(peer.hashCode())
        posted.remove("match:$peer".hashCode())
    }

    override fun clearAll() {
        synchronized(posted) { posted.forEach { manager.cancel(it) }; posted.clear() }
    }

    override fun background(on: Boolean) {
        if (on) ReceiveService.start(context) else ReceiveService.stop(context)
    }

    // The permanent notification of the receiving service
    fun serviceNotification(): Notification {
        ensureChannels()
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_nearly).setContentTitle(context.getString(R.string.notify_service_title)).setContentText(context.getString(R.string.notify_service_text))
            .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build()
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_SERVICE = "receiving"
        const val EXTRA_PEER = "peer"
    }
}
