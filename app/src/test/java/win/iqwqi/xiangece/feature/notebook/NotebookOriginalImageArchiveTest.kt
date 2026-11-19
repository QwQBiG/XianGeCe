package win.iqwqi.xiangece.feature.notebook

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import win.iqwqi.xiangece.feature.notebook.data.*

class NotebookOriginalImageArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun iosCompatibleHeicAndHeifOriginalsRemainOpaqueAcrossTwoActualZipRoundtrips() {
        for (extension in listOf("heic", "heif")) {
            val original = byteArrayOf(0, 1, 2, 3, 0xff.toByte(), 5, 6)
            val preview = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 8, 9)
            val metadata = """{"version":1,"notes":[{"id":"ios-note","path":"课程/原文.md","sources":[{"id":"source1","title":"来源原图","kind":"image","imagePath":"images/preview.jpg","originalImagePath":"images/original.$extension","blocks":[{"id":"block1","text":"不可变来源原文"}],"sessionId":42,"sessionCreatedAt":99}]}]}"""
            val input = zip(mapOf(".xiangece/index.json" to metadata.toByteArray(), "课程/原文.md" to "# 内容\n中文".toByteArray(), "images/preview.jpg" to preview, "images/original.$extension" to original))
            val first = NotebookFileStore(temporary.newFolder())
            val imported = NotebookArchive.import(first, input.inputStream()).single()
            val source = imported.record.sources.single()
            assertEquals("不可变来源原文", source.text)
            assertNull(source.sessionId)
            assertNotNull(first.attachment(requireNotNull(source.imagePath)))
            assertNull(first.attachment(requireNotNull(source.originalImagePath)))
            assertArrayEquals(original, requireNotNull(first.originalAttachment(requireNotNull(source.originalImagePath))).readBytes())
            first.save(imported.record.id, "编辑正文不改原件", imported.revision)
            val exported = ByteArrayOutputStream().also { NotebookArchive.export(first, it) }.toByteArray()
            val entries = entries(exported)
            val exportedMetadata = Json.parseToJsonElement(entries.getValue(".xiangece/index.json").toString(Charsets.UTF_8)).jsonObject
            val rawPath = exportedMetadata.getValue("notes").jsonArray.single().jsonObject.getValue("sources").jsonArray.single().jsonObject.getValue("originalImagePath").jsonPrimitive.content
            assertArrayEquals(original, entries.getValue(rawPath))
            val second = NotebookFileStore(temporary.newFolder())
            val again = NotebookArchive.import(second, exported.inputStream()).single().record.sources.single()
            assertArrayEquals(original, requireNotNull(second.originalAttachment(requireNotNull(again.originalImagePath))).readBytes())
            assertArrayEquals(preview, requireNotNull(second.attachment(requireNotNull(again.imagePath))).readBytes())
        }
    }
    @Test fun missingOrUnsafeOriginalNeverPublishesTheStagedNotebook() {
        val store = NotebookFileStore(temporary.newFolder())
        store.create("已有笔记", text = "不得覆盖")
        for (path in listOf("images/missing.heic", "../escape.heic")) {
            val metadata = """{"version":1,"notes":[{"id":"ios-note","path":"new.md","sources":[{"id":"source","title":"来源","kind":"image","originalImagePath":"$path","blocks":[]}]}]}"""
            val bytes = zip(mapOf(".xiangece/index.json" to metadata.toByteArray(), "new.md" to "不应发布".toByteArray()))
            assertThrows(IllegalArgumentException::class.java) { NotebookArchive.import(store, bytes.inputStream()) }
            assertEquals(1, store.list().size)
            assertEquals("不得覆盖", store.list().single().text)
        }
    }
    private fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip -> files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
        output.toByteArray()
    }
    private fun entries(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(bytes.inputStream()).use { zip -> var next = zip.nextEntry
            while (next != null) { put(next.name, zip.readBytes()); next = zip.nextEntry }
        }
    }
}
