package io.github.artcraftandroid.wordcraft.engine

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A document opened before, as the landing page's Recent list shows it. */
data class RecentFile(
    /** Where it was opened from: a `content://` URI the app may read again. */
    val uri: String,
    val name: String,
    /** When it was last opened, ms since the epoch. */
    val opened: Long,
    /** Where it lives, in a few words (the provider's or folder's name). */
    val place: String,
)

/**
 * The recent documents, newest first, kept in a JSON file in the app's private files (at most
 * [MAX] of them). Reads and writes are small and synchronous; callers stay off the main thread.
 */
class RecentFiles(private val file: File) {
    private val lock = Any()

    fun list(): List<RecentFile> = synchronized(lock) { read() }

    fun touch(entry: RecentFile) = synchronized(lock) { write(listOf(entry) + read().filter { it.uri != entry.uri }) }

    fun remove(uri: String) = synchronized(lock) { write(read().filter { it.uri != uri }) }

    private fun read(): List<RecentFile> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val a = JSONArray(file.readText())
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                RecentFile(
                    uri = o.optString("uri").ifEmpty { return@mapNotNull null },
                    name = o.optString("name", "Document.docx"),
                    opened = o.optLong("opened"),
                    place = o.optString("place"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(list: List<RecentFile>) {
        val a = JSONArray()
        for (r in list.take(MAX)) a.put(JSONObject().put("uri", r.uri).put("name", r.name).put("opened", r.opened).put("place", r.place))
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(file)
        }
    }

    companion object {
        const val MAX = 50

        /** A short name for where [uri] lives: the provider (Downloads, Drive…) or "This device". */
        fun placeOf(uri: android.net.Uri): String = when (uri.authority) {
            "com.android.providers.downloads.documents" -> "Downloads"
            "com.android.externalstorage.documents" -> "This device"
            "com.google.android.apps.docs.storage" -> "Google Drive"
            "media" -> "This device"
            null -> "This device"
            else -> uri.authority?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() } ?: "This device"
        }
    }
}
