package win.iqwqi.xiangece.core.ocr

import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import win.iqwqi.xiangece.core.offline.OfflinePackArchive

class OfflineOcrPackStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fixture(id: String = "test_mobile"): OfflineOcrPackDescriptor {
        val source = temporary.newFile()
        source.writeText("test", Charsets.UTF_8)
        return OfflineOcrPackDescriptor(
            id, "test", "Synthetic installation fixture, not recognition data",
            listOf(OfflineOcrPackFile("det.onnx", 4, OfflineOcrPackStorage.sha256(source), "", "")),
        )
    }

    private fun install(storage: OfflineOcrPackStorage, pack: OfflineOcrPackDescriptor) {
        val target = storage.directory(pack).apply { mkdirs() }
        File(target, "det.onnx").writeText("test", Charsets.UTF_8)
        File(target, ".installed").writeText(pack.id, Charsets.UTF_8)
    }

    @Test
    fun independentlyInstalledPacksSurviveDeletingOneTier() {
        val storage = OfflineOcrPackStorage(temporary.newFolder())
        val mobile = fixture()
        val server = mobile.copy(id = "test_server")
        install(storage, mobile)
        install(storage, server)
        assertTrue(storage.verify(mobile))
        assertTrue(storage.verify(server))
        storage.directory(server).deleteRecursively()
        assertTrue(storage.verify(mobile))
        assertFalse(storage.installed(server))
    }

    @Test
    fun fullPartialFileAndStaleMarkerDoNotCountAsInstalled() {
        val storage = OfflineOcrPackStorage(temporary.newFolder())
        val pack = fixture()
        install(storage, pack)
        val file = File(storage.directory(pack), "det.onnx")
        assertTrue(file.renameTo(File(storage.directory(pack), "det.onnx.part")))
        assertEquals(4L, storage.downloadedBytes(pack))
        assertFalse(storage.installed(pack))
        assertFalse(storage.verify(pack))
    }

    @Test
    fun wrongTierMarkerAndSameSizeCorruptionAreRejected() {
        val storage = OfflineOcrPackStorage(temporary.newFolder())
        val pack = fixture()
        install(storage, pack)
        val target = storage.directory(pack)
        File(target, ".installed").writeText("test_server", Charsets.UTF_8)
        assertFalse(storage.installed(pack))
        File(target, ".installed").writeText(pack.id, Charsets.UTF_8)
        File(target, "det.onnx").writeText("bad!", Charsets.UTF_8)
        assertTrue(storage.installed(pack)) // Cheap UI check; engine load verifies the digest.
        assertFalse(storage.verify(pack))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPackIdCannotEscapeResourceDirectory() {
        OfflineOcrPackStorage(temporary.newFolder()).directory(fixture("../outside"))
    }

    @Test
    fun legacyZipWithCommonFolderStillInstallsAllThreeBasenames() {
        val base = fixture()
        val pack = base.copy(files = listOf("det.onnx", "rec.onnx", "rec.yml").map { name ->
            base.files.single().copy(localName = name)
        })
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            pack.files.forEach { file ->
                zip.putNextEntry(ZipEntry("ocr-zh-en/" + file.localName))
                zip.write("test".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        val storage = OfflineOcrPackStorage(temporary.newFolder())
        val target = storage.directory(pack)
        val extracted = OfflinePackArchive.extract(
            ByteArrayInputStream(archive.toByteArray()),
            target,
            pack.files.associate { it.localName to it.bytes },
        )
        assertEquals(setOf("det.onnx", "rec.onnx", "rec.yml"), extracted.keys)
        File(target, ".installed").writeText(pack.id, Charsets.UTF_8)
        assertTrue(storage.verify(pack))
    }
}
