package win.iqwqi.xiangece.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import win.iqwqi.xiangece.data.local.CampusDao
import win.iqwqi.xiangece.data.local.CampusEventEntity
import win.iqwqi.xiangece.data.local.CourseEntity
import win.iqwqi.xiangece.data.local.CourseMeetingEntity
import win.iqwqi.xiangece.data.local.GradeRecordEntity
import win.iqwqi.xiangece.data.local.GradeRuleEntity
import win.iqwqi.xiangece.data.local.HabitCheckinEntity
import win.iqwqi.xiangece.data.local.HabitTemplateEntity
import win.iqwqi.xiangece.data.local.InboxItemEntity
import win.iqwqi.xiangece.data.local.OcrSnapshotEntity
import win.iqwqi.xiangece.data.local.PeriodTemplateEntity
import win.iqwqi.xiangece.data.local.ReminderEntity
import win.iqwqi.xiangece.data.local.SemesterEntity
import win.iqwqi.xiangece.data.local.TaskEntity
import win.iqwqi.xiangece.data.local.TimetableEntity
import win.iqwqi.xiangece.data.local.XiangeceDatabase
import win.iqwqi.xiangece.data.settings.AppSettings
import win.iqwqi.xiangece.data.settings.AppSettingsStore

@Serializable
data class BackupPayload(
    val formatVersion: Int = 1,
    val applicationId: String = "win.iqwqi.xiangece",
    val exportedAtEpochMillis: Long = System.currentTimeMillis(),
    val settings: AppSettings,
    val semesters: List<SemesterEntity>,
    val timetables: List<TimetableEntity> = listOf(TimetableEntity(id = 1, name = "默认课表", isCurrent = true)),
    val periods: List<PeriodTemplateEntity>,
    val courses: List<CourseEntity>,
    val meetings: List<CourseMeetingEntity>,
    val tasks: List<TaskEntity>,
    val events: List<CampusEventEntity>,
    val inbox: List<InboxItemEntity>,
    val ocrSnapshots: List<OcrSnapshotEntity>,
    val grades: List<GradeRecordEntity>,
    val gradeRules: List<GradeRuleEntity>,
    val reminders: List<ReminderEntity>,
    val habitTemplates: List<HabitTemplateEntity> = emptyList(),
    val habitCheckins: List<HabitCheckinEntity> = emptyList(),
)

