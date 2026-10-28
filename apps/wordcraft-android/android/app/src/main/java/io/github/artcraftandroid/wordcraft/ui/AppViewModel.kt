package io.github.artcraftandroid.wordcraft.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.artcraftandroid.wordcraft.WordcraftApplication
import io.github.artcraftandroid.wordcraft.core.ColorChoices
import io.github.artcraftandroid.wordcraft.core.DocumentInfo
import io.github.artcraftandroid.wordcraft.core.FormatState
import io.github.artcraftandroid.wordcraft.core.MobileException
import io.github.artcraftandroid.wordcraft.core.MobileSession
import io.github.artcraftandroid.wordcraft.core.PageSetup
import io.github.artcraftandroid.wordcraft.core.ReviewState
import io.github.artcraftandroid.wordcraft.core.SelectionView
import io.github.artcraftandroid.wordcraft.core.TextAround
import io.github.artcraftandroid.wordcraft.engine.DocumentFiles
import io.github.artcraftandroid.wordcraft.engine.Engine
import io.github.artcraftandroid.wordcraft.engine.toBitmap
import io.github.artcraftandroid.wordcraft.engine.userMessage
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class Screen { Start, Editor, Settings }

/** The open document: its name, and where it came from when the app may write back there. */
data class OpenDocument(val name: String, val uri: Uri?, val writable: Boolean)

/** How the document shows: as pages, or reflowed to the screen's width (Mobile View). */
enum class ViewMode { Print, Mobile }

/**
 * Mobile View draws the reflowed text this large: dp on screen per point of the document, as the
 * reference app's reflowed view measures on the phone (36 pt of paragraph spacing over 60.1 dp).
 */
const val MOBILE_DP_PER_PT = 1.67f

/** Mobile View's white margin each side of the reflowed text (points), on top of the engine's own. */
const val MOBILE_PAD_PT = 5f

/**
 * Mobile View's room above the text (points): the phone app starts it about 8.6 pt under the bar,
 * so the reflowed page's 18 pt top margin is partly hidden.
 */
const val MOBILE_TOP_PT = -9.4f

/** What separates the words Read Aloud counts. */
private val WORD_GAP = Regex("\\s+")

/** Read Aloud's slowest and fastest reading speeds, times the voice's own. */
const val MIN_READING_RATE = 0.5f
const val MAX_READING_RATE = 2.5f

/** How many of Tell Me's picks it remembers. */
private const val TELL_ME_RECENT = 5

/** How many documents' reading places are kept. */
private const val MAX_PLACES = 100

/** The action of a home-screen shortcut to a document ([AppViewModel.pinToHome]); its data is the document's URI. */
const val ACTION_OPEN_PINNED = "io.github.artcraftandroid.wordcraft.action.OPEN_PINNED"

