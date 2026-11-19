package win.iqwqi.xiangece.feature.notebook.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Markdown is authoritative; metadata never contains the only copy of a note. */
class NotebookFileStore(val root: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val vault = File(root, "vault").apply { mkdirs() }
    private val meta = File(root, "state").apply { mkdirs() }
    private val manifestFile = File(meta, "index.json")
    private var manifest = if (manifestFile.isFile) {
        json.decodeFromString<NotebookManifest>(manifestFile.readText(Charsets.UTF_8))
            .also { require(it.version == 1) { "笔记库版本暂不支持" } }
    } else NotebookManifest()

    @Synchronized fun list(includeTrash: Boolean = false): List<NoteDocument> {
        discover()
        return manifest.notes.filter { includeTrash || it.deletedAt == null }.mapNotNull { record ->
            file(record).takeIf { it.isFile }?.let { readRecord(record, it) }
        }.sortedWith(compareByDescending<NoteDocument> { it.record.pinned }.thenByDescending { it.modifiedAt })
    }
    @Synchronized fun read(id: String): NoteDocument = record(id).let { readRecord(it, file(it)) }

    @Synchronized fun create(title: String, folder: String = "", text: String = "",
                             sources: List<NoteSource> = emptyList(), collectionId: String? = null): NoteDocument {
        if (collectionId != null) manifest.notes.firstOrNull { it.collectionId == collectionId }?.let { return read(it.id) }
        require(sources.size <= 4)
        sources.forEach { source ->
            source.validate(); source.imagePath?.let(::resolve)
            source.originalImagePath?.let { requireNotNull(originalAttachment(it)) { "来源原件缺失或格式无效" } }
        }
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_NOTE_BYTES) { "单篇笔记不能超过 2 MB" }
        val path = uniquePath(listOf(folder.trim('/'), safeName(title) + ".md").filter { it.isNotEmpty() }.joinToString("/"))
        val record = NoteRecord(UUID.randomUUID().toString(), path, sources = sources, collectionId = collectionId)
        atomicWrite(resolve(path), text.toByteArray(Charsets.UTF_8))
        manifest = manifest.copy(notes = manifest.notes + record)
        persist()
        return read(record.id)
    }
    @Synchronized fun save(id: String, text: String, expectedRevision: String): NoteDocument {
        val current = read(id)
        check(current.record.deletedAt == null) { "笔记已在回收站" }
        if (current.revision != expectedRevision) throw NoteConflict()
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_NOTE_BYTES) { "单篇笔记不能超过 2 MB" }
        if (current.text != text) {
            val history = File(meta, "history/$id").apply { mkdirs() }
            atomicWrite(File(history, "${System.currentTimeMillis()}-${UUID.randomUUID()}.md"), current.text.toByteArray(Charsets.UTF_8))
            atomicWrite(file(current.record), text.toByteArray(Charsets.UTF_8))
            history.listFiles()?.sortedByDescending { it.name }?.drop(30)?.forEach { it.delete() }
        }
        draftFile(id).delete()
        return read(id)
    }
    @Synchronized fun writeDraft(draft: NoteDraft) {
        record(draft.noteId)
        atomicWrite(draftFile(draft.noteId), json.encodeToString(draft).toByteArray(Charsets.UTF_8))
    }
    @Synchronized fun draft(id: String): NoteDraft? = draftFile(id).takeIf { it.isFile }
        ?.let { json.decodeFromString<NoteDraft>(it.readText(Charsets.UTF_8)) }
    @Synchronized fun discardDraft(id: String) { record(id); draftFile(id).delete() }

    @Synchronized fun rename(id: String, title: String, folder: String): NoteDocument {
        val old = record(id)
        check(old.deletedAt == null)
        val path = listOf(folder.trim('/'), safeName(title) + ".md").filter { it.isNotEmpty() }.joinToString("/")
        if (path == old.path) return read(id)
        require(!resolve(path).exists()) { "此文件夹已有同名笔记" }
        val current = read(id)
        val relocated = win.iqwqi.xiangece.feature.notebook.domain.NotebookLinkRewriter.relocate(current, folder.trim('/'), list())
        if (relocated != current.text) save(id, relocated, current.revision)
        val target = resolve(path).apply { parentFile?.mkdirs() }
        Files.move(file(old).toPath(), target.toPath())
        replace(old.copy(path = path, aliases = (old.aliases + old.path).distinct()))
        return read(id)
    }
    @Synchronized fun pin(id: String) { val old = record(id); replace(old.copy(pinned = !old.pinned)) }
    @Synchronized fun trash(id: String) {
        val old = record(id)
        if (old.deletedAt != null) return
        val next = old.copy(deletedAt = System.currentTimeMillis())
        val target = file(next).apply { parentFile?.mkdirs() }
        Files.move(file(old).toPath(), target.toPath())
        replace(next)
    }
    @Synchronized fun restore(id: String): NoteDocument {
        val old = record(id)
        check(old.deletedAt != null)
        val next = old.copy(path = uniquePath(old.path), deletedAt = null)
        val target = file(next).apply { parentFile?.mkdirs() }
        Files.move(file(old).toPath(), target.toPath())
        replace(next)
        return read(id)
    }
    @Synchronized fun history(id: String): List<NoteHistory> {
        record(id)
        return File(meta, "history/$id").listFiles().orEmpty().sortedByDescending { it.name }.map {
            NoteHistory(it.name, it.name.substringBefore('-').toLongOrNull() ?: it.lastModified(), it.readText(Charsets.UTF_8))
        }
    }
    @Synchronized fun folders(): List<String> = vault.walkTopDown().filter {
        it.isDirectory && it != vault && !it.relativeTo(vault).invariantSeparatorsPath.startsWith(".") &&
            it.relativeTo(vault).invariantSeparatorsPath.substringBefore('/') != "attachments"
    }.map { it.relativeTo(vault).invariantSeparatorsPath }.sorted().toList()
    @Synchronized fun createFolder(path: String) { require(resolve(path).mkdirs()) { "文件夹已存在或无法创建" } }

    @Synchronized fun addImage(bytes: ByteArray, extension: String): String {
        require(extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif")) { "请选择 PNG、JPEG、WebP 或 GIF 图片" }
        require(bytes.size <= 20 * 1024 * 1024) { "图片不能超过 20 MB" }
        val path = "attachments/${UUID.randomUUID()}.$extension"
        atomicWrite(resolve(path), bytes)
        return path
    }
    fun attachment(path: String): File? = runCatching {
        resolve(path).takeIf { it.isFile && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif") }
    }.getOrNull()

    /** Storage only. Opaque HEIC/HEIF originals never enter the display or recognition attachment API. */
    fun originalAttachment(path: String): File? = runCatching {
        resolve(path).takeIf { it.isFile && it.length() <= 20L * 1024 * 1024 && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "heic", "heif") }
    }.getOrNull()

    internal fun resolve(path: String): File {
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && ':' !in path) { "文件路径无效" }
        require(path.split('/').all { it.isNotBlank() && it != "." && it != ".." && !it.startsWith('.') }) { "文件路径无效" }
        val result = File(vault, path).canonicalFile
        require(result.path.startsWith(vault.canonicalPath + File.separator)) { "文件路径超出笔记库" }
        return result
    }
    private fun file(record: NoteRecord): File = if (record.deletedAt == null) resolve(record.path)
        else File(meta, "trash/${record.id}.md")
    private fun record(id: String) = manifest.notes.firstOrNull { it.id == id } ?: error("找不到这篇笔记")
    private fun readRecord(record: NoteRecord, file: File): NoteDocument {
        require(file.length() <= MAX_NOTE_BYTES) { "单篇笔记不能超过 2 MB" }
        val text = file.readText(Charsets.UTF_8)
        return NoteDocument(record, text, textRevision(text), file.lastModified())
    }
    private fun draftFile(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9-]+")))
        return File(meta, "drafts/$id.json")
    }
    private fun replace(record: NoteRecord) {
        manifest = manifest.copy(notes = manifest.notes.map { if (it.id == record.id) record else it })
        persist()
    }
    private fun persist() = atomicWrite(manifestFile, json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
    @Synchronized internal fun exportMetadata(): ByteArray = json.encodeToString(
        manifest.copy(notes = manifest.notes.filter { it.deletedAt == null }.map { it.copy(collectionId = null) })
    ).toByteArray(Charsets.UTF_8)
    @Synchronized internal fun importMetadata(folder: String, bytes: ByteArray?) {
        if (bytes == null) return
        val imported = json.decodeFromString<NotebookManifest>(bytes.toString(Charsets.UTF_8))
        require(imported.version == 1)
        manifest = manifest.copy(notes = manifest.notes.map { note ->
            val old = imported.notes.firstOrNull { "$folder/${it.path}" == note.path } ?: return@map note
            note.copy(pinned = old.pinned, aliases = old.aliases.map { "$folder/$it" }.filter { runCatching { resolve(it) }.isSuccess },
                sources = old.sources.map { source -> source.copy(
                    imagePath = source.imagePath?.let { "$folder/$it" }?.takeIf { attachment(it) != null },
                    originalImagePath = source.originalImagePath?.let { original ->
                        "$folder/$original".also { requireNotNull(originalAttachment(it)) { "来源原件缺失，未丢弃其关联" } }
                    },
                    sessionId = null, sessionCreatedAt = null,
                    blocks = source.blocks.map { it.copy(noteId = null, noteRevision = null) },
                ) })
        })
        persist()
    }
    private fun discover() {
        val known = manifest.notes.filter { it.deletedAt == null }.map { it.path }.toSet()
        val added = vault.walkTopDown().filter { it.isFile && it.extension.equals("md", true) }.mapNotNull {
            val path = it.relativeTo(vault).invariantSeparatorsPath
            if (path in known) null else NoteRecord(UUID.randomUUID().toString(), path)
        }.toList()
        if (added.isNotEmpty()) { manifest = manifest.copy(notes = manifest.notes + added); persist() }
    }
    private fun uniquePath(path: String): String {
        if (!resolve(path).exists()) return path
        var suffix = 2
        while (resolve(path.removeSuffix(".md") + " ($suffix).md").exists()) suffix++
        return path.removeSuffix(".md") + " ($suffix).md"
    }
    companion object {
        const val MAX_NOTE_BYTES = 2 * 1024 * 1024
        fun safeName(value: String): String {
            val name = value.trim().removeSuffix(".md")
            require(name.isNotBlank() && name.length <= 100 && !name.startsWith('.')) { "请填写 1–100 字的笔记名称" }
            require(name.none { it in "\\/:*?\"<>|" || it.isISOControl() }) { "名称不能包含斜杠等特殊符号" }
            return name
        }
        internal fun atomicWrite(file: File, bytes: ByteArray) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.tmp")
            try {
                java.io.FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
                try {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { temp.delete() }
        }
    }
}
