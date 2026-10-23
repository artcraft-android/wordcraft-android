package io.github.artcraftandroid.wordcraft.engine

import android.graphics.Bitmap
import io.github.artcraftandroid.wordcraft.core.MobileException
import io.github.artcraftandroid.wordcraft.core.MobileSession
import io.github.artcraftandroid.wordcraft.core.PageImage
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The WordCraft engine (Rust: `apps/wordcraft-android`) as the app uses it: one [MobileSession]
 * and the threads its calls run on. Never call the session from the main thread, except the
 * keyboard's short questions ([commandBlocking]).
 */
class Engine(val session: MobileSession) {
    /**
     * Edits and queries run one at a time, in the order the UI issued them, on a thread with a
     * large stack: layout recurses into tables and fields.
     */
    val commands: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(null, r, "wordcraft-commands", 64L shl 20) }.asCoroutineDispatcher()

    /** Page pictures, drawn without holding the session (the engine draws on several cores itself). */
    val renders: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(null, r, "wordcraft-render", 32L shl 20) }.asCoroutineDispatcher()

    /** Run [block] on the session after the edits issued before it. */
    suspend fun <T> command(block: MobileSession.() -> T): T = withContext(commands) { session.block() }

    /** The same from a thread that must answer at once (the keyboard's questions). */
    fun <T> commandBlocking(block: MobileSession.() -> T): T = runBlocking(commands) { session.block() }

    /** Draw on the render thread. */
    suspend fun <T> render(block: MobileSession.() -> T): T = withContext(renders) { session.block() }
}

/** A message for the user from anything the engine (or the system) threw. */
val Throwable.userMessage: String
    get() = when (this) {
        is MobileException -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
        else -> message?.takeIf { it.isNotBlank() && !it.contains("Exception") } ?: "Something went wrong."
    }

/** A page picture as a new bitmap (the engine's premultiplied RGBA is ARGB_8888's byte order). */
fun PageImage.toBitmap(): Bitmap? {
    val w = width.toInt()
    val h = height.toInt()
    if (w <= 0 || h <= 0 || rgba.size < w * h * 4) return null
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    b.copyPixelsFromBuffer(ByteBuffer.wrap(rgba, 0, w * h * 4))
    return b
}