/** The app's state: which screen shows, the open document, and the engine's view of it. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val engineAsync = (app as WordcraftApplication).engine

    var screen by mutableStateOf(Screen.Start)
        private set
    var document by mutableStateOf<OpenDocument?>(null)
        private set
    var info by mutableStateOf<DocumentInfo?>(null)
        private set
    var selection by mutableStateOf<SelectionView?>(null)
        private set
    var format by mutableStateOf<FormatState?>(null)
        private set

    /** Editing (the format strip, the palette, the keyboard) rather than reading. */
    var editing by mutableStateOf(false)
        private set
    var viewMode by mutableStateOf(ViewMode.Print)
        private set

    /** The palette (the expanded command surface) is open, on [paletteTab], [panels] deep. */
    var paletteOpen by mutableStateOf(false)
        private set
    var paletteTab by mutableStateOf("Home")
        private set
    var panels by mutableStateOf<List<String>>(emptyList())
        private set

    /** The colour the split Highlight and Font Color buttons apply. */
    /** White until a highlight colour is picked: the split button then applies yellow. */
    var lastHighlight by mutableStateOf(Pair("yellow", "FFFFFF"))
        private set
    var lastFontColor by mutableStateOf("FF0000")
        private set

    var colors by mutableStateOf<ColorChoices?>(null)
        private set
    var fonts by mutableStateOf<List<String>>(emptyList())
        private set

    /** The document's theme fonts: for headings, then for body text. */
    var themeFonts by mutableStateOf<List<String>>(emptyList())
        private set

    /** The Styles list: the document's styles, then the recommended ones, each with its look. */
    var styleLooks by mutableStateOf<List<io.github.artcraftandroid.wordcraft.core.StyleLook>>(emptyList())
        private set

    /** Read the Styles list again (another document, or styles changed). */
    fun loadStyleLooks() = withEngine { e -> styleLooks = e.command { styleLooks() } }

    /** The keyboard's composing text, drawn at the caret until it is committed. */
    var composing by mutableStateOf("")
        private set

    /** A message to show the user once. */
    var message by mutableStateOf<String?>(null)
    var engine by mutableStateOf<Engine?>(null)
        private set

    /** Bumped when the caret moved other than by typing: the keyboard starts afresh. */
    var inputGeneration by mutableStateOf(0)
        private set

    /** Bumped to ask for the keyboard (a tap while editing). */
    var keyboardRequests by mutableStateOf(0)
        private set

    /** The reflow width (points) Mobile View was laid out for. */
    private var mobileWidth = 0f

    /** Requests to pick where to save a copy (the suggested file name). */
    val saveAsRequests = Channel<String>(Channel.CONFLATED)

    private val recentFiles = io.github.artcraftandroid.wordcraft.engine.RecentFiles(java.io.File(app.filesDir, "recent.json"))

    /** The documents opened before, newest first (the landing page's Recent). */
    var recent by mutableStateOf<List<io.github.artcraftandroid.wordcraft.engine.RecentFile>>(emptyList())
        private set

    fun loadRecent() {
        viewModelScope.launch { recent = withContext(Dispatchers.IO) { recentFiles.list() } }
    }

    private fun remember(uri: Uri, name: String) {
        // where it is: the place, then its folders (This device » Documents » Letters)
        val folders = DocumentFiles.folderOf(getApplication(), uri)
        val place = listOfNotNull(io.github.artcraftandroid.wordcraft.engine.RecentFiles.placeOf(uri), folders).joinToString(" » ")
        val entry = io.github.artcraftandroid.wordcraft.engine.RecentFile(uri.toString(), name, System.currentTimeMillis(), place)
        viewModelScope.launch { withContext(Dispatchers.IO) { recentFiles.touch(entry) } }
    }

    fun removeRecent(uri: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { recentFiles.remove(uri) }
            loadRecent()
        }
    }

    /** Open a recent document again (it stays in the list only while the app may still read it). */
    fun openRecent(r: io.github.artcraftandroid.wordcraft.engine.RecentFile) {
        val uri = Uri.parse(r.uri)
        val readable = runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.close() }.isSuccess
        if (!readable) {
            message = "${r.name} can't be opened any more."
            removeRecent(r.uri)
            return
        }
        open(uri, writable = true)
    }

    /** Put a shortcut to recent document [r] on the home screen (the launcher asks first): it opens the document. */
    fun pinToHome(r: io.github.artcraftandroid.wordcraft.engine.RecentFile) {
        val ctx = getApplication<Application>()
        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            message = "This home screen doesn't take shortcuts."
            return
        }
        val label = r.name.substringBeforeLast('.', r.name)
        val intent = android.content.Intent(ACTION_OPEN_PINNED, Uri.parse(r.uri), ctx, io.github.artcraftandroid.wordcraft.MainActivity::class.java)
        val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, "doc-" + r.uri.hashCode().toUInt().toString(16))
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(ctx, io.github.artcraftandroid.wordcraft.R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(ctx, shortcut, null)
    }

    /** A home-screen shortcut's document: opened as from the recent list, if it can still be read. */
    fun openPinned(uri: Uri) {
        val readable = runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.close() }.isSuccess
        if (!readable) {
            message = "That document can't be opened any more."
            return
        }
        open(uri, writable = true)
    }

    init {
        loadRecent()
        DocumentFiles.clearCache(app)
        viewModelScope.launch {
            engineAsync.await().onSuccess { engine = it }.onFailure { message = "WordCraft couldn't start: ${it.userMessage}" }
        }
    }

    fun tell(text: String) {
        message = text
    }

    private fun withEngine(block: suspend (Engine) -> Unit) {
        viewModelScope.launch {
            val e = engineAsync.await().getOrNull() ?: return@launch
            try {
                block(e)
            } catch (t: MobileException) {
                message = t.userMessage
            }
        }
    }

    var pageSetup by mutableStateOf<PageSetup?>(null)
        private set
    var review by mutableStateOf<ReviewState?>(null)
        private set

    /** Zoom asks for the pages' view: `100`, `in`, `out`, `onePage`, `pageWidth`. */
    val zoomRequests = Channel<String>(Channel.BUFFERED)

    /** Insert a picture from `photos` or the `camera` (the activity opens them). */
    val pictureRequests = Channel<String>(Channel.CONFLATED)

    /**
     * Drawing: whether a finger draws ([inkOn]), with which tool (`pen`, `highlighter`, `eraser`),
     * and each pen's colour and width in points.
     */
    var inkOn by mutableStateOf(false)
        private set
    var inkTool by mutableStateOf("pen")
        private set
    var penColor by mutableStateOf("000000")
        private set
    var penWidth by mutableStateOf(0.7f * 72f / 25.4f)
        private set
    var highlighterColor by mutableStateOf("FFE066")
        private set
    var highlighterWidth by mutableStateOf(5f * 72f / 25.4f)
        private set

    fun setInk(on: Boolean) {
        inkOn = on
        if (!on) inkEditor = false
    }

    /** Pick up [tool]; a finger draws with it from now on. */
    fun pickTool(tool: String) {
        inkTool = tool
        inkOn = true
    }

    /** Set the colour of the current pen (the highlighter's when it is the tool). */
    fun setInkColor(hex: String) {
        if (inkTool == "highlighter") highlighterColor = hex else penColor = hex
        if (inkTool == "eraser") inkTool = "pen"
        inkOn = true
    }

    fun setInkWidth(pt: Float) {
        if (inkTool == "highlighter") highlighterWidth = pt else penWidth = pt
        inkOn = true
    }

    /** A stroke drawn on page [page] through [points] (page points). */
    /** Draw › Ink Editor: the pen edits the text (its strokes are read as gestures, not kept as ink). */
    var inkEditor by mutableStateOf(false)
        private set

    fun useInkEditor(on: Boolean) {
        inkEditor = on
        if (on) {
            if (inkTool == "eraser") inkTool = "pen"
            inkOn = true
        }
    }

    /** A stroke drawn while the Ink Editor is on: what it means is done to the text under it. */
    private fun inkEdit(page: Int, points: List<Pair<Float, Float>>) {
        val pts = org.json.JSONArray()
        for ((x, y) in points.take(5000)) pts.put(org.json.JSONArray().put(x.toDouble()).put(y.toDouble()))
        val p = JSONObject().put("page", page).put("points", pts).put("tool", if (inkTool == "highlighter") "highlighter" else "pen")
        command("draw.inkEdit", p.toString())
    }

    fun stroke(page: Int, points: List<Pair<Float, Float>>) {
        if (points.isEmpty()) return
        if (inkEditor && inkTool != "eraser") {
            inkEdit(page, points)
            return
        }
        val pts = org.json.JSONArray()
        // a tap is a dot: two points at the same place
        val all = if (points.size == 1) points + points else points
        for ((x, y) in all.take(5000)) pts.put(org.json.JSONArray().put(x.toDouble()).put(y.toDouble()))
        val hl = inkTool == "highlighter"
        val p = JSONObject().put("page", page).put("points", pts).put("tool", if (hl) "highlighter" else "pen")
            .put("color", if (hl) highlighterColor else penColor).put("width", (if (hl) highlighterWidth else penWidth).toDouble())
        command("draw.stroke", p.toString())
    }

    /** The eraser touched page [page] at ([x], [y]): the stroke there goes. */
    fun eraseAt(page: Int, x: Float, y: Float) {
        command("draw.erase", JSONObject().put("page", page).put("x", x.toDouble()).put("y", y.toDouble()).toString())
    }

    // Read Aloud: the engine gives the sentences; the phone's own speech says them one by one.

    /** The player shows, paused or not, at what rate; the sentence being read is highlighted. */
    var reading by mutableStateOf(false)
        private set
    var readingPaused by mutableStateOf(false)
        private set
    var readingRate by mutableStateOf(1f)
        private set

    /** Read Aloud's Audio Settings (reading speed, voice) show over its bar. */
    var audioSettingsOpen by mutableStateOf(false)
    var readingRects by mutableStateOf<List<io.github.artcraftandroid.wordcraft.core.PageRect>>(emptyList())
        private set

    /** The word being said, highlighted over its sentence. */
    var readingWord by mutableStateOf<List<io.github.artcraftandroid.wordcraft.core.PageRect>>(emptyList())
        private set
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    private var sentences: List<String> = emptyList()
    private var sentenceIndex = 0

    private val ttsListener = object : android.speech.tts.UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        // the voice says the word from [start]: count the words before it as the engine does
        // (runs between spaces with a letter or digit) and highlight that one
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            viewModelScope.launch {
                if (!reading || utteranceId != "s$sentenceIndex") return@launch
                val text = sentences.getOrNull(sentenceIndex) ?: return@launch
                val before = text.take(start.coerceIn(0, text.length))
                val n = before.split(WORD_GAP).count { w -> w.any(Char::isLetterOrDigit) }
                val i = sentenceIndex
                withEngine { e -> readingWord = e.command { readAloudWordRects(i.toUInt(), n.toUInt()) } }
            }
        }

        override fun onDone(utteranceId: String?) {
            viewModelScope.launch {
                if (!reading || readingPaused || utteranceId != "s$sentenceIndex") return@launch
                if (sentenceIndex + 1 < sentences.size) {
                    sentenceIndex++
                    speakCurrent()
                } else {
                    stopReadAloud()
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {}
    }

    /** Read from the caret (or from the start when nothing follows it). */
    fun startReadAloud() = withEngine { e ->
        commitComposing(e)
        val list = e.command { readAloudSentences(false) }.ifEmpty { e.command { readAloudSentences(true) } }
        if (list.isEmpty()) {
            message = "There's nothing to read."
            return@withEngine
        }
        sentences = list
        sentenceIndex = 0
        reading = true
        readingPaused = false
        val existing = tts
        if (existing != null && ttsReady) {
            speakCurrent()
        } else {
            tts = android.speech.tts.TextToSpeech(getApplication()) { status ->
                viewModelScope.launch {
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        ttsReady = true
                        tts?.setOnUtteranceProgressListener(ttsListener)
                        loadVoices()
                        if (reading) speakCurrent()
                    } else {
                        message = "Speech isn't available on this phone."
                        stopReadAloud()
                    }
                }
            }
        }
    }

    private fun speakCurrent() {
        val text = sentences.getOrNull(sentenceIndex) ?: return
        showSentence()
        tts?.setSpeechRate(readingRate)
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "s$sentenceIndex")
    }

    private fun showSentence() = withEngine { e ->
        val i = sentenceIndex
        readingWord = emptyList()
        readingRects = e.command { readAloudRects(i.toUInt()) }
    }

    fun toggleReading() {
        readingPaused = !readingPaused
        if (readingPaused) tts?.stop() else speakCurrent()
    }

    /** The previous (-1) or next (1) sentence. */
    fun skipSentence(by: Int) {
        if (sentences.isEmpty()) return
        sentenceIndex = (sentenceIndex + by).coerceIn(0, sentences.lastIndex)
        if (readingPaused) showSentence() else speakCurrent()
    }

    /** Read at [rate] times the voice's own speed (0.5 to 2.5), from the sentence being read. */
    fun chooseReadingRate(rate: Float) {
        readingRate = rate.coerceIn(MIN_READING_RATE, MAX_READING_RATE)
        if (reading && !readingPaused) speakCurrent()
    }

    /** The installed voices that speak the phone's language, and the one chosen, put to use. */
    private fun loadVoices() {
        val engine = tts ?: return
        val language = java.util.Locale.getDefault().language
        val voices = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == language && !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.displayName }, { it.name }))
        // the voices' own names are codes: they're shown by language, numbered
        readingVoices = voices.mapIndexed { i, v -> v.name to "${v.locale.displayName} ${i + 1}" }
        voices.firstOrNull { it.name == readingVoice }?.let { v -> runCatching { engine.voice = v } }
    }

    /** Read in the voice named [name] (one of [readingVoices]), kept for the next time. */
    fun chooseReadingVoice(name: String) {
        readingVoice = name
        prefs.edit().putString("readingVoice", name).apply()
        val engine = tts ?: return
        // none chosen: the phone's own default voice
        val voice = if (name.isEmpty()) runCatching { engine.defaultVoice }.getOrNull()
        else runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet()).firstOrNull { it.name == name }
        voice?.let { v -> runCatching { engine.voice = v } }
        if (reading && !readingPaused) speakCurrent()
    }

    fun stopReadAloud() {
        tts?.stop()
        audioSettingsOpen = false
        reading = false
        readingPaused = false
        readingRects = emptyList()
        readingWord = emptyList()
    }

    override fun onCleared() {
        tts?.shutdown()
        super.onCleared()
    }

    /** The edit header's overflow menu (a bottom sheet over a scrim). */
    var overflowOpen by mutableStateOf(false)

    /** Share a file (its content URI and type); print a PDF (a job name and the bytes). */
    val shareRequests = Channel<Pair<Uri, String>>(Channel.BUFFERED)
    val printRequests = Channel<Pair<String, ByteArray>>(Channel.CONFLATED)

    /** Share the document, as it is or as a PDF, through the phone's share sheet. */
    fun share(asPdf: Boolean) = withEngine { e ->
        val doc = document ?: return@withEngine
        commitComposing(e)
        val base = doc.name.substringBeforeLast('.', doc.name)
        val name = if (asPdf) "$base.pdf" else saveName(doc.name)
        val bytes = e.command { exportBytes(name) }
        val ctx = getApplication<Application>()
        val uri = withContext(Dispatchers.IO) {
            val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
            val file = java.io.File(dir, name.replace(Regex("""[\\/:*?"<>|]"""), "_"))
            file.writeBytes(bytes)
            androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        }
        shareRequests.trySend(uri to if (asPdf) "application/pdf" else DocumentFiles.DOCX_TYPE)
    }

    /** Print the document through the phone's print service. */
    fun print() = withEngine { e ->
        val doc = document ?: return@withEngine
        commitComposing(e)
        val bytes = e.command { exportBytes("print.pdf") }
        printRequests.trySend(doc.name.substringBeforeLast('.', doc.name) to bytes)
    }

    // Settings, kept between runs.

    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    /** Theme: `system` (follow the phone), `light` or `dark`. */
    var themeChoice by mutableStateOf(prefs.getString("theme", "system") ?: "system")
        private set

    /** Documents open in Mobile View (else as pages). */
    var startInMobileView by mutableStateOf(prefs.getBoolean("startMobileView", true))
        private set

    /** Changes are saved to the document's own file as they are made. */
    var autoSaveOn by mutableStateOf(prefs.getBoolean("autoSave", true))
        private set

    fun chooseTheme(choice: String) {
        themeChoice = choice
        prefs.edit().putString("theme", choice).apply()
    }

    fun chooseStartInMobileView(on: Boolean) {
        startInMobileView = on
        prefs.edit().putBoolean("startMobileView", on).apply()
    }

    /** Display for Review: kept for every document from now on, as the reference keeps it. */
    fun chooseMarkupView(view: String) {
        prefs.edit().putString("markupView", view).apply()
        command("review.markup", JSONObject().put("value", view).toString())
    }

    /** The Headings pane offers lines that look like headings in a document without heading styles. */
    var autoHeadings by mutableStateOf(prefs.getBoolean("autoHeadings", true))
        private set

    /** The voices Read Aloud can use (installed ones for the phone's language), as (name, label). */
    var readingVoices by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    /** The voice Read Aloud reads in (its name), or empty for the phone's default. */
    var readingVoice by mutableStateOf(prefs.getString("readingVoice", "") ?: "")
        private set

    fun chooseAutoHeadings(on: Boolean) {
        autoHeadings = on
        prefs.edit().putBoolean("autoHeadings", on).apply()
    }

    fun chooseAutoSave(on: Boolean) {
        autoSaveOn = on
        prefs.edit().putBoolean("autoSave", on).apply()
        if (on) scheduleAutoSave()
    }

    /** Word Count counts text boxes, footnotes and endnotes too. */
    var countWithBoxes by mutableStateOf(prefs.getBoolean("countWithBoxes", true))
        private set

    /** Word Count's "Show Word Count" option, kept like the other options (the phone has no
     *  status bar to show it in). */
    var showWordCount by mutableStateOf(prefs.getBoolean("showWordCount", false))
        private set

    fun chooseCountWithBoxes(on: Boolean) {
        countWithBoxes = on
        prefs.edit().putBoolean("countWithBoxes", on).apply()
    }

    fun chooseShowWordCount(on: Boolean) {
        showWordCount = on
        prefs.edit().putBoolean("showWordCount", on).apply()
    }

    /** Recent Shapes: the gallery entries inserted lately, newest first (see ShapeGalleryPanel). */
    var recentShapes by mutableStateOf(
        prefs.getString("recentShapes", null)?.split(',')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: listOf("textBox", "straightConnector:none", "straightConnector:end", "rect", "ellipse", "roundRect", "triangle", "elbowConnector:none"),
    )
        private set

    /** Insert gallery entry [key] (a preset, `kind:arrow` for a line, or `textBox`) and put it first among the recent ones. */
    fun insertShape(key: String) {
        when {
            key == "textBox" -> command("insert.textBox")
            key.contains(':') -> command("insert.shape", JSONObject().put("kind", key.substringBefore(':')).put("arrow", key.substringAfter(':')).toString())
            else -> command("insert.shape", JSONObject().put("preset", key).toString())
        }
        recentShapes = (listOf(key) + recentShapes.filter { it != key }).take(8)
        prefs.edit().putString("recentShapes", recentShapes.joinToString(",")).apply()
        closePalette()
    }

    /** A text field in the palette (Alt Text) has the keyboard: the palette stays up above it. */
    var paletteTyping by mutableStateOf(false)

    /** Recent Styles: the shape styles applied lately (`style:colour`, see `shape.style`), newest first. */
    var recentShapeStyles by mutableStateOf(
        prefs.getString("recentShapeStyles", null)?.split(',')?.filter { it.contains(':') }?.takeIf { it.isNotEmpty() }
            ?: listOf("dark1", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6").map { "coloredFill:$it" },
    )
        private set

    /** Shape Styles: apply style [key] (`style:colour`) to the selected shapes and put it first among the recent ones. */
    fun applyShapeStyle(key: String) {
        command("shape.style", JSONObject().put("style", key.substringBefore(':')).put("color", key.substringAfter(':')).toString())
        recentShapeStyles = (listOf(key) + recentShapeStyles.filter { it != key }).take(7)
        prefs.edit().putString("recentShapeStyles", recentShapeStyles.joinToString(",")).apply()
    }

    /** Recent Styles of the Graphics tab: the graphics styles applied lately (`look:colour`), newest first. */
    var recentGraphicStyles by mutableStateOf(
        prefs.getString("recentGraphicStyles", null)?.split(',')?.filter { it.contains(':') }?.takeIf { it.isNotEmpty() }
            ?: listOf("dark1", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6").map { "lightFill:$it" },
    )
        private set

    /**
     * Graphics Styles: give the selected icon style [key] (`look:colour`, the colour `dark1` or
     * `accent1`..`accent6` of the theme) and put it first among the recent ones. The looks: a light
     * fill with an outline in the colour, a fill in the colour with no outline or a dark one, or no
     * fill with an outline in the colour.
     */
    fun applyGraphicStyle(key: String) = withEngine { e ->
        val theme = colors?.theme?.firstOrNull() ?: return@withEngine
        fun hex(i: Int) = theme.getOrNull(i)?.hex ?: "000000"
        val name = key.substringAfter(':')
        val c = if (name == "dark1") hex(1) else hex(3 + (name.removePrefix("accent").toIntOrNull() ?: 1))
        val (fill, outline) = when (key.substringBefore(':')) {
            "lightFill" -> hex(0) to c
            "coloredFill" -> c to null
            "darkOutline" -> c to hex(1)
            else -> null to c
        }
        e.command { execute("graphic.fill", JSONObject().put("color", fill ?: JSONObject.NULL).toString()) }
        e.command { execute("graphic.outline", JSONObject().put("color", outline ?: JSONObject.NULL).toString()) }
        recentGraphicStyles = (listOf(key) + recentGraphicStyles.filter { it != key }).take(7)
        prefs.edit().putString("recentGraphicStyles", recentGraphicStyles.joinToString(",")).apply()
        refresh(e)
    }

    /** Hand the selected chart's type, title, labels, legend and colours to [onResult]. */
    fun chartState(onResult: (io.github.artcraftandroid.wordcraft.core.ChartState?) -> Unit) = withEngine { e -> onResult(e.command { chartState() }) }

    /** Hand a chart gallery's pictures ([which]: `type:<family>` or `layout`), [widthPx] wide, to [onResult]. */
    fun chartGallery(which: String, widthPx: Int, onResult: (List<Pair<String, android.graphics.Bitmap>>) -> Unit) = withEngine { e ->
        val list = e.command { chartGallery(which, widthPx.coerceIn(16, 2000).toUInt()) }
        onResult(list.mapNotNull { g -> g.image.toBitmap()?.let { g.id to it } })
    }

    /** Hand every picture style, drawn on a sample picture [widthPx] wide, to [onResult] (style id, bitmap). */
    fun pictureStyleGallery(widthPx: Int, onResult: (List<Pair<String, android.graphics.Bitmap>>) -> Unit) = withEngine { e ->
        val list = e.command { pictureStyleGallery(widthPx.coerceIn(16, 2000).toUInt()) }
        onResult(list.mapNotNull { g -> g.image.toBitmap()?.let { g.id to it } })
    }

    /** Hand the chart palettes in the document's theme to [onResult]. */
    fun chartPalettes(onResult: (List<io.github.artcraftandroid.wordcraft.core.ChartPaletteView>) -> Unit) = withEngine { e -> onResult(e.command { chartPalettes() }) }

    /** Hand the selected picture's crop (the left, top, right and bottom trims, fractions) to [onResult]. */
    fun pictureCrop(onResult: (List<Float>?) -> Unit) = withEngine { e -> onResult(e.command { pictureCrop() }) }

    /** Crop mode: the selected picture shows crop handles (the Crop button); dragging them crops it. */
    var cropping by mutableStateOf(false)

    /** A finished drag on the selected object: resize, move, turn or crop it, one undo step each. */
    fun objectEdit(e: io.github.artcraftandroid.wordcraft.ui.editor.ObjectEdit) {
        when (e) {
            is io.github.artcraftandroid.wordcraft.ui.editor.ObjectEdit.Rotate -> command("arrange.rotation", JSONObject().put("degrees", e.degrees.toDouble()).toString())
            is io.github.artcraftandroid.wordcraft.ui.editor.ObjectEdit.Frame -> {
                val p = JSONObject().put("width", e.width.toDouble()).put("height", e.height.toDouble())
                // a floating object moved, or resized from its top or left, goes to its new
                // top-left; one in the line only resizes (it stays where it is in the text)
                if (format?.objectInline == false && (e.moved || e.fromTopLeft)) p.put("x", e.x.toDouble()).put("y", e.y.toDouble()).put("page", e.page)
                command("arrange.bounds", p.toString())
            }
            is io.github.artcraftandroid.wordcraft.ui.editor.ObjectEdit.Crop -> withEngine { eng ->
                val c = eng.command { pictureCrop() } ?: return@withEngine
                if (c.size < 4) return@withEngine
                val b = e.before
                // the source's whole size on the page, then each side's new trim of it
                val fullW = b.width / (1f - c[0] - c[2]).coerceAtLeast(0.1f)
                val fullH = b.height / (1f - c[1] - c[3]).coerceAtLeast(0.1f)
                fun trim(v: Float) = v.coerceIn(0f, 0.45f)
                val l = trim(c[0] + (e.x - b.left) / fullW)
                val r = trim(c[2] + (b.right - (e.x + e.width)) / fullW)
                val t = trim(c[1] + (e.y - b.top) / fullH)
                val bo = trim(c[3] + (b.bottom - (e.y + e.height)) / fullH)
                val p = JSONObject().put("left", l.toDouble()).put("top", t.toDouble()).put("right", r.toDouble()).put("bottom", bo.toDouble())
                    .put("width", (fullW * (1f - l - r)).toDouble()).put("height", (fullH * (1f - t - bo)).toDouble())
                command("picture.crop", p.toString())
            }
        }
    }

    /** Hand the selected object's alt text and whether it is decorative to [onResult]. */
    fun objectAlt(onResult: (io.github.artcraftandroid.wordcraft.core.ObjectAlt?) -> Unit) = withEngine { e -> onResult(e.command { objectAlt() }) }

    /** In a dark theme, the pages shown light instead (Switch to Light Background). */
    var lightBackground by mutableStateOf(false)

    /** Closing with changes AutoSave won't keep: ask whether to save them first. */
    var confirmClose by mutableStateOf(false)

    fun openSettings() {
        overflowOpen = false
        screen = Screen.Settings
    }

    fun closeSettings() {
        screen = if (document != null) Screen.Editor else Screen.Start
    }

    /** The zoom the document was saved at, percent (0: fitting the page), which Print View opens at. */
    var docZoom by mutableStateOf(100)
        private set

    /** How far down the first page its text starts, points: Print View opens scrolled past it. */
    var textTop by mutableStateOf(0f)
        private set

    /** The document has drawings Mobile View can't show well (its message bar says so), unless closed. */
    var hasDrawings by mutableStateOf(false)
        private set
    var messageBarClosed by mutableStateOf(false)

    /** The Comments pane shows (in the palette's place). */
    var commentsOpen by mutableStateOf(false)
        private set

    /** The thread the Comments pane shows (null: the first), and whether a reply or a new comment is being written (the pane then takes the whole screen). */
    var commentFocus by mutableStateOf<Int?>(null)
    var commentWriting by mutableStateOf(false)

    /** Open the Comments pane on thread [focus] (or the first); [write] starts a new comment on the selection. */
    fun openComments(focus: Int? = null, write: Boolean = false) {
        commentFocus = focus
        commentWriting = write
        commentsOpen = true
        loadComments()
    }

    fun closeComments() {
        commentsOpen = false
        commentWriting = false
        commentFocus = null
        activeComment = null
        commentRange = emptyList()
    }

    /** The thread the Comments pane shows (marked on the pages: its text and its mark), and its range's rectangles. */
    var activeComment by mutableStateOf<Int?>(null)
        private set
    var commentRange by mutableStateOf<List<io.github.artcraftandroid.wordcraft.core.PageRect>>(emptyList())
        private set

    /** Mark the thread the Comments pane shows, after it moves to another or the pages change. */
    fun markActiveComment() = withEngine { e -> readActiveComment(e) }

    private suspend fun readActiveComment(e: Engine) {
        val id = if (commentsOpen) commentFocus ?: comments.firstOrNull { it.parent == null }?.id else null
        activeComment = id
        commentRange = if (id == null || id < 0) emptyList() else e.command { commentRangeRects(id.toUInt()) }
    }

    /** A comment as the pane lists it. */
    data class CommentItem(val id: Int, val author: String, val initials: String, val date: String, val dateUtc: String, val text: String, val resolved: Boolean, val parent: Int?, val anchor: JSONObject?)

    var comments by mutableStateOf<List<CommentItem>>(emptyList())
        private set

    fun loadComments() = withEngine { e -> comments = readComments(e) }

    private suspend fun readComments(e: Engine): List<CommentItem> {
        val out = e.command { runCatching { execute("review.comments", "") }.getOrNull() }
        return runCatching {
            val arr = JSONArray(out ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CommentItem(
                    id = o.optInt("id"),
                    author = o.optString("author"),
                    initials = o.optString("initials"),
                    date = o.optString("date"),
                    dateUtc = if (o.isNull("dateUtc")) "" else o.optString("dateUtc"),
                    text = o.optString("text"),
                    resolved = o.optBoolean("resolved"),
                    parent = if (o.isNull("parent")) null else o.optInt("parent"),
                    anchor = o.optJSONObject("anchor"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Post a new comment on the selection, and show its thread. */
    fun postComment(text: String) = withEngine { e ->
        val id = e.command { runCatching { JSONObject(execute("review.newComment", JSONObject().put("text", text).toString())).optInt("id", -1) }.getOrDefault(-1) }
        refresh(e)
        comments = readComments(e)
        commentFocus = id.takeIf { it >= 0 && comments.any { c -> c.id == it } }
        commentWriting = false
        if (commentFocus == null) closeComments()
    }

    /** Post a reply to thread [id], and show the thread again. */
    fun postReply(id: Int, text: String) = withEngine { e ->
        e.command { runCatching { execute("review.reply", JSONObject().put("id", id).put("text", text).toString()) } }
        refresh(e)
        comments = readComments(e)
        commentFocus = id
        commentWriting = false
    }

    /** Delete thread [id] with its replies; the pane goes on to the next thread, or closes after the last. */
    fun deleteThread(id: Int) = withEngine { e ->
        val threads = comments.filter { it.parent == null }
        val at = threads.indexOfFirst { it.id == id }
        e.command { runCatching { execute("review.deleteComment", JSONObject().put("id", id).toString()) } }
        refresh(e)
        comments = readComments(e)
        val left = comments.filter { it.parent == null }
        if (left.isEmpty()) closeComments() else commentFocus = left.getOrNull(at.coerceIn(0, left.size - 1))?.id
    }

    /** Run a comment command and list the comments again. */
    fun commentCommand(id: String, params: JSONObject) = withEngine { e ->
        e.command { runCatching { execute(id, params.toString()) } }
        refresh(e)
        loadComments()
    }

    /** Go to where comment [c] is. */
    fun goToComment(c: CommentItem) {
        val anchor = c.anchor ?: return
        command("caret.set", JSONObject().put("pos", anchor).toString())
    }

    /** Check Document: the proofing issues in the palette's place, and its Settings page. */
    var checkOpen by mutableStateOf(false)
    var checkSettings by mutableStateOf(false)

    /** An issue as the pane lists it: where it is, its text, and what it could change to. */
    data class ProofIssue(val start: JSONObject?, val end: JSONObject?, val text: String, val spelling: Boolean, val message: String, val suggestions: List<String>)

    var issues by mutableStateOf<List<ProofIssue>>(emptyList())
        private set

    /** Spelling and grammar marks, as the pane's switches set them (the reference marks no grammar). */
    var checkSpelling by mutableStateOf(true)
        private set
    var markGrammar by mutableStateOf(false)
        private set

    fun openCheck() {
        closePalette()
        checkSettings = false
        checkOpen = true
    }

    /** The document's issues, those the marks show. */
    fun loadIssues() = withEngine { e ->
        val out = e.command { runCatching { execute("review.issues", JSONObject().put("grammar", markGrammar).toString()) }.getOrNull() }
        issues = runCatching {
            val arr = JSONArray(out ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val s = o.optJSONArray("suggestions")
                ProofIssue(
                    start = o.optJSONObject("start"),
                    end = o.optJSONObject("end"),
                    text = o.optString("text"),
                    spelling = o.optString("kind") == "spelling",
                    message = o.optString("message"),
                    suggestions = if (s == null) emptyList() else (0 until s.length()).map { s.optString(it) },
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Turn spelling or grammar marks on or off. */
    fun setProofing(spelling: Boolean = checkSpelling, grammar: Boolean = markGrammar) {
        checkSpelling = spelling
        markGrammar = grammar
        command("file.options", JSONObject().put("checkSpelling", spelling).put("markGrammar", grammar).toString())
    }

    private fun selectIssue(i: ProofIssue) = "select.range" to JSONObject().put("anchor", i.start).put("focus", i.end).toString()

    /** Go to the issue, its text selected. */
    fun goToIssue(i: ProofIssue) {
        val (id, params) = selectIssue(i)
        command(id, params)
    }

    /** Change the issue's text to [text] (or ignore the word everywhere: null), then list the issues again. */
    fun fixIssue(i: ProofIssue, text: String?) = withEngine { e ->
        val (id, params) = selectIssue(i)
        e.command {
            runCatching {
                execute(id, params)
                if (text != null) execute("review.applySuggestion", JSONObject().put("text", text).toString()) else execute("review.ignoreAll", "")
            }
        }
        refresh(e)
        loadIssues()
    }

    /** The spelling or grammar suggestions for the word at the caret, offered in the context bar. */
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    /** Replace the misspelled word with [text]. */
    fun applySuggestion(text: String) {
        suggestions = emptyList()
        contextBar = false
        command("review.applySuggestion", JSONObject().put("text", text).toString())
    }

    /** Ignore All or Add to Dictionary for the word at the caret. */
    fun dismissSpelling(id: String) {
        suggestions = emptyList()
        contextBar = false
        command(id)
    }

    /** Bumped to hide the keyboard (a picture or shape was selected). */
    var hideKeyboardRequests by mutableStateOf(0)
        private set

    /** Edit Text: the caret goes into the selected shape's text (made if it has none), and the keyboard comes up. */
    fun editObjectText() = withEngine { e ->
        contextBar = false
        if (e.command { editObjectText() || runCatching { execute("shape.addText", "{}") }.isSuccess }) {
            refresh(e)
            inputGeneration++
            keyboardRequests++
        }
    }

    /** Replace: the selected picture becomes [bytes] (a photo picked). */
    fun replacePicture(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        contextBar = false
        command("picture.change", JSONObject().put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).toString())
    }

    /** The floating bar of Cut, Copy and Paste over the selection. */
    var contextBar by mutableStateOf(false)

    /** Drag a selection handle: `anchor` or `focus` to the point, or the caret (`caret`). */
    fun moveSelectionEnd(end: String, page: Int, x: Float, y: Float) = withEngine { e ->
        if (end == "caret") e.command { tap(page.toUInt(), x, y, false) } else e.command { setSelectionEnd(end, page.toUInt(), x, y) }
        refresh(e)
    }

    private val clipboard get() = getApplication<Application>().getSystemService(android.content.ClipboardManager::class.java)

    /** Copy (or cut) the selection to the phone's clipboard, as text and as formatted HTML. */
    fun copy(cut: Boolean) = withEngine { e ->
        val out = e.command { runCatching { execute(if (cut) "edit.cut" else "edit.copy", "") }.getOrNull() }
        val o = out?.let { runCatching { JSONObject(it) }.getOrNull() }
        val text = o?.optString("text").orEmpty()
        val html = o?.optString("html")?.takeIf { it.isNotBlank() }
        if (text.isNotEmpty()) {
            val clip = if (html != null) android.content.ClipData.newHtmlText("WordCraft", text, html) else android.content.ClipData.newPlainText("WordCraft", text)
            runCatching { clipboard?.setPrimaryClip(clip) }
        }
        contextBar = false
        refresh(e)
    }

    /** Paste what the phone's clipboard holds: formatted when it has HTML, else as text. */
    /** The phone's clipboard holds something to paste (a picture's context bar then offers Paste). */
    fun canPaste(): Boolean = runCatching { clipboard?.hasPrimaryClip() == true }.getOrDefault(false)

    fun paste() = withEngine { e ->
        val item = runCatching { clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) }.getOrNull()
        val p = JSONObject()
        item?.text?.toString()?.let { p.put("text", it) }
        item?.htmlText?.let { p.put("html", it) }
        e.command { execute("edit.paste", p.toString()) }
        contextBar = false
        refresh(e)
    }

    /** The full-screen panes and the sheet in the palette's place. */
    var linkPane by mutableStateOf(false)
    var commentPane by mutableStateOf(false)
    var headingsOpen by mutableStateOf(false)

    /** The link the caret is in, if any (Open, Edit and Remove Link). */
    var link by mutableStateOf<io.github.artcraftandroid.wordcraft.core.LinkHere?>(null)
        private set

    /** The link being edited in the Link pane (its text and address filled in). */
    var linkEdit by mutableStateOf<io.github.artcraftandroid.wordcraft.core.LinkHere?>(null)

    /** Web addresses to open in the phone's browser. */
    val linkRequests = Channel<String>(Channel.CONFLATED)

    /** Open the link at the caret: a place in the document is gone to, a web address opened. */
    fun openLink() {
        val target = link?.url ?: return
        contextBar = false
        when {
            target == "#_top" -> command("caret.docStart")
            target.startsWith("#") -> command("edit.goto", JSONObject().put("bookmark", target.drop(1)).toString())
            else -> linkRequests.trySend(if (target.contains(':')) target else "https://$target")
        }
    }

    /** Go to Footnote / Endnote: from the mark at the caret into its note. */
    fun goToNote() {
        contextBar = false
        command("references.goToNote")
    }

    /** Edit Link: the pane with the link's text and address. */
    fun startEditLink() {
        contextBar = false
        linkEdit = link
    }

    /** The edited link: its text (when changed) and address replace the old ones. */
    fun editLink(text: String, address: String) = withEngine { e ->
        val old = linkEdit
        linkEdit = null
        if (address.isBlank() || old == null) return@withEngine
        commitComposing(e)
        e.command {
            if (selectLink()) {
                if (text.isNotBlank() && text != old.text) {
                    execute("text.delete", "")
                    execute("insert.link", JSONObject().put("url", address).put("text", text).toString())
                } else {
                    execute("insert.link", JSONObject().put("url", address).toString())
                }
            }
        }
        refresh(e)
    }

    /** Remove Link: the text stays, as plain text. */
    fun removeLink() = withEngine { e ->
        contextBar = false
        commitComposing(e)
        e.command {
            if (selectLink()) execute("insert.removeLink", "")
        }
        refresh(e)
    }

    /** The thread whose mark a tap at (`x`, `y`) on `page` is on (its middle 5.2 pt in from the page's right edge, 12 pt across), if any. */
    private fun commentMarkAt(page: Int, x: Float, y: Float): Int? {
        val w = info?.pages?.getOrNull(page)?.width ?: return null
        return commentMarks.firstOrNull { m ->
            val l = m.line
            l.page.toInt() == page && kotlin.math.abs(x - (w - 5.2f)) <= 10f && kotlin.math.abs(y - (l.y + l.height * 0.32f)) <= 10f
        }?.id?.toInt()
    }

    /** Where each comment thread is anchored (its first line), for the marks beside the text. */
    var commentMarks by mutableStateOf<List<io.github.artcraftandroid.wordcraft.core.CommentMark>>(emptyList())
        private set

    /** Re-read the pages, the selection, the formatting and the engine's requests after a change. */
    private suspend fun refresh(e: Engine) {
        val r = e.command { Triple(documentInfo(), selectionView(), formatState()) }
        info = r.first
        selection = r.second
        format = r.third
        link = e.command { linkHere() }
        val extra = e.command { pageSetup() to reviewState() }
        pageSetup = extra.first
        review = extra.second
        commentMarks = e.command { commentMarks() }
        if (commentsOpen) readActiveComment(e)
        val events = e.command { takeEvents() }
        events.status?.let { if (it.isNotBlank()) message = it }
        scheduleAutoSave()
    }

    /** AutoSave: a pending save of the document to its own file, once the edits pause. */
    private var autoSaveJob: kotlinx.coroutines.Job? = null

    /** Whether the document can be saved quietly: AutoSave is on and it has a file the app may write, in a format that keeps everything. */
    private fun autoSavable(): Boolean {
        if (!autoSaveOn) return false
        val doc = document ?: return false
        val ext = doc.name.substringAfterLast('.', "").lowercase()
        return doc.uri != null && doc.writable && ext in setOf("docx", "docm", "dotx", "dotm", "odt", "rtf")
    }

    private fun scheduleAutoSave() {
        if (info?.dirty != true || !autoSavable()) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2500)
            autoSaveNow()
        }
    }

    /** Save now if there are changes and the document has a file (leaving the app, closing). */
    fun autoSaveNow() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        if (info?.dirty != true || !autoSavable()) return
        val doc = document ?: return
        val uri = doc.uri ?: return
        withEngine { e ->
            val bytes = e.command { saveBytes(doc.name) }
            val ok = withContext(Dispatchers.IO) { DocumentFiles.write(getApplication(), uri, bytes) }
            if (ok) {
                info = e.command { documentInfo() }
            } else {
                message = "Changes couldn't be saved to ${doc.name}. Use Save As to keep them."
            }
        }
    }

    /** What every opened or new document starts with. */
    private suspend fun started(e: Engine, doc: OpenDocument, edit: Boolean) {
        document = doc
        composing = ""
        findOpen = false
        findSettingsOpen = false
        findView = null
        insertBar = false
        e.command { setDark(io.github.artcraftandroid.wordcraft.ui.WordColors.dark) }
        pagesDark = io.github.artcraftandroid.wordcraft.ui.WordColors.dark
        editing = edit
        paletteOpen = false
        panels = emptyList()
        viewMode = if (edit || !startInMobileView) ViewMode.Print else ViewMode.Mobile
        if (viewMode == ViewMode.Mobile && mobileWidth > 0f) e.command { setView("mobile", mobileWidth) } else e.command { setView("print", 0f) }
        // the Display for Review chosen last, for this document too
        prefs.getString("markupView", null)?.takeIf { it != "allMarkup" }?.let { v ->
            e.command { runCatching { execute("review.markup", JSONObject().put("value", v).toString()) } }
        }
        colors = e.command { colorChoices() }
        themeFonts = e.command { themeFonts() }
        // the drawings message shows for each document with drawings Mobile View moves, until
        // closed, as the reference app's does
        hasDrawings = e.command { hasDrawings() }
        messageBarClosed = false
        docZoom = e.command { documentZoom() }.toInt()
        textTop = e.command { firstTextTop() }
        val key = doc.uri?.toString()
        // reopened where it was left past its first page: offered to go back there
        resumeOffer = key?.let { readingPlaces().optJSONObject(it) }?.takeIf { it.optInt("p") >= 1 }?.let { it.optDouble("f").toFloat() to it.optInt("p") }
        refresh(e)
        inputGeneration++
        screen = Screen.Editor
        // a new document is ready to type in: the keyboard comes up
        if (edit) keyboardRequests++
    }

    fun newDocument(template: String) = withEngine { e ->
        e.command { newDocument(template) }
        started(e, OpenDocument(if (template == "blank") "Document" else template.replaceFirstChar { it.uppercase() }, null, false), edit = true)
    }

    /** Open the document at [uri]: a copy in the app's cache is what the engine reads. */
    fun open(uri: Uri, writable: Boolean) = withEngine { e ->
        val ctx = getApplication<Application>()
        val name = DocumentFiles.displayName(ctx, uri)
        val file = try {
            withContext(Dispatchers.IO) { DocumentFiles.copyToCache(ctx, uri, name) }
        } catch (t: Throwable) {
            message = "The document can't be opened: ${t.userMessage}"
            return@withEngine
        }
        // a text file that isn't Unicode: its encoding is chosen first
        val offer = e.command { textEncodingOffer(file.absolutePath) }
        if (offer != null) {
            encodingChoice = EncodingChoice(file, uri, name, writable, offer)
            encodingLabel = offer.detected
            encodingPreview = e.command { textPreview(file.absolutePath, offer.detected) }
            return@withEngine
        }
        try {
            e.command { open(file.absolutePath, null) }
        } catch (_: MobileException.NeedsPassword) {
            message = "This document is protected with a password."
            return@withEngine
        }
        remember(uri, name)
        started(e, OpenDocument(name, uri, writable), edit = false)
    }

    /** A text file waiting for its encoding to be chosen ([TextEncodingScreen]): the copy the engine reads, where it came from, and what it can be read in. */
    class EncodingChoice(val file: java.io.File, val uri: Uri, val name: String, val writable: Boolean, val offer: io.github.artcraftandroid.wordcraft.core.TextEncodingOffer)

    var encodingChoice by mutableStateOf<EncodingChoice?>(null)
        private set

    /** The encoding chosen (its label) and the start of the text read in it. */
    var encodingLabel by mutableStateOf("")
        private set
    var encodingPreview by mutableStateOf("")
        private set

    /** Read the waiting text file in encoding [label] for the preview. */
    fun chooseEncoding(label: String) = withEngine { e ->
        val c = encodingChoice ?: return@withEngine
        encodingLabel = label
        encodingPreview = e.command { textPreview(c.file.absolutePath, label) }
    }

    /** Open the waiting text file in the encoding chosen. */
    fun openInEncoding() = withEngine { e ->
        val c = encodingChoice ?: return@withEngine
        encodingChoice = null
        e.command { openText(c.file.absolutePath, encodingLabel) }
        remember(c.uri, c.name)
        started(e, OpenDocument(c.name, c.uri, c.writable), edit = false)
    }

    /** Leave the waiting text file unopened. */
    fun cancelEncoding() {
        encodingChoice = null
    }

    // Views and modes.

    /** The document area is [widthPx] wide at [density] px per dp: Mobile View reflows to it. */
    fun setViewWidth(widthPx: Int, density: Float) {
        if (widthPx <= 0 || density <= 0f) return
        val w = widthPx / (density * MOBILE_DP_PER_PT) - 2 * MOBILE_PAD_PT
        if (abs(w - mobileWidth) < 1f) return
        mobileWidth = w
        if (viewMode == ViewMode.Mobile && screen == Screen.Editor) withEngine { e ->
            e.command { setView("mobile", w) }
            refresh(e)
        }
    }

    fun toggleMobileView() = withEngine { e ->
        commitComposing(e)
        viewMode = if (viewMode == ViewMode.Mobile) ViewMode.Print else ViewMode.Mobile
        if (viewMode == ViewMode.Mobile) e.command { setView("mobile", mobileWidth.coerceAtLeast(200f)) } else e.command { setView("print", 0f) }
        refresh(e)
    }

    /** Start editing (the pen); the keyboard comes up. */
    /** Start editing (the pen): the document shows as pages, and the keyboard comes up. */
    fun startEditing() = withEngine { e ->
        // editing keeps the view the document is shown in (Mobile View or the pages)
        commitComposing(e)
        editing = true
        keyboardRequests++
    }

    /** Stop editing (the check mark): the palette closes and the composing text goes in. */
    fun stopEditing() = withEngine { e ->
        commitComposing(e)
        // out of the equation being edited, if any (its shading and caret go)
        if (selection?.equation != null) e.command { runCatching { execute("equation.exit", "") } }
        editing = false
        paletteOpen = false
        panels = emptyList()
        refresh(e)
    }

    // The palette.

    /** The tab switcher's list is open over the pages. */
    var tabsOpen by mutableStateOf(false)
        private set

    /**
     * Open the palette, on one of its pages when [panel] is given (the quick bar's buttons). The
     * tab for what is selected (Table, Picture, Shape, Header & Footer) comes first when there is
     * one.
     */
    fun openPalette(panel: String? = null) {
        paletteOpen = true
        tabsOpen = false
        val context = io.github.artcraftandroid.wordcraft.ui.editor.contextTab(format)
        paletteTab = when {
            context != null -> context
            paletteTab !in io.github.artcraftandroid.wordcraft.ui.editor.PALETTE_TABS -> "Home"
            panel != null -> "Home"
            else -> paletteTab
        }
        panels = listOfNotNull(panel)
        if (fonts.isEmpty()) loadLists()
    }

    /** The document's table styles (id, name), for the Table tab. */
    fun tableStyles(onResult: (List<Pair<String, String>>) -> Unit) = withEngine { e ->
        val out = e.command { runCatching { execute("styles.manage", "") }.getOrNull() }
        val list = runCatching {
            val arr = JSONArray(out ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                if (o.optString("type") == "table" && !o.optBoolean("hidden")) o.optString("id") to o.optString("name") else null
            }
        }.getOrDefault(emptyList())
        onResult(list)
    }

    fun closePalette() {
        paletteOpen = false
        tabsOpen = false
        tellMeOpen = false
        panels = emptyList()
    }

    /** Tell Me, the palette's command search, shows in place of the tab's commands. */
    var tellMeOpen by mutableStateOf(false)

    /** Tell Me's last picks (their names), most recent first, kept between runs. */
    var tellMeRecent by mutableStateOf(prefs.getString("tellMeRecent", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
        private set

    fun noteTellMe(label: String) {
        tellMeRecent = (listOf(label) + tellMeRecent.filter { it != label }).take(TELL_ME_RECENT)
        prefs.edit().putString("tellMeRecent", tellMeRecent.joinToString("\n")).apply()
    }

    /** The palette on [tab]'s page [panel], as Tell Me opens it. */
    fun showPanel(tab: String, panel: String) {
        showTab(tab)
        panels = listOf(panel)
    }

    fun toggleTabs() {
        tabsOpen = !tabsOpen
    }

    fun showTab(tab: String) {
        paletteTab = tab
        tabsOpen = false
        tellMeOpen = false
        panels = emptyList()
    }

    fun pushPanel(panel: String) {
        panels = panels + panel
    }

    fun popPanel() {
        panels = panels.dropLast(1)
    }

    /** The floating toolbar shows its insert buttons (after +). */
    var insertBar by mutableStateOf(false)

    /** Open the palette on the Insert tab, on one of its pages when [panel] is given. */
    fun openInsert(panel: String? = null) {
        openPalette()
        paletteTab = "Insert"
        panels = listOfNotNull(panel)
        insertBar = false
    }

    /** The palette's collapse button: back to typing, with the keyboard. */
    fun collapsePalette() {
        closePalette()
        // with a picture, shape or chart selected the keyboard stays down, as after a tap on it
        if (editing && format?.`object` == null) keyboardRequests++
    }

    /** Toggle the palette (the edit bar's Show Commands). */
    fun togglePalette() {
        if (paletteOpen) closePalette() else openPalette()
    }

    private fun loadLists() = withEngine { e ->
        val families = e.command { runCatching { execute("font.list", """{"all":true}""") }.getOrNull() }
        fonts = parseFamilies(families)
    }

    private fun parseFamilies(json: String?): List<String> = runCatching {
        val v = json ?: return emptyList()
        val arr = if (v.trimStart().startsWith("[")) JSONArray(v) else JSONObject(v).optJSONArray("families") ?: JSONObject(v).optJSONArray("all") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            when (val item = arr.get(i)) {
                is String -> item
                is JSONObject -> item.optString("family").ifBlank { item.optString("name") }.ifBlank { null }
                else -> null
            }
        }.distinct().sortedBy { it.lowercase() }
    }.getOrDefault(emptyList())


    // Editing.

    /** Run an edit, then show its result. */
    fun edit(block: MobileSession.() -> Unit) = withEngine { e ->
        commitComposing(e)
        e.command(block)
        refresh(e)
    }

    /** Run the engine command [id] with [params] (JSON). */
    fun command(id: String, params: String = "") = edit { execute(id, params) }

    /** Run [id] and hand its result (parsed JSON) to [onResult], then show the change. */
    fun query(id: String, params: String = "", onResult: (JSONObject?) -> Unit) = withEngine { e ->
        val out = e.command { runCatching { execute(id, params) }.getOrNull() }
        refresh(e)
        onResult(out?.let { runCatching { JSONObject(it) }.getOrNull() })
    }

    fun zoom(kind: String) {
        zoomRequests.trySend(kind)
    }

    fun insertTable(rows: Int, cols: Int) {
        command("insert.table", JSONObject().put("rows", rows).put("cols", cols).toString())
        closePalette()
    }

    /** Insert the picture [bytes] (a photo picked or taken) at the caret. */
    fun insertPicture(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        command("insert.picture", JSONObject().put("data", data).toString())
    }

    fun insertLink(text: String, address: String) {
        val p = JSONObject().put("url", address)
        if (text.isNotBlank()) p.put("text", text)
        command("insert.link", p.toString())
        linkPane = false
        if (address.isNotBlank()) {
            recentLinks = (listOf(address) + recentLinks.filter { it != address }).take(5)
            prefs.edit().putString("recentLinks", recentLinks.joinToString(" ")).apply()
        }
    }

    /** The addresses linked lately, newest first (the Link page's list), kept space-separated. */
    var recentLinks by mutableStateOf(prefs.getString("recentLinks", null)?.split(' ')?.filter { it.isNotBlank() }.orEmpty())
        private set

    fun addComment(text: String) {
        if (text.isNotBlank()) command("review.newComment", JSONObject().put("text", text).toString())
        commentPane = false
    }

    // Find.

    /** The Find bar is open; what it looks for, with what to replace it, and its settings. */
    var findOpen by mutableStateOf(false)
        private set
    var findQuery by mutableStateOf("")
        private set
    var replaceText by mutableStateOf("")
    var findSettingsOpen by mutableStateOf(false)
    var replaceOn by mutableStateOf(false)
        private set
    var matchWord by mutableStateOf(false)
        private set
    var matchCase by mutableStateOf(false)
        private set
    var soundsLike by mutableStateOf(false)
        private set

    /** The matches and which is selected, after a search (null before one). */
    var findView by mutableStateOf<io.github.artcraftandroid.wordcraft.core.FindView?>(null)
        private set

    fun openFind() {
        if (paletteOpen) closePalette()
        insertBar = false
        findOpen = true
    }

    fun closeFind() = withEngine { e ->
        findOpen = false
        findSettingsOpen = false
        findView = null
        e.command { clearFind() }
    }

    private fun findParams(text: String = findQuery): JSONObject =
        JSONObject().put("text", text).put("matchCase", matchCase).put("wholeWord", matchWord).put("soundsLike", soundsLike)

    /** Look for [text] from the caret (as it is typed): the first match after the caret is selected. */
    fun findSearch(text: String) {
        findQuery = text
        withEngine { e ->
            if (text.isBlank()) {
                e.command { clearFind() }
                findView = null
                return@withEngine
            }
            commitComposing(e)
            findView = runCatching { e.command { findAgain(findParams(text).toString()) } }.getOrElse { e.command { findView() } }
            refresh(e)
        }
    }

    /** The next (1) or previous (-1) match. */
    fun findStep(dir: Int) = withEngine { e ->
        if (findQuery.isBlank()) return@withEngine
        runCatching { e.command { execute(if (dir < 0) "edit.findPrevious" else "edit.findNext", findParams().toString()) } }
        refresh(e)
        findView = e.command { findView() }
    }

    /** Replace the selected match with [replaceText] and select the next one. */
    fun replaceOne() = withEngine { e ->
        if (findQuery.isBlank()) return@withEngine
        commitComposing(e)
        runCatching { e.command { execute("edit.replace", findParams().put("with", replaceText).toString()) } }
        findView = runCatching { e.command { findAgain(findParams().toString()) } }.getOrElse { e.command { findView() } }
        refresh(e)
    }

    /** Replace every match with [replaceText]. */
    fun replaceAll() = withEngine { e ->
        if (findQuery.isBlank()) return@withEngine
        commitComposing(e)
        val out = runCatching { e.command { execute("edit.replaceAll", findParams().put("with", replaceText).toString()) } }.getOrNull()
        val n = runCatching { JSONObject(out ?: "{}").optInt("replaced") }.getOrDefault(0)
        message = if (n == 1) "Replaced 1 item." else "Replaced $n items."
        findView = runCatching { e.command { findAgain(findParams().toString()) } }.getOrElse { e.command { findView() } }
        refresh(e)
    }

    /** Find settings' boxes: Replace shows the replace row (and closes the settings), the others search again. */
    fun setFindOption(option: String, on: Boolean) {
        when (option) {
            "replace" -> {
                replaceOn = on
                findSettingsOpen = false
                return
            }
            "word" -> matchWord = on
            "case" -> matchCase = on
            "sounds" -> soundsLike = on
        }
        if (findQuery.isNotBlank()) findSearch(findQuery)
    }

    // Dark pages and dictation.

    /** The engine draws the pages dark (set once it does, so tiles drawn after are dark). */
    var pagesDark by mutableStateOf(false)
        private set

    /** Draw the pages dark (or not) to suit the theme. */
    fun setDarkPages(dark: Boolean) = withEngine { e ->
        e.command { setDark(dark) }
        pagesDark = dark
    }

    /** Requests to listen for speech to type (Dictation). */
    val dictationRequests = Channel<Unit>(Channel.CONFLATED)

    /** Type what was dictated at the caret, a space before it when it follows a word. */
    fun dictate(text: String) {
        val words = text.trim()
        if (words.isEmpty()) return
        val before = runCatching { inputTextAround(1, 0).before }.getOrDefault("")
        inputCommit(if (before.isNotEmpty() && !before.last().isWhitespace()) " $words" else words)
    }

    fun applyHighlight(name: String, hex: String) {
        if (name != "none") lastHighlight = name to hex
        command("format.highlight", """{"color":"$name"}""")
    }

    fun applyFontColor(hex: String) {
        if (hex != "auto") lastFontColor = hex
        command("format.color", """{"color":"$hex"}""")
    }

    /** A tap on the page: the caret goes there; while editing, the keyboard comes up. */
    fun tap(page: Int, x: Float, y: Float) = withEngine { e ->
        // a comment's mark beside its line opens that comment, as the reference's does
        val mark = if (viewMode == ViewMode.Mobile) commentMarkAt(page, x, y) else null
        if (mark != null) {
            openComments(mark)
            return@withEngine
        }
        commitComposing(e)
        contextBar = false
        e.command { tap(page.toUInt(), x, y, false) }
        refresh(e)
        // a tap on a picture or shape selects it: its bar shows, the keyboard doesn't
        if (format?.`object` != null) {
            contextBar = true
            editing = true
            hideKeyboardRequests++
            return@withEngine
        }
        inputGeneration++
        // while editing, a tap in a form's check box ticks it; in a list or date, it opens a picker
        if (editing) {
            val control = e.command { formControl() }
            if (control != null && !control.locked) {
                when (control.kind) {
                    "checkBox" -> {
                        e.command { execute("developer.toggleCheckBox", "") }
                        refresh(e)
                        return@withEngine
                    }
                    "dropDown", "comboBox", "date" -> {
                        formPick = control
                        hideKeyboardRequests++
                        return@withEngine
                    }
                }
            }
        }
        // a tap on a link: its bar (Open Link; and Edit and Remove Link while editing)
        if (!editing) {
            contextBar = link != null || format?.noteHere != null
            return@withEngine
        }
        if (editing) {
            paletteOpen = false
            keyboardRequests++
            // in an equation: its bar (its two forms), no spelling
            if (selection?.equation != null) {
                suggestions = emptyList()
                contextBar = true
                return@withEngine
            }
            // a misspelled word at the caret: its suggestions go in the context bar
            val issue = e.command { runCatching { execute("review.suggestions", "") }.getOrNull() }
            val list = issue?.let { runCatching { JSONObject(it).optJSONArray("suggestions") }.getOrNull() }
            suggestions = if (list == null) emptyList() else (0 until minOf(list.length(), 3)).map { list.optString(it) }.filter { it.isNotBlank() }
            // an issue at the caret (even one without suggestions), a link or a note's mark shows the bar
            contextBar = issue?.trimStart()?.startsWith("{") == true || link != null || format?.noteHere != null
        }
    }

    /** A form's list or date control waiting for its entry to be picked (a tap in it, while editing). */
    var formPick by mutableStateOf<io.github.artcraftandroid.wordcraft.core.FormControl?>(null)
        private set

    /** Fill the waiting list control with entry [index]. */
    fun chooseFormItem(index: Int) {
        formPick = null
        command("developer.chooseItem", JSONObject().put("index", index).toString())
    }

    /** Fill the waiting date control with [date] (`YYYY-MM-DD`). */
    fun setFormDate(date: String) {
        formPick = null
        command("developer.setDate", JSONObject().put("date", date).toString())
    }

    fun cancelFormPick() {
        formPick = null
    }

    /** A double tap or long press: select the word there (and start editing). */
    fun selectWord(page: Int, x: Float, y: Float) = withEngine { e ->
        commitComposing(e)
        suggestions = emptyList()
        e.command { selectAt(page.toUInt(), x, y, "word") }
        refresh(e)
        inputGeneration++
        editing = true
        contextBar = true
    }

    private suspend fun commitComposing(e: Engine) {
        val text = composing
        if (text.isNotEmpty()) {
            composing = ""
            e.command { typeText(text) }
        }
    }

    // The keyboard (EditorInputView). Its calls arrive on the main thread in order; edits queue on
    // the engine's command thread in the same order.

    fun inputCommit(text: String) {
        composing = ""
        if (text.isNotEmpty()) withEngine { e ->
            e.command { typeText(text) }
            refresh(e)
        }
    }

    fun inputCompose(text: String) {
        composing = text
    }

    fun inputDelete(before: Int, after: Int) {
        if (before > 0 || after > 0) withEngine { e ->
            e.command { deleteAround(before.coerceAtLeast(0).toUInt(), after.coerceAtLeast(0).toUInt()) }
            refresh(e)
        }
    }

    fun inputCommand(id: String, params: String = "") = withEngine { e ->
        e.command { execute(id, params) }
        refresh(e)
    }

    /** A hardware keyboard shortcut: run what the key map binds to it (and show the result). */
    fun inputShortcut(combo: String): Boolean {
        val e = engine ?: return false
        val ran = runCatching { e.commandBlocking { runShortcut(combo) } }.getOrDefault(false)
        if (ran) withEngine { refresh(it) }
        return ran
    }

    fun inputTextAround(before: Int, after: Int): TextAround {
        val e = engine ?: return TextAround("", "", "")
        return runCatching { e.commandBlocking { textAround(before.coerceAtLeast(0).toUInt(), after.coerceAtLeast(0).toUInt()) } }
            .getOrDefault(TextAround("", "", ""))
    }

    fun undo() = command("edit.undo")

    fun redo() = command("edit.redo")

    // Files.

    /** Save over the file the document came from, or ask where to save a copy. */
    fun save() = withEngine { e ->
        val doc = document ?: return@withEngine
        commitComposing(e)
        val name = docxName(doc.name)
        if (doc.uri == null || !doc.writable) {
            saveAsRequests.trySend(name)
            return@withEngine
        }
        val bytes = e.command { saveBytes(saveName(doc.name)) }
        val ok = withContext(Dispatchers.IO) { DocumentFiles.write(getApplication(), doc.uri, bytes) }
        if (!ok) saveAsRequests.trySend(name) else refresh(e)
    }

    /** Ask where to save a copy (Save As). */
    fun saveAs() {
        val doc = document ?: return
        saveAsRequests.trySend(docxName(doc.name))
    }

    /** Save a copy at [uri], which the user picked; it becomes the document's file. */
    fun saveTo(uri: Uri) = withEngine { e ->
        val ctx = getApplication<Application>()
        val name = DocumentFiles.displayName(ctx, uri)
        val bytes = e.command { saveBytes(saveName(name)) }
        val ok = withContext(Dispatchers.IO) { DocumentFiles.write(ctx, uri, bytes) }
        if (ok) {
            document = OpenDocument(name, uri, true)
            remember(uri, name)
            refresh(e)
        } else {
            message = "The document can't be saved there."
        }
    }

    // Picking up where the reader left off.

    /** On reopening a document read past its first page: where it was left (how far down, 0 to 1; the page, 0-based). */
    var resumeOffer by mutableStateOf<Pair<Float, Int>?>(null)
        private set

    fun dismissResume() {
        resumeOffer = null
    }

    /** Where each document was left, by its URI: `{"f": how far down, "p": page, "t": when}`, the newest [MAX_PLACES]. */
    private fun readingPlaces(): JSONObject = runCatching { JSONObject(prefs.getString("readingPlaces", null) ?: "{}") }.getOrDefault(JSONObject())

    /** The editor shows [fraction] of the way down the document, page [page] (0-based) at its top: remembered for its next opening. */
    fun notePlace(fraction: Float, page: Int) {
        val key = document?.uri?.toString() ?: return
        if (!fraction.isFinite()) return
        val places = readingPlaces()
        places.put(key, JSONObject().put("f", fraction.toDouble()).put("p", page).put("t", System.currentTimeMillis()))
        val keys = places.keys().asSequence().toList()
        if (keys.size > MAX_PLACES) {
            keys.sortedBy { places.optJSONObject(it)?.optLong("t") ?: 0L }.take(keys.size - MAX_PLACES).forEach { places.remove(it) }
        }
        prefs.edit().putString("readingPlaces", places.toString()).apply()
    }

    /** Leave the editor for the start screen, saving first when the document has a file. */
    /** Close the document: its changes are saved (AutoSave), or the user is asked what to do with them. */
    fun close() {
        val doc = document
        if (info?.dirty == true && doc != null) {
            if (autoSavable()) {
                autoSaveNow()
            } else {
                confirmClose = true
                return
            }
        }
        closeNow()
    }

    /** The close question's answer: save the changes first ([save]), or throw them away. */
    fun answerClose(save: Boolean) {
        confirmClose = false
        val doc = document
        if (save) {
            if (doc?.uri != null && doc.writable) {
                save()
            } else {
                saveAs()
                return
            }
        }
        closeNow()
    }

    private fun closeNow() {
        resumeOffer = null
        paletteOpen = false
        editing = false
        stopReadAloud()
        loadRecent()
        screen = Screen.Start
    }

    private fun docxName(name: String): String {
        val base = name.substringBeforeLast('.', name)
        return "$base.docx"
    }

    /** The name the engine saves under: the document's own when it keeps everything, else docx. */
    private fun saveName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext in setOf("docx", "docm", "dotx", "dotm", "odt", "rtf")) name else docxName(name)
    }
}
