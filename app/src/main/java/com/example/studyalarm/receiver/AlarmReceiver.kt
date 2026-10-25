package com.example.studyalarm.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.studyalarm.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "alarm_full_screen_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 1. 取得 WakeLock 強行讓 CPU 保持運作 (10 秒)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "StudyAlarm:WakeLockTag",
        )
        wakeLock?.acquire(10000L)

        // 2. 建立跳轉至 AlarmActivity 的 PendingIntent
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 3. 建立高優先級的通知管道 (NotificationChannel)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "鬧鐘響鈴服務",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "用於鬧鐘時間到達時觸發全螢幕喚醒"
            setBypassDnd(true) // 繞過勿擾模式
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        // 4. 使用 setFullScreenIntent 繞過 Android 背景啟動 Activity 的限制
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ 鬧鐘響起！")
            .setContentText("請點擊或解鎖以開始背誦測驗")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true) // 🟢 關鍵：系統授權全螢幕彈出

        notificationManager.notify(NOTIFICATION_ID, builder.build())

        // 5. 若螢幕當前為開啟狀態，則同步嘗試 direct startActivity 作為雙重保險
        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}