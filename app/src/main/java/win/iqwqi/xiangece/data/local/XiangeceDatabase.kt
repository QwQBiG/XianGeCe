package win.iqwqi.xiangece.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import win.iqwqi.xiangece.feature.diting.data.DitingDao
import win.iqwqi.xiangece.feature.diting.data.DitingMarkerEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSegmentEntity
import win.iqwqi.xiangece.feature.diting.data.DitingSessionEntity

@Database(
    entities = [
        SemesterEntity::class,
        TimetableEntity::class,
        PeriodTemplateEntity::class,
        CourseEntity::class,
        CourseMeetingEntity::class,
        TaskEntity::class,
        CampusEventEntity::class,
        InboxItemEntity::class,
        OcrSnapshotEntity::class,
        GradeRecordEntity::class,
        GradeRuleEntity::class,
        ReminderEntity::class,
        HabitTemplateEntity::class,
        HabitCheckinEntity::class,
        CustomQuoteEntity::class,
        ExpenseRecordEntity::class,
        DitingSessionEntity::class,
        DitingSegmentEntity::class,
        DitingMarkerEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class XiangeceDatabase : RoomDatabase() {
    abstract fun campusDao(): CampusDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun ditingDao(): DitingDao

    companion object {
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diting_segments ADD COLUMN errorMessage TEXT")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diting_sessions ADD COLUMN aiAnnotationEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diting_sessions ADD COLUMN glossary TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS diting_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    courseId INTEGER,
                    meetingId INTEGER,
                    mode TEXT NOT NULL,
                    languageMode TEXT NOT NULL,
                    status TEXT NOT NULL,
                    audioDirectory TEXT NOT NULL,
                    audioPath TEXT NOT NULL,
                    startedAtEpochMillis INTEGER,
                    endedAtEpochMillis INTEGER,
                    durationMillis INTEGER NOT NULL,
                    audioBytes INTEGER NOT NULL,
                    cloudTranscriptionEnabled INTEGER NOT NULL,
                    transcriptionEngine TEXT NOT NULL,
                    errorMessage TEXT,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diting_sessions_startedAtEpochMillis ON diting_sessions(startedAtEpochMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diting_sessions_courseId ON diting_sessions(courseId)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS diting_segments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId INTEGER NOT NULL,
                    sequence INTEGER NOT NULL,
                    startMillis INTEGER NOT NULL,
                    endMillis INTEGER NOT NULL,
                    audioPath TEXT NOT NULL,
                    text TEXT NOT NULL,
                    rawText TEXT NOT NULL,
                    isFinal INTEGER NOT NULL,
                    confidence REAL,
                    language TEXT NOT NULL,
                    status TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diting_segments_sessionId ON diting_segments(sessionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_diting_segments_sessionId_sequence ON diting_segments(sessionId, sequence)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS diting_markers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId INTEGER NOT NULL,
                    segmentId INTEGER,
                    positionMillis INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    note TEXT NOT NULL,
                    confidence REAL,
                    source TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diting_markers_sessionId ON diting_markers(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diting_markers_segmentId ON diting_markers(segmentId)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS expense_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amountCents INTEGER NOT NULL,
                        type INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        note TEXT NOT NULL,
                        occurredAtEpochMillis INTEGER NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL
                    )""",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_expense_records_occurredAtEpochMillis ON expense_records(occurredAtEpochMillis)",
                )
            }
        }
    }
}
