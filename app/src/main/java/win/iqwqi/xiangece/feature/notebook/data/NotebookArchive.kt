package win.iqwqi.xiangece.feature.notebook.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object NotebookArchive {
    private const val MAX_TOTAL = 150L * 1024 * 1024
    private const val MAX_FILE = 20 * 1024 * 1024
    private val extensions = setOf("md", "png", "jpg", "jpeg", "webp", "gif")
    private val opaqueImageExtensions = setOf("heic", "heif")

    fun decodeMarkdown(bytes: ByteArray): String {
        require(bytes.size <= NotebookFileStore.MAX_NOTE_BYTES) { "单篇笔记不能超过 2 MB" }
        return try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
            catch (_: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("文件不是 UTF-8 文本，请转换编码后再导入") }
            .also { require('\u0000' !in it) { "文件不是 Markdown 文本" } }
    }

    fun export(store: NotebookFileStore, output: OutputStream) = synchronized(store) {
        val vault = File(store.root, "vault")
        val notes = store.list()
        val originals = notes.flatMap { it.record.sources.mapNotNull { source -> source.originalImagePath } }.toSet()
        originals.forEach { requireNotNull(store.originalAttachment(it)) { "来源原件缺失或格式无效，未导出不完整笔记库：$it" } }
        val images = notes.flatMap { note ->
            win.iqwqi.xiangece.feature.notebook.domain.NotebookMarkdown.localImages(note.text, note.record.folder) +
                note.record.sources.mapNotNull { it.imagePath }
        }.toSet() + originals
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(".xiangece/index.json"))
            zip.write(store.exportMetadata())
            zip.closeEntry()
            vault.walkTopDown().filter { it.isFile && (it.extension.lowercase() in extensions || it.extension.lowercase() in opaqueImageExtensions) }.forEach { file ->
                val relative = file.relativeTo(vault).invariantSeparatorsPath
                if (file.extension.lowercase() != "md" && relative !in images) return@forEach
                store.resolve(relative)
                zip.putNextEntry(ZipEntry(relative))
                val note = notes.firstOrNull { it.record.path == relative }
                if (note != null) zip.write(win.iqwqi.xiangece.feature.notebook.domain.NotebookLinkRewriter.portable(note, notes).toByteArray(Charsets.UTF_8))
                else file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Stage the entire archive before publishing it as one new folder. Never overwrite notes. */
    fun import(store: NotebookFileStore, input: InputStream): List<NoteDocument> = synchronized(store) {
        val stage = File(store.root, "import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var count = 0
            var total = 0L
            var notes = 0
            var metadata: ByteArray? = null
            val seen = mutableSetOf<String>()
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(++count <= 5000) { "压缩包文件过多" }
                    val path = entry.name.trimEnd('/')
                    require(!path.startsWith('/') && '\\' !in path && ':' !in path &&
                        path.split('/').none { it == ".." || it == "." || it.isEmpty() }) { "压缩包包含无效路径" }
                    if (!entry.isDirectory) {
                        require(seen.add(path.lowercase())) { "压缩包包含重复路径" }
                        val bytes = zip.readBytesLimited(MAX_FILE)
                        total += bytes.size
                        require(total <= MAX_TOTAL) { "压缩包解压后不能超过 150 MB" }
                        if (path == ".xiangece/index.json") {
                            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<NotebookManifest>(bytes.toString(Charsets.UTF_8))
                                .also { manifest ->
                                    require(manifest.version == 1) { "笔记库版本暂不支持" }
                                    manifest.notes.forEach { note ->
                                        require(note.sources.size <= 4)
                                        note.sources.forEach { it.validate() }
                                    }
                                }
                            metadata = bytes
                        }
                        if (path.split('/').none { it.startsWith('.') } && path.substringAfterLast('.').lowercase() in extensions + opaqueImageExtensions) {
                            val target = File(stage, path)
                            target.parentFile?.mkdirs()
                            require(!target.exists()) { "压缩包路径在本机文件系统中冲突" }
                            if (target.extension.lowercase() == "md") {
                                val text = decodeMarkdown(bytes)
                                target.writeText(text, Charsets.UTF_8)
                                notes++
                            } else target.writeBytes(bytes)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            require(notes > 0) { "压缩包中没有可导入的 Markdown 笔记" }
            // Validate opaque originals before publishing the staged folder. No decoder sees these bytes.
            metadata?.let { bytes ->
                val imported = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<NotebookManifest>(bytes.toString(Charsets.UTF_8))
                imported.notes.flatMap { it.sources }.mapNotNull { it.originalImagePath }.forEach { path ->
                    val file = File(stage, path).canonicalFile
                    require(file.path.startsWith(stage.canonicalPath + File.separator) && file.isFile && file.length() <= MAX_FILE &&
                        file.extension.lowercase() in (extensions - "md") + opaqueImageExtensions) { "来源原件缺失或格式无效：$path" }
                }
            }
            val folder = "导入-${java.time.LocalDate.now()}-${UUID.randomUUID().toString().take(6)}"
            Files.move(stage.toPath(), store.resolve(folder).toPath())
            store.list()
            store.importMetadata(folder, metadata)
            store.list().filter { it.record.path.startsWith("$folder/") }
        } finally { stage.deleteRecursively() }
    }

    fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = read(buffer)
            if (size < 0) break
            require(output.size().toLong() + size <= limit) { "文件过大" }
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }
}
