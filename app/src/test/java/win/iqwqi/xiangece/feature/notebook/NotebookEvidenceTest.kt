package win.iqwqi.xiangece.feature.notebook

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.*
import win.iqwqi.xiangece.feature.notebook.data.*
import win.iqwqi.xiangece.feature.notebook.domain.*

class NotebookEvidenceTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun inventedIdsRewrittenQuotesAndMissingEvidenceAreRejected() {
        val raw = """{"points":[
            {"text":"关闭重复的顶部间距","evidence":[{"id":"p1","quote":"移除重复的状态栏内边距"}]},
            {"text":"虚构内容","evidence":[{"id":"fake","quote":"移除重复的状态栏内边距"}]},
            {"text":"变造引用","evidence":[{"id":"p1","quote":"去掉所有系统边距"}]},
            {"text":"没有来源","evidence":[]}],"missing":"设备行为需要核对"}"""
        val result = NotebookEvidence.check(raw, mapOf("p1" to "需要移除重复的状态栏内边距，保留按钮安全区域。"))
        assertEquals(1, result.points.size)
        assertEquals(3, result.rejected)
        assertEquals("设备行为需要核对", result.missing)
    }
    @Test fun referencesStayBoundToTextRevisionAndTrashState() {
        val store = NotebookFileStore(temporary.newFolder())
        val note = store.create("状态栏", text = "需要移除重复的状态栏内边距。")
        val passage = NotebookEvidence.passages(listOf(note), "状态栏", emptyList()).single()
        assertTrue(NotebookEvidence.stillCurrent(passage, note))
        val edited = store.save(note.record.id, "新的方法", note.revision)
        assertFalse(NotebookEvidence.stillCurrent(passage, edited))
        assertFalse(NotebookEvidence.stillCurrent(passage, note.copy(record = note.record.copy(deletedAt = 1))))
        assertFalse(NotebookEvidence.stillCurrent(passage, null))
    }
    @Test fun naturalLanguageExpansionFindsOnlyExistingPassages() {
        val store = NotebookFileStore(temporary.newFolder())
        val desired = store.create("显示设置", text = "通过 WindowInsets 设置状态栏安全区。")
        val other = store.create("午饭", text = "今天吃面条。")
        val terms = NotebookEvidence.expansion("""{"terms":["状态栏","WindowInsets","状态栏",""]}""")
        assertEquals(listOf("状态栏", "WindowInsets"), terms)
        assertEquals(desired.record.id, NotebookEvidence.passages(listOf(desired, other), "顶部被挡住", terms).single().noteId)
    }
    @Test fun collectionIsIdempotentAndBodyEditsDoNotChangeSources() {
        val root = temporary.newFolder()
        val store = NotebookFileStore(root)
        val source = NoteSource("original", "原始材料", "text", listOf(NoteSourceBlock("p1", "用户最初提供的内容")))
        val a = store.create("收集", "收集", "整理内容", listOf(source), "collection-1")
        val b = store.create("收集", "收集", "另一种内容", listOf(source), "collection-1")
        assertEquals(a.record.id, b.record.id)
        assertEquals(1, store.list().size)
        store.save(a.record.id, "用户修改了笔记", a.revision)
        assertEquals("用户最初提供的内容", NotebookFileStore(root).read(a.record.id).record.sources.single().text)
    }
    @Test fun archivesKeepSourcesButNeverReuseForeignClassIdsOrUncollectedImages() {
        val store = NotebookFileStore(temporary.newFolder())
        val picture = store.addImage(byteArrayOf(1, 2), "png")
        val pendingImage = store.addImage(byteArrayOf(3, 4), "png")
        val source = NoteSource("class1", "课堂来源", "class", listOf(NoteSourceBlock("s1", "矩阵求逆的条件")),
            imagePath = picture, sessionId = 42, sessionCreatedAt = 100)
        store.create("笔记", "收集", "正文", listOf(source))
        val trashed = store.create("不导出的笔记", text = "内容", sources = listOf(source.copy(id = "private", title = "回收站私有来源")))
        store.trash(trashed.record.id)
        val bytes = ByteArrayOutputStream().also { NotebookArchive.export(store, it) }.toByteArray()
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip -> var entry = zip.nextEntry
            while (entry != null) { entries[entry.name] = zip.readBytes(); entry = zip.nextEntry }
        }
        assertTrue(picture in entries)
        assertFalse(pendingImage in entries)
        assertFalse(entries.getValue(".xiangece/index.json").toString(Charsets.UTF_8).contains("回收站私有来源"))
        val importedStore = NotebookFileStore(temporary.newFolder())
        val imported = NotebookArchive.import(importedStore, bytes.inputStream()).single().record.sources.single()
        assertEquals(source.text, imported.text)
        assertNull(imported.sessionId)
        assertNull(imported.sessionCreatedAt)
        assertNotNull(importedStore.attachment(requireNotNull(imported.imagePath)))
    }
    @Test fun onlyActualLocalImageNodesAreIncludedInExports() {
        val images = NotebookMarkdown.localImages("![图](../attachments/p.png)\n`![假](../attachments/secret.png)`\n![远程](https://example.com/a.png)", "学习")
        assertEquals(setOf("attachments/p.png"), images)
    }
}
