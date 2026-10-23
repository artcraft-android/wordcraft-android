package io.github.artcraftandroid.wordcraft

import android.app.Application
import android.system.Os
import io.github.artcraftandroid.wordcraft.core.MobileSession
import io.github.artcraftandroid.wordcraft.core.initLogging
import io.github.artcraftandroid.wordcraft.engine.Engine
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class WordcraftApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The engine, loaded once per process on a background thread and shared by the screens. */
    val engine: Deferred<Result<Engine>> by lazy {
        scope.async {
            runCatching {
                // Rust's home and temporary folder on Android are not the app's own: point them at
                // it before the library first reads them (it can't set them itself). The engine
                // also looks for fonts in HOME/.fonts, where the app puts the ones it ships.
                installDocumentFonts(File(filesDir, ".fonts"))
                Os.setenv("HOME", filesDir.absolutePath, true)
                Os.setenv("TMPDIR", cacheDir.absolutePath, true)
                initLogging()
                val session = MobileSession()
                session.start(Locale.getDefault().toLanguageTag())
                Engine(session)
            }
        }
    }

    /**
     * The fonts the app ships for documents (assets/docfonts, open fonts with the widths of the
     * ones Word documents ask for), copied where the engine looks for them; again only when a file
     * changed.
     */
    private fun installDocumentFonts(dir: File) {
        dir.mkdirs()
        val names = runCatching { assets.list("docfonts") }.getOrNull().orEmpty()
        for (name in names) {
            val dest = File(dir, name)
            runCatching {
                val size = assets.openFd("docfonts/$name").use { it.length }
                if (dest.isFile && dest.length() == size) return@runCatching
                assets.open("docfonts/$name").use { input -> dest.outputStream().use { input.copyTo(it) } }
            }.recoverCatching {
                // compressed assets have no file descriptor: copy unconditionally
                if (!dest.isFile) assets.open("docfonts/$name").use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
        }
    }
}
