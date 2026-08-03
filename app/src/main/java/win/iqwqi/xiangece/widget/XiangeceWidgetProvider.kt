package win.iqwqi.xiangece.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.MainActivity
import win.iqwqi.xiangece.R
import win.iqwqi.xiangece.data.local.CampusDao
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity
import win.iqwqi.xiangece.data.local.SemesterEntity
import win.iqwqi.xiangece.domain.semester.TeachingWeekCalculator

@EntryPoint
@InstallIn(SingletonComponent::class)
interface XiangeceWidgetDependencies {
    fun campusDao(): CampusDao
}

class XiangeceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    XiangeceWidgetDependencies::class.java,
                ).campusDao()
                val model = WidgetModel.load(dao)
                ids.forEach { updateOne(context, manager, it, model) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            updateAll(context)
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            listOf(
                XiangeceWidgetProvider::class.java,
                XiangeceCompactWidgetProvider::class.java,
                XiangeceAgendaWidgetProvider::class.java,
            ).forEach { provider ->
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                if (ids.isNotEmpty()) context.sendBroadcast(
                    Intent(context, provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
            }
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, XiangeceWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) context.sendBroadcast(
                Intent(context, XiangeceWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        private fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            model: WidgetModel,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_date, model.dateLabel)
            views.setTextViewText(R.id.widget_week, model.weekLabel)
            views.setTextViewText(R.id.widget_course, model.courseLabel)
            views.setTextViewText(R.id.widget_location, model.locationLabel)
            views.setTextViewText(R.id.widget_time, model.timeLabel)
            views.setViewVisibility(R.id.widget_time, if (model.hasCourse) android.view.View.VISIBLE else android.view.View.GONE)
            views.setViewVisibility(R.id.widget_location, if (model.hasCourse && model.locationLabel.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE)
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)
            manager.updateAppWidget(id, views)
        }
    }
}

internal data class WidgetModel(
    val dateLabel: String,
    val weekLabel: String,
    val courseLabel: String,
    val locationLabel: String,
    val timeLabel: String,
    val hasCourse: Boolean,
) {
    companion object {
        suspend fun load(dao: CampusDao): WidgetModel {
            val now = java.time.ZonedDateTime.now()
            val today = now.toLocalDate()
            val semester = dao.currentSemester()
            val periods = dao.allPeriods()
            val courses = dao.allCourses().associateBy { it.id }
            val week = semester?.let {
                TeachingWeekCalculator.weekOf(LocalDate.ofEpochDay(it.startDateEpochDay), today, it.weekCount)
            }
            val meetings = if (week != null) dao.allMeetings().filter { it.dayOfWeek == today.dayOfWeek.value && it.activeIn(week) } else emptyList()
            val currentMinutes = now.toLocalTime().toSecondOfDay() / 60
            val next = meetings
                .mapNotNull { meeting ->
                    val period = periods.firstOrNull { it.periodIndex == meeting.startPeriod } ?: return@mapNotNull null
                    if (period.endMinutes <= currentMinutes) return@mapNotNull null
                    meeting to period
                }
                .minByOrNull { it.second.startMinutes }
            val course = next?.first?.let { courses[it.courseId] }
            return WidgetModel(
                dateLabel = "${today.monthValue}月${today.dayOfMonth}日 ${dayLabel(today.dayOfWeek)}",
                weekLabel = if (week != null) "第 ${week} 周" else "尚未设置学期",
                courseLabel = course?.name ?: "今日无课",
                locationLabel = next?.first?.location?.ifBlank { course?.defaultLocation.orEmpty() }.orEmpty(),
                timeLabel = next?.second?.let { "${formatTime(it.startMinutes)}–${formatTime(it.endMinutes)}" }.orEmpty(),
                hasCourse = next != null,
            )
        }

        private fun dayLabel(day: DayOfWeek): String = listOf("一", "二", "三", "四", "五", "六", "日")[day.value - 1]
        private fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
    }
}

private fun CourseMeetingEntity.activeIn(week: Int): Boolean = when (weekParity.name) {
    "ODD" -> week % 2 == 1
    "EVEN" -> week % 2 == 0
    else -> true
}
