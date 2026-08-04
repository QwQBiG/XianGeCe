package win.iqwqi.xiangece.core.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import win.iqwqi.xiangece.R
import win.iqwqi.xiangece.data.local.CampusDao
import win.iqwqi.xiangece.data.local.CampusEventEntity
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity
import win.iqwqi.xiangece.data.local.ReminderEntity
import win.iqwqi.xiangece.data.local.SemesterEntity
import win.iqwqi.xiangece.data.local.TaskEntity
import win.iqwqi.xiangece.domain.semester.ReminderTimeCalculator

object ReminderChannels {
    const val COURSE = "course_reminders"
    const val TASK = "task_reminders"
    const val EVENT = "event_reminders"
    const val MISC = "misc"
    /** 独立测试频道，避免旧版本被用户关闭后测试永远静默。 */
    const val TEST = "test_notifications_v2"
}

object ReminderTargets {
    const val COURSE = "course"
    const val TASK = "task"
    const val EVENT = "event"
}

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CampusDao,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    init {
        createChannels()
    }

    suspend fun scheduleTask(task: TaskEntity, firstHours: Int, secondHours: Int) {
        cancelTarget(ReminderTargets.TASK, task.id)
        val due = task.dueAtEpochMillis ?: return
        ReminderTimeCalculator.offsetTriggers(due, listOf(firstHours, secondHours)).forEach { (hours, trigger) ->
            persistAndSchedule(
                ReminderEntity(
                    targetType = ReminderTargets.TASK,
                    targetId = task.id,
                    triggerAtEpochMillis = trigger,
                    title = task.title,
                    body = if (hours == 0) "任务现在截止" else "距离截止还有${hours}小时",
                    channel = ReminderChannels.TASK,
                ),
            )
        }
    }

    suspend fun scheduleCourse(
        course: CourseEntity,
        meeting: CourseMeetingEntity,
        semester: SemesterEntity,
        periods: List<PeriodTemplateEntity>,
        minutesBefore: Int,
    ) {
        cancelTarget(ReminderTargets.COURSE, meeting.id)
        val startPeriod = periods.firstOrNull { it.periodIndex == meeting.startPeriod } ?: return
        ReminderTimeCalculator.courseTriggers(
            meeting = meeting,
            semester = semester,
            startMinutes = startPeriod.startMinutes,
            minutesBefore = minutesBefore,
        )
            .sorted()
            .take(MAX_UPCOMING_COURSE_REMINDERS)
            .forEach { trigger ->
            persistAndSchedule(
                ReminderEntity(
                    targetType = ReminderTargets.COURSE,
                    targetId = meeting.id,
                    triggerAtEpochMillis = trigger,
                    title = course.name,
                    body = "${minutesBefore}分钟后上课 · ${meeting.location.ifBlank { course.defaultLocation }}",
                    channel = ReminderChannels.COURSE,
                ),
            )
        }
    }

    suspend fun scheduleEvent(event: CampusEventEntity, firstHours: Int, secondHours: Int) {
        cancelTarget(ReminderTargets.EVENT, event.id)
        ReminderTimeCalculator.offsetTriggers(
            event.startsAtEpochMillis,
            listOf(firstHours, secondHours),
        ).forEach { (hours, trigger) ->
            persistAndSchedule(
                ReminderEntity(
                    targetType = ReminderTargets.EVENT,
                    targetId = event.id,
                    triggerAtEpochMillis = trigger,
                    title = event.title,
                    body = if (hours == 0) "事件即将开始" else "距离开始还有${hours}小时",
                    channel = ReminderChannels.EVENT,
                ),
            )
        }
    }

    suspend fun persistAndSchedule(reminder: ReminderEntity) {
        val id = dao.upsertReminder(reminder)
        schedule(reminder.copy(id = id))
    }

    suspend fun restore(reminders: List<ReminderEntity>) {
        dao.deleteExpiredReminders()
        reminders.filter { it.enabled && it.triggerAtEpochMillis > System.currentTimeMillis() }
            .forEach(::schedule)
    }

    suspend fun cancelTarget(targetType: String, targetId: Long) {
        val reminders = dao.remindersForTarget(targetType, targetId)
        cancelAlarms(reminders)
        dao.deleteRemindersForTarget(targetType, targetId)
    }

    suspend fun setEnabled(reminder: ReminderEntity, enabled: Boolean): Boolean {
        cancelAlarms(listOf(reminder))
        val canEnable = enabled && reminder.triggerAtEpochMillis > System.currentTimeMillis()
        dao.upsertReminder(reminder.copy(enabled = canEnable))
        if (canEnable) schedule(reminder.copy(enabled = true))
        return !enabled || canEnable
    }

    suspend fun delete(reminder: ReminderEntity) {
        cancelAlarms(listOf(reminder))
        dao.deleteReminder(reminder.id)
    }

    fun cancelAlarms(reminders: List<ReminderEntity>) {
        reminders.forEach { reminder ->
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun schedule(reminder: ReminderEntity) {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
            .putExtra(ReminderReceiver.EXTRA_BODY, reminder.body)
            .putExtra(ReminderReceiver.EXTRA_CHANNEL, reminder.channel)
            .putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAtEpochMillis,
            pendingIntent,
        )
    }

    fun sendTestNotification(onResult: (String) -> Unit = {}) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                onResult("通知权限未开启，请在系统设置中允许弦歌册发送通知")
                return@postDelayed
            }
            val notifications = NotificationManagerCompat.from(context)
            if (!notifications.areNotificationsEnabled()) {
                onResult("系统通知总开关已关闭，请先开启弦歌册通知")
                return@postDelayed
            }
            createTestChannel()
            if (context.getSystemService(NotificationManager::class.java)
                    ?.getNotificationChannel(ReminderChannels.TEST)?.importance == NotificationManager.IMPORTANCE_NONE
            ) {
                onResult("测试通知频道已被系统关闭，请在通知设置中重新开启")
                return@postDelayed
            }
            val openIntent = PendingIntent.getActivity(
                context,
                999999,
                Intent(context, win.iqwqi.xiangece.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, ReminderChannels.TEST)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("弦歌册测试")
                .setContentText("测试通知已发送，请检查系统通知栏")
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            runCatching {
                notifications.notify(999999, notification)
            }.onSuccess {
                onResult("测试通知已发送，请检查系统通知栏")
            }.onFailure {
                onResult("通知发送失败：${it.message ?: "系统拒绝了通知"}")
            }
        }, 3000L)
    }

    private fun createTestChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                ReminderChannels.TEST,
                "测试通知",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "用于验证弦歌册通知权限与系统通知栏"
                enableVibration(true)
                setShowBadge(true)
            },
        )
    }

    private fun sendTestNotificationLegacy() {
        val testId = 999999L
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TITLE, "弦歌册测试")
            .putExtra(ReminderReceiver.EXTRA_BODY, "这是一条测试通知，如果你看到了说明提醒功能正常 ✓")
            .putExtra(ReminderReceiver.EXTRA_CHANNEL, ReminderChannels.MISC)
            .putExtra(ReminderReceiver.EXTRA_ID, testId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            testId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 3秒后触发测试通知
        val triggerTime = System.currentTimeMillis() + 3000
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent,
        )
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    ReminderChannels.COURSE,
                    context.getString(R.string.notification_channel_course),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    ReminderChannels.TASK,
                    context.getString(R.string.notification_channel_task),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    ReminderChannels.MISC,
                    "弦歌册测试通知",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    ReminderChannels.TEST,
                    "测试通知",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    ReminderChannels.EVENT,
                    "校园事件提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    private companion object {
        // Android vendors commonly cap alarms per app UID at 500. A timetable can
        // otherwise create hundreds of alarms in one import.
        const val MAX_UPCOMING_COURSE_REMINDERS = 2
    }
}
