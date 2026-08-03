package win.iqwqi.xiangece.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import win.iqwqi.xiangece.MainActivity
import win.iqwqi.xiangece.R
import win.iqwqi.xiangece.data.local.CampusDao
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.domain.semester.TeachingWeekCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class XiangeceCompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    XiangeceWidgetDependencies::class.java,
                ).campusDao()
                val model = WidgetModel.load(dao)
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_compact)
                    views.setTextViewText(R.id.compact_course, model.courseLabel)
                    views.setTextViewText(R.id.compact_time, if (model.hasCourse) model.timeLabel else model.weekLabel)
                    views.setOnClickPendingIntent(R.id.compact_root, openApp(context, 100 + id))
                    manager.updateAppWidget(id, views)
                }
            } finally {
                result.finish()
            }
        }
    }
}

class XiangeceAgendaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    XiangeceWidgetDependencies::class.java,
                ).campusDao()
                val rows = AgendaModel.load(dao)
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_agenda)
                    val values = rows + List(3 - rows.size) { AgendaRow("暂无更多课程", "", "") }
                    listOf(R.id.agenda_row_1, R.id.agenda_row_2, R.id.agenda_row_3).forEachIndexed { index, rowId ->
                        views.setTextViewText(rowId, "${values[index].title}\n${values[index].time}${if (values[index].location.isNotBlank()) " · ${values[index].location}" else ""}")
                    }
                    views.setOnClickPendingIntent(R.id.agenda_root, openApp(context, 200 + id))
                    manager.updateAppWidget(id, views)
                }
            } finally {
                result.finish()
            }
        }
    }
}

private fun openApp(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private data class AgendaRow(val title: String, val time: String, val location: String)

private object AgendaModel {
    suspend fun load(dao: CampusDao): List<AgendaRow> {
        val now = java.time.ZonedDateTime.now()
        val date = now.toLocalDate()
        val semester = dao.currentSemester() ?: return emptyList()
        val week = TeachingWeekCalculator.weekOf(
            java.time.LocalDate.ofEpochDay(semester.startDateEpochDay), date, semester.weekCount,
        )
        val periods = dao.allPeriods().associateBy { it.periodIndex }
        val courses = dao.allCourses().associateBy { it.id }
        val minutes = now.hour * 60 + now.minute
        return dao.allMeetings()
            .filter { it.dayOfWeek == date.dayOfWeek.value && it.activeInAgenda(week) }
            .mapNotNull { meeting ->
                val period = periods[meeting.startPeriod] ?: return@mapNotNull null
                if (period.endMinutes <= minutes) return@mapNotNull null
                val course = courses[meeting.courseId]
                AgendaRow(course?.name ?: "未命名课程", "%02d:%02d".format(period.startMinutes / 60, period.startMinutes % 60), meeting.location.ifBlank { course?.defaultLocation.orEmpty() })
            }
            .sortedBy { it.time }
            .take(3)
    }
}

private fun CourseMeetingEntity.activeInAgenda(week: Int): Boolean =
    week in startWeek..endWeek && when (weekParity.name) {
        "ODD" -> week % 2 == 1
        "EVEN" -> week % 2 == 0
        else -> true
    }
