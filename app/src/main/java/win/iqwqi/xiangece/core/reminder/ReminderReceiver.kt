package win.iqwqi.xiangece.core.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.MainActivity
import win.iqwqi.xiangece.R
import win.iqwqi.xiangece.data.local.CampusDao

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: CampusDao

    override fun onReceive(context: Context, intent: Intent) {
        // 番茄钟开启免打扰时，仅抑制弦歌册自己的课程/任务提醒，
        // 不修改系统通知权限，也不影响其他应用。
        val pomodoroPrefs = context.getSharedPreferences("pomodoro_state", Context.MODE_PRIVATE)
        val suppressUntil = pomodoroPrefs.getLong("suppress_until", 0L)
        val suppressionActive = pomodoroPrefs.getBoolean("suppress_reminders", false) &&
            (suppressUntil == 0L || suppressUntil > System.currentTimeMillis())
        if (suppressionActive) return
        // 旧版本或异常退出可能留下永久开关；过期后立即清理，避免提醒被静默吞掉。
        if (pomodoroPrefs.getBoolean("suppress_reminders", false)) {
            pomodoroPrefs.edit()
                .putBoolean("suppress_reminders", false)
                .putLong("suppress_until", 0L)
                .apply()
        }
        val reminderId = intent.getLongExtra(EXTRA_ID, -1L)
        if (reminderId > 0) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dao.deleteReminder(reminderId)
                } finally {
                    pendingResult.finish()
                }
            }
        }
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val id = reminderId.takeIf { it > 0 }?.hashCode() ?: System.currentTimeMillis().hashCode()
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: ReminderChannels.TASK
        val openIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE) ?: "弦歌册提醒")
            .setContentText(intent.getStringExtra(EXTRA_BODY).orEmpty())
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_ID = "id"
    }
}
