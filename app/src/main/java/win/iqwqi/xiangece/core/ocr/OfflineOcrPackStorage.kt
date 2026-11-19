package win.iqwqi.xiangece.core.ocr

import java.io.File
import java.security.MessageDigest

/** Keeps installation identity separate from partial download progress. */
internal class OfflineOcrPackStorage(private val root: File) {
    fun directory(pack: OfflineOcrPackDescriptor): File {
        require(pack.id.matches(Regex("[a-z0-9_]+"))) { "Invalid OCR pack id" }
        return File(root, pack.id)
    }

    fun installed(pack: OfflineOcrPackDescriptor): Boolean {
        val target = directory(pack)
        val marker = File(target, INSTALL_MARKER)
        return marker.isFile &&
            runCatching { marker.readText(Charsets.UTF_8).trim() == pack.id }.getOrDefault(false) &&
            pack.files.all { file ->
                File(target, file.localName).let { it.isFile && it.length() == file.bytes }
            }
    }

    fun downloadedBytes(pack: OfflineOcrPackDescriptor): Long = pack.files.sumOf { file ->
        val target = directory(pack)
        val finalFile = File(target, file.localName)
        when {
            finalFile.isFile && finalFile.length() == file.bytes -> file.bytes
            else -> File(target, file.localName + ".part")
                .takeIf(File::isFile)?.length()?.coerceAtMost(file.bytes) ?: 0L
        }
    }

    fun verify(pack: OfflineOcrPackDescriptor): Boolean = installed(pack) &&
        pack.files.all { file -> sha256(File(directory(pack), file.localName)) == file.sha256 }

    companion object {
        const val INSTALL_MARKER = ".installed"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                var count: Int
                while (input.read(buffer).also { count = it } >= 0) {
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
