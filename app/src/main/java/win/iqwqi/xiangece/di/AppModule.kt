package win.iqwqi.xiangece.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import win.iqwqi.xiangece.data.local.CampusDao
import win.iqwqi.xiangece.data.local.ExpenseDao
import win.iqwqi.xiangece.data.local.XiangeceDatabase
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): XiangeceDatabase =
        Room.databaseBuilder(
            context,
            XiangeceDatabase::class.java,
            "xiangece.db",
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            XiangeceDatabase.MIGRATION_4_5,
        ).build()

    @Provides
    fun provideCampusDao(database: XiangeceDatabase): CampusDao = database.campusDao()

    @Provides
    fun provideExpenseDao(database: XiangeceDatabase): ExpenseDao = database.expenseDao()

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `timetables` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `semesterId` INTEGER, `isCurrent` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `timetables` (`id`, `name`, `semesterId`, `isCurrent`, `createdAtEpochMillis`) VALUES (1, '默认课表', NULL, 1, strftime('%s','now') * 1000)",
            )
            db.execSQL("ALTER TABLE `courses` ADD COLUMN `timetableId` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `course_meetings` ADD COLUMN `timetableId` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `inbox_items` ADD COLUMN `timetableId` INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habit_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `frequency` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habit_checkins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL, `checkinDateEpochDay` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)",
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_quotes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `author` TEXT NOT NULL DEFAULT '', `isBuiltIn` INTEGER NOT NULL DEFAULT 0, `orderIndex` INTEGER NOT NULL DEFAULT 0, `createdAtEpochMillis` INTEGER NOT NULL DEFAULT 0)",
            )
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
                    "INSERT INTO `custom_quotes` (`text`, `author`, `isBuiltIn`, `orderIndex`, `createdAtEpochMillis`) VALUES (?, '', 1, ?, ?)",
                    arrayOf<Any>(text, index, System.currentTimeMillis() + index),
                )
            }
        }
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
