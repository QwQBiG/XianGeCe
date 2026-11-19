package win.iqwqi.xiangece.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import win.iqwqi.xiangece.data.local.*

class CalendarMigrationTest {
    @Test fun version9UpgradePreservesExistingTasksAndReopensNotes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "calendar-migration-${UUID.randomUUID()}.db")
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("calendar-migration-v9.json").bufferedReader(Charsets.UTF_8).use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql")
                    .replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            sqlite.execSQL("INSERT INTO tasks VALUES (91, 'existing task', NULL, NULL, 'TODO', 'keep note', NULL, 1000)")
            sqlite.version = 9
        }
        fun open() = Room.databaseBuilder(context, XiangeceDatabase::class.java, file.absolutePath)
            .addMigrations(XiangeceDatabase.MIGRATION_9_10).build()
        try {
            val database = open()
            try {
                assertEquals("existing task", database.campusDao().allTasks().single().title)
                assertTrue(database.campusDao().allCalendarNotes().isEmpty())
                database.campusDao().upsertCalendarNotes(listOf(CalendarNoteEntity(20706, "持久备注")))
            } finally { database.close() }
            val reopened = open()
            try {
                assertEquals("持久备注", reopened.campusDao().allCalendarNotes().single().text)
                assertEquals("keep note", reopened.campusDao().allTasks().single().note)
            } finally { reopened.close() }
        } finally {
            check(file.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            SQLiteDatabase.deleteDatabase(file)
        }
    }
}