internal object BackupPayloadValidator {
    fun validate(payload: BackupPayload) {
        require(payload.formatVersion == 1) { "暂不支持此备份版本" }
        require(payload.applicationId == "win.iqwqi.xiangece") { "备份不属于弦歌册" }
        require(payload.semesters.size <= 50) { "学期数据过多" }
        require(payload.timetables.size <= 50) { "课表数据过多" }
        require(payload.periods.size <= 50) { "节次数据过多" }
        require(payload.courses.size <= 500) { "课程数据过多" }
        require(payload.meetings.size <= 5_000) { "课表数据过多" }
        require(payload.tasks.size <= 10_000) { "任务数据过多" }
        require(payload.events.size <= 10_000) { "事件数据过多" }
        require(payload.inbox.size <= 10_000) { "收件数据过多" }
        require(payload.ocrSnapshots.size <= 10_000) { "OCR 数据过多" }
        require(payload.grades.size <= 5_000) { "成绩数据过多" }
        require(payload.reminders.size <= 20_000) { "提醒数据过多" }
        require(payload.habitTemplates.size <= 2_000) { "长期事项数据过多" }
        require(payload.habitCheckins.size <= 200_000) { "打卡数据过多" }

        val courseIds = payload.courses.map { it.id }.toSet()
        val timetableIds = payload.timetables.map { it.id }.toSet()
        val inboxIds = payload.inbox.map { it.id }.toSet()
        val habitIds = payload.habitTemplates.map { it.id }.toSet()
        require(courseIds.size == payload.courses.size) { "课程 ID 重复" }
        require(timetableIds.size == payload.timetables.size) { "课表 ID 重复" }
        require(inboxIds.size == payload.inbox.size) { "收件 ID 重复" }
        require(habitIds.size == payload.habitTemplates.size) { "长期事项 ID 重复" }
        require(payload.courses.all { it.timetableId in timetableIds }) { "课程引用了不存在的课表" }
        require(payload.meetings.all { it.timetableId in timetableIds }) { "上课时段引用了不存在的课表" }
        require(payload.meetings.all { it.courseId in courseIds }) { "课表引用了不存在的课程" }
        require(payload.tasks.all { it.courseId == null || it.courseId in courseIds }) { "任务引用了不存在的课程" }
        require(payload.ocrSnapshots.all { it.inboxItemId in inboxIds }) { "OCR 引用了不存在的收件项" }
        require(payload.habitCheckins.all { it.habitId in habitIds }) { "打卡引用了不存在的长期事项" }
        require(payload.periods.all { it.periodIndex in 1..30 && it.startMinutes in 0..1439 && it.endMinutes in 1..1440 }) {
            "节次时间无效"
        }
        require(payload.meetings.all {
            it.dayOfWeek in 1..7 &&
                it.startPeriod in 1..30 &&
                it.endPeriod in it.startPeriod..30 &&
                it.startWeek in 1..52 &&
                it.endWeek in it.startWeek..52
        }) { "课表范围无效" }
    }
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CampusDao,
    private val database: XiangeceDatabase,
    private val settingsStore: AppSettingsStore,
    private val json: Json,
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val settings = settingsStore.settings.first().copy(
            aiEnabled = false,
            encryptedApiKey = "",
        )
        val originalInbox = dao.allInbox()
        val imageFiles = originalInbox.mapNotNull { item ->
            privateInboxFile(item.imagePath)?.let { file ->
                val extension = file.extension.lowercase().filter(Char::isLetterOrDigit).take(8).ifBlank { "jpg" }
                Triple(item.id, "images/${item.id}.$extension", file)
            }
        }
        val imagePathByInbox = imageFiles.associate { (id, path, _) -> id to path }
        val payload = BackupPayload(
            settings = settings,
            semesters = listOfNotNull(dao.currentSemester()),
            timetables = dao.allTimetables(),
            periods = dao.allPeriods(),
            courses = dao.allCourses(),
            meetings = dao.allMeetings(),
            tasks = dao.allTasks(),
            events = dao.allEvents(),
            inbox = originalInbox.map { item ->
                item.copy(imagePath = imagePathByInbox[item.id])
            },
            ocrSnapshots = dao.allOcrSnapshots(),
            grades = dao.allGrades(),
            gradeRules = dao.allGradeRules(),
            reminders = dao.allReminders(),
            habitTemplates = dao.allHabitTemplates(),
            habitCheckins = dao.allHabitCheckins(),
        )
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "无法创建备份文件" }
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                imageFiles.forEach { (_, relativePath, file) ->
                    zip.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun restoreFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val tempDirectory = File(context.cacheDir, "xiangece-restore-${UUID.randomUUID()}").apply { mkdirs() }
        val extractedImages = mutableMapOf<String, File>()
        val createdImages = mutableListOf<File>()
        try {
            var backupJson: ByteArray? = null
            var entryCount = 0
            var totalImageBytes = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取备份文件" }
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        entryCount++
                        require(entryCount <= MAX_ZIP_ENTRIES) { "备份条目过多" }
                        when {
                            entry.isDirectory -> Unit
                            entry.name == "backup.json" -> {
                                require(backupJson == null) { "备份包含重复的数据文件" }
                                backupJson = zip.readLimited(MAX_JSON_BYTES)
                            }
                            IMAGE_ENTRY.matches(entry.name) -> {
                                val target = File(tempDirectory, entry.name.substringAfterLast('/'))
                                val bytes = target.outputStream().use {
                                    zip.copyLimited(it, MAX_IMAGE_BYTES)
                                }
                                totalImageBytes += bytes
                                require(totalImageBytes <= MAX_TOTAL_IMAGE_BYTES) { "备份图片总量过大" }
                                extractedImages[entry.name] = target
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val payloadBytes = requireNotNull(backupJson) { "不是有效的弦歌册备份" }
            val payload = json.decodeFromString<BackupPayload>(payloadBytes.toString(Charsets.UTF_8))
            BackupPayloadValidator.validate(payload)

            val inboxDirectory = File(context.filesDir, "inbox").apply { mkdirs() }.canonicalFile
            val restoredInbox = payload.inbox.map { item ->
                val source = item.imagePath?.let(extractedImages::get)
                if (source == null) {
                    item.copy(imagePath = null)
                } else {
                    val extension = source.extension.filter(Char::isLetterOrDigit).take(8).ifBlank { "jpg" }
                    val target = File(inboxDirectory, "${UUID.randomUUID()}.$extension")
                    source.copyTo(target, overwrite = false)
                    createdImages += target
                    item.copy(imagePath = target.absolutePath)
                }
            }
            val oldImagePaths = dao.allInbox().mapNotNull { it.imagePath }
            database.withTransaction {
                dao.clearReminders()
                dao.clearOcrSnapshots()
                dao.clearInbox()
                dao.clearTasks()
                dao.clearEvents()
                dao.clearMeetings()
                dao.clearCourses()
                dao.clearTimetables()
                dao.clearPeriods()
                dao.clearSemesters()
                dao.clearGrades()
                dao.clearGradeRules()
                dao.clearHabitCheckins()
                dao.clearHabitTemplates()
                payload.timetables.forEach { dao.upsertTimetable(it) }
                dao.upsertCourses(payload.courses)
                dao.upsertMeetings(payload.meetings)
                dao.upsertTasks(payload.tasks)
                dao.upsertEvents(payload.events)
                dao.upsertInbox(restoredInbox)
                dao.upsertOcrSnapshots(payload.ocrSnapshots)
                dao.upsertGrades(payload.grades)
                dao.upsertGradeRules(payload.gradeRules)
                dao.upsertPeriods(payload.periods)
                payload.semesters.forEach { dao.upsertSemester(it) }
                dao.upsertReminders(payload.reminders)
                dao.upsertHabitTemplates(payload.habitTemplates)
                dao.upsertHabitCheckins(payload.habitCheckins)
            }
            settingsStore.restore(payload.settings)
            oldImagePaths.forEach { privateInboxFile(it)?.delete() }
        } catch (error: Throwable) {
            createdImages.forEach(File::delete)
            throw error
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private fun privateInboxFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val directory = File(context.filesDir, "inbox").canonicalFile
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.parentFile == directory && it.isFile }
    }

    private fun InputStream.readLimited(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        copyLimited(output, maxBytes)
        return output.toByteArray()
    }

    private fun InputStream.copyLimited(output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "备份条目过大" }
            output.write(buffer, 0, read)
        }
        return total
    }

    private companion object {
        const val MAX_JSON_BYTES = 20L * 1024 * 1024
        const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
        const val MAX_TOTAL_IMAGE_BYTES = 200L * 1024 * 1024
        const val MAX_ZIP_ENTRIES = 10_100
        val IMAGE_ENTRY = Regex("""images/[A-Za-z0-9._-]{1,80}""")
    }
}
