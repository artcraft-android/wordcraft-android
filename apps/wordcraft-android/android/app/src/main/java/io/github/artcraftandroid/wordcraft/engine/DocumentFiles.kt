package io.github.artcraftandroid.wordcraft.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Documents the user opens and saves through the system's pickers and other apps' URIs. */
object DocumentFiles {
    /** What the app opens: Word documents and templates, older Word files, RTF, ODT, plain text. */
    val OPEN_TYPES = arrayOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
        "application/vnd.ms-word.document.macroEnabled.12",
        "application/msword",
        "application/rtf",
        "text/rtf",
        "application/vnd.oasis.opendocument.text",
        "text/plain",
    )

    const val DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** The name a URI shows the user, else its last path segment. */
    /**
     * The folders a document is in, from the top (`Documents » Letters`), when its provider says:
     * a shared-storage file's relative path, or an on-device document's id. Null when unknown.
     */
    fun folderOf(context: Context, uri: Uri): String? {
        val path = runCatching {
            when (uri.authority) {
                "media" -> context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
                // primary:Documents/Letters/letter.docx
                "com.android.externalstorage.documents" -> android.provider.DocumentsContract.getDocumentId(uri).substringAfter(':').substringBeforeLast('/', "")
                // document:1000042739 (a picker's Recent or Documents list): the media store row's folder
                "com.android.providers.media.documents" -> {
                    val id = android.provider.DocumentsContract.getDocumentId(uri).substringAfter(':').toLongOrNull()
                    id?.let {
                        val files = android.provider.MediaStore.Files.getContentUri("external")
                        context.contentResolver.query(files, arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH), "_id=?", arrayOf(it.toString()), null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }
                }
                // raw:/storage/emulated/0/Download/letter.docx
                "com.android.providers.downloads.documents" -> android.provider.DocumentsContract.getDocumentId(uri).takeIf { it.startsWith("raw:") }
                    ?.substringAfter("/storage/emulated/0/")?.substringBeforeLast('/', "")
                else -> null
            }
        }.getOrNull() ?: return null
        val parts = path.split('/').filter { it.isNotBlank() }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" » ")
    }

    fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Document.docx"
    }

    /**
     * A copy of the document at [uri] in the app's cache, under its own name (the engine reads the
     * format from the extension and the content). One folder per open, so names never clash.
     */
    fun copyToCache(context: Context, uri: Uri, name: String): File {
        val dir = File(context.cacheDir, "open/${System.nanoTime()}").apply { mkdirs() }
        val safe = name.replace(Regex("""[\\/:*?"<>|\u0000]"""), "_").ifBlank { "Document.docx" }
        val file = File(dir, safe)
        val input = context.contentResolver.openInputStream(uri) ?: error("The document can't be read.")
        input.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
        return file
    }

    /** Write [bytes] over the document at [uri]; false when the app may not write there. */
    fun write(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: return false
        out.use { it.write(bytes) }
        true
    }.getOrDefault(false)

    /** Remove the copies of earlier opens (called at start-up). */
    fun clearCache(context: Context) {
        runCatching { File(context.cacheDir, "open").deleteRecursively() }
    }
}
