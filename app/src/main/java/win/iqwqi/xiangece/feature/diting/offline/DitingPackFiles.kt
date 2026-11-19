package win.iqwqi.xiangece.feature.diting.offline

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import win.iqwqi.xiangece.core.offline.OfflinePackArchive

/** Archive paths never decide destinations: only this application's verified catalog does. */
internal object DitingPackFiles {
    fun destination(directory: File, name: String): File {
        require(name.isNotBlank() && !name.startsWith('/') && '\\' !in name && ':' !in name)
        require(name.split('/').none { it.isBlank() || it == "." || it == ".." })
        val root = directory.canonicalFile
        val file = File(root, name).canonicalFile
        require(file.path.startsWith(root.path + File.separator))
        return file
    }

    fun extract(
        input: InputStream,
        staging: File,
        pack: DitingOfflinePack,
        checkCancelled: () -> Unit = {},
    ): Map<String, File> {
        pack.files.forEach { destination(staging, it.name) }
        // The common extractor flattens paths. All these packs have unique basenames.
        val names = pack.files.map { it.name.substringAfterLast('/') }
        require(names.distinct().size == names.size) { "离线包包含同名资源" }
        val checkedInput = object : FilterInputStream(input) {
            override fun read(): Int {
                checkCancelled()
                return super.read()
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                checkCancelled()
                return super.read(buffer, offset, length)
            }
        }
        val flat = OfflinePackArchive.extract(
            checkedInput, staging, pack.files.associate { it.name.substringAfterLast('/') to it.bytes },
        )
        return pack.files.associate { file ->
            checkCancelled()
            val source = flat.getValue(file.name.substringAfterLast('/'))
            check(source.length() == file.bytes && sha256(source, checkCancelled) == file.sha256) {
                "离线资源校验失败：${file.name}"
            }
            val destination = destination(staging, file.name)
            if (source.canonicalFile != destination) {
                check(destination.parentFile!!.mkdirs() || destination.parentFile!!.isDirectory)
                check(source.renameTo(destination)) { "无法整理词表目录" }
            }
            file.name to destination
        }
    }

    fun sha256(file: File, checkCancelled: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
