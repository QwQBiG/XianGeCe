package win.iqwqi.xiangece.feature.notebook

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import win.iqwqi.xiangece.feature.notebook.data.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NotebookFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun saveSurvivesReopenAndProtectsConcurrentEdit() {
        val root = temporary.newFolder()
        val store = NotebookFileStore(root)
        val note = store.create("课程随记", text = "第一版")
        store.save(note.record.id, "第二版", note.revision)
        assertThrows(NoteConflict::class.java) { store.save(note.record.id, "过期编辑", note.revision) }
        assertEquals("第二版", NotebookFileStore(root).read(note.record.id).text)
        assertEquals("第一版", store.history(note.record.id).first().text)
    }
    @Test fun trashRestoreAndDraftKeepContent() {
        val store = NotebookFileStore(temporary.newFolder())
        val note = store.create("记录", "学习", "内容")
        store.writeDraft(NoteDraft(note.record.id, "未写完", note.revision, 1))
        store.trash(note.record.id)
        assertTrue(store.list().isEmpty())
        assertEquals("未写完", store.draft(note.record.id)?.text)
        assertEquals("内容", store.restore(note.record.id).text)
    }
    @Test fun archiveRoundTripAndTraversalNeverPublishesPartialImport() {
        val first = NotebookFileStore(temporary.newFolder())
        first.create("记录", "文件夹", "# 内容\n中文")
        val bytes = ByteArrayOutputStream().also { NotebookArchive.export(first, it) }.toByteArray()
        val second = NotebookFileStore(temporary.newFolder())
        assertEquals("# 内容\n中文", NotebookArchive.import(second, bytes.inputStream()).single().text)
        val bad = ByteArrayOutputStream()
        ZipOutputStream(bad).use { zip ->
            for (name in listOf("good.md", "../escape.md")) { zip.putNextEntry(ZipEntry(name)); zip.write("x".toByteArray()); zip.closeEntry() }
        }
        assertThrows(IllegalArgumentException::class.java) { NotebookArchive.import(second, bad.toByteArray().inputStream()) }
        assertEquals(1, second.list().size)
    }
}
