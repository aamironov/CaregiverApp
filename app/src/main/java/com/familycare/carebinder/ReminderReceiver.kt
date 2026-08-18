package com.familycare.carebinder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Care plan reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Care plan reminder"
        manager.notify(intent.getIntExtra(EXTRA_ID, 0), NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("CareBinder").setContentText(title).setAutoCancel(true).build())
    }

    companion object { const val CHANNEL_ID = "care_plan_reminders"; const val EXTRA_TITLE = "title"; const val EXTRA_ID = "id" }
}

internal fun scheduleReminder(context: Context, task: UiTask) {
    val value = task.reminderAt ?: return
    val triggerAt = runCatching { Instant.parse(value).toEpochMilli() }.getOrElse { runCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull() } ?: return
    if (triggerAt <= System.currentTimeMillis()) return
    val requestCode = task.id.hashCode()
    val intent = Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_TITLE, task.title).putExtra(ReminderReceiver.EXTRA_ID, requestCode)
    val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
}
