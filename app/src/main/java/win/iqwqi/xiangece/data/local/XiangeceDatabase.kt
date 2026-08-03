package win.iqwqi.xiangece.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class XiangeceDatabase : RoomDatabase() {
    abstract fun campusDao(): CampusDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS custom_quotes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        text TEXT NOT NULL,
                        author TEXT NOT NULL DEFAULT '',
                        isBuiltIn INTEGER NOT NULL DEFAULT 0,
                        orderIndex INTEGER NOT NULL DEFAULT 0,
                        createdAtEpochMillis INTEGER NOT NULL DEFAULT 0
                    )""",
                )
                // 预置内置箴言
                val builtIns = listOf(
                    "不积跬步，无以至千里；不积小流，无以成江海。",
                    "锲而舍之，朽木不折；锲而不舍，金石可镂。",
                    "千里之行，始于足下。",
                    "天行健，君子以自强不息。",
                    "合抱之木，生于毫末；九层之台，起于累土。",
                    "每日一善，功不唐捐。",
                    "日拱一卒，功不唐捐。",
                    "有志者事竟成。",
                    "业精于勤，荒于嬉。",
                    "博观而约取，厚积而薄发。",
                    "士不可以不弘毅，任重而道远。",
                    "知之者不如好之者，好之者不如乐之者。",
                    "纸上得来终觉浅，绝知此事要躬行。",
                    "千淘万漉虽辛苦，吹尽狂沙始到金。",
                    "宝剑锋从磨砺出，梅花香自苦寒来。",
                    "随风潜入夜，润物细无声。",
                    "读书破万卷，下笔如有神。",
                    "学而不思则罔，思而不学则殆。",
                    "温故而知新，可以为师矣。",
                    "见贤思齐焉，见不贤而内自省也。",
                )
                builtIns.forEachIndexed { index, text ->
                    db.execSQL(
                        "INSERT INTO custom_quotes (text, author, isBuiltIn, orderIndex, createdAtEpochMillis) VALUES (?, ?, 1, ?, ?)",
                        arrayOf<Any>(text, "", index, System.currentTimeMillis() + index),
                    )
                }
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
