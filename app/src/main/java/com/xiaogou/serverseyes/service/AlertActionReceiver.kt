package com.xiaogou.serverseyes.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaogou.serverseyes.ServersEyesApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACKNOWLEDGE_ALERT) return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) {
            runCatching {
                androidx.core.app.NotificationManagerCompat.from(context).cancel(notificationId)
            }
        }
        if (eventId.isNotBlank()) {
            val pendingResult = goAsync()
            val app = context.applicationContext as? ServersEyesApplication
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app?.container?.repository?.acknowledgeAlert(eventId)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE_ALERT = "com.xiaogou.serverseyes.ACTION_ACK_ALERT"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
