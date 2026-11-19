package win.iqwqi.xiangece.feature.diting

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePack
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackFile
import win.iqwqi.xiangece.feature.diting.offline.DitingPackFiles

class DitingPackFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val file = DitingOfflinePackFile(
        "Qwen3-0.6B/merges.txt", 3L,
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        "", "",
    )
    private val pack = DitingOfflinePack("test", "test", "", 3L, listOf(file))

    @Test
    fun wrappedAndFlatZipRestoreCatalogTokenizerDirectory() {
        for (entry in listOf("wrapper/Qwen3-0.6B/merges.txt", "merges.txt")) {
            val dir = temporary.newFolder()
            val result = DitingPackFiles.extract(zip(entry to "abc"), dir, pack)
            assertEquals("abc", result.getValue(file.name).readText(Charsets.UTF_8))
            assertEquals(DitingPackFiles.destination(dir, file.name), result.getValue(file.name))
            assertFalse(java.io.File(dir, "merges.txt").exists())
        }
    }

    @Test
    fun wrongHashAndDuplicateFilesAreRejectedBeforeInstallation() {
        assertThrows(IllegalStateException::class.java) {
            DitingPackFiles.extract(zip("merges.txt" to "abd"), temporary.newFolder(), pack)
        }
        assertThrows(IllegalStateException::class.java) {
            DitingPackFiles.extract(
                zip("a/merges.txt" to "abc", "b/merges.txt" to "abc"), temporary.newFolder(), pack,
            )
        }
    }

    @Test
    fun catalogTraversalAndAmbiguousBasenamesAreRejected() {
        for (name in listOf("../escape", "/absolute", "C:/outside", "a/../escape", "a\\b")) {
            assertThrows(IllegalArgumentException::class.java) {
                DitingPackFiles.destination(temporary.root, name)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            DitingPackFiles.extract(
                zip("merges.txt" to "abc"), temporary.newFolder(),
                pack.copy(files = listOf(file, file.copy(name = "other/merges.txt"))),
            )
        }
    }

    @Test
    fun cancelledImportDoesNotContinueExtracting() {
        assertThrows(CancellationException::class.java) {
            DitingPackFiles.extract(zip("merges.txt" to "abc"), temporary.newFolder(), pack) {
                throw CancellationException("cancel")
            }
        }
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
