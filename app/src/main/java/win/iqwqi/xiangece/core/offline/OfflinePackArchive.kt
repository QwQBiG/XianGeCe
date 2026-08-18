package win.iqwqi.xiangece.core.offline

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Safely extracts only the named files required by an offline model pack. */
object OfflinePackArchive {
    fun extract(
        resolver: ContentResolver,
        uri: Uri,
        targetDirectory: File,
        expectedSizes: Map<String, Long>,
    ): Map<String, File> = resolver.openInputStream(uri).use { raw ->
        requireNotNull(raw) { "无法读取所选离线资源" }
        extract(raw, targetDirectory, expectedSizes)
    }

    fun extract(
        input: InputStream,
        targetDirectory: File,
        expectedSizes: Map<String, Long>,
    ): Map<String, File> {
        require(expectedSizes.isNotEmpty()) { "离线资源清单为空" }
        targetDirectory.deleteRecursively()
        check(targetDirectory.mkdirs() || targetDirectory.isDirectory) { "无法创建临时导入目录" }
        val extracted = linkedMapOf<String, File>()
        val totalLimit = expectedSizes.values.sum()
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val normalizedPath = entry.name.replace('\\', '/')
                val pathParts = normalizedPath.split('/')
                val name = normalizedPath.substringAfterLast('/')
                if (!entry.isDirectory && name.isNotBlank() && pathParts.none { it == ".." }) {
                    val maxBytes = expectedSizes[name]
                    if (maxBytes != null) {
                        check(extracted[name] == null) { "离线资源 ZIP 包含重复文件：$name" }
                        val destination = File(targetDirectory, name)
                        var fileBytes = 0L
                        destination.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var count: Int
                            while (zip.read(buffer).also { count = it } != -1) {
                                if (count == 0) continue
                                fileBytes += count
                                totalBytes += count
                                check(fileBytes <= maxBytes) { "离线资源文件过大：$name" }
                                check(totalBytes <= totalLimit) { "离线资源 ZIP 包过大" }
                                output.write(buffer, 0, count)
                            }
                        }
                        extracted[name] = destination
                    }
                }
                zip.closeEntry()
            }
        }
        expectedSizes.keys.forEach { name ->
            check(extracted[name]?.isFile == true) { "离线资源 ZIP 缺少文件：$name" }
        }
        return extracted
    }
}