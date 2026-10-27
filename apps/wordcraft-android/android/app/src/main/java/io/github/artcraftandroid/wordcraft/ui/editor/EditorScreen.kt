package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.artcraftandroid.wordcraft.core.TextAround
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.ViewMode
import io.github.artcraftandroid.wordcraft.ui.WordColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** How the edit header leaves while the palette is open, and comes back: it glides with the layout. */
private val HEADER_OUT = slideOutVertically(tween(io.github.artcraftandroid.wordcraft.ui.Motion.BASIC, easing = io.github.artcraftandroid.wordcraft.ui.Motion.EaseOutSharp)) { -it } + shrinkVertically(tween(io.github.artcraftandroid.wordcraft.ui.Motion.BASIC, easing = io.github.artcraftandroid.wordcraft.ui.Motion.EaseOutSharp), shrinkTowards = Alignment.Top)
private val HEADER_IN = slideInVertically(tween(io.github.artcraftandroid.wordcraft.ui.Motion.BASIC, easing = io.github.artcraftandroid.wordcraft.ui.Motion.EaseOutSharp)) { -it } + expandVertically(tween(io.github.artcraftandroid.wordcraft.ui.Motion.BASIC, easing = io.github.artcraftandroid.wordcraft.ui.Motion.EaseOutSharp), expandFrom = Alignment.Top)

/** Full-screen panes (Link, Comment) come up from the bottom and drop back. */
private val PANE_IN = slideInVertically(tween(io.github.artcraftandroid.wordcraft.ui.Motion.BASIC, easing = io.github.artcraftandroid.wordcraft.ui.Motion.EaseOutSharp)) { it }
private val PANE_OUT = slideOutVertically(tween(io.github.artcraftandroid.wordcraft.ui.Motion.BASIC, easing = io.github.artcraftandroid.wordcraft.ui.Motion.EaseInSharp)) { it }

/**
 * The editor. Reading: a bar with Edit, the pages (Mobile View when the document opens) and a
 * floating pill. Editing: a bar of tools, the pages, and a toolbar floating above the keyboard;
 * or the palette in the keyboard's place (the bar slides away while the palette is open).
 */
@Composable
fun EditorScreen(vm: AppViewModel) {
    val engine = vm.engine ?: return
    val info = vm.info ?: return
    val mobile = vm.viewMode == ViewMode.Mobile
    val density = LocalDensity.current.density
    val viewport = remember(info.documentId, mobile) {
        if (mobile) {
            DocumentViewport(pad = io.github.artcraftandroid.wordcraft.ui.MOBILE_PAD_PT, gap = 0f, padTop = io.github.artcraftandroid.wordcraft.ui.MOBILE_TOP_PT)
        } else {
            // the pages open at the zoom the document was saved at, as the reference opens them
            // (100% is 160/72 dp a point, the scale of View › 100%)
            DocumentViewport(pad = 5f, gap = 12f, startScale = vm.docZoom / 100f * density * 160f / 72f, startTop = vm.textTop, hundred = density * 160f / 72f)
        }
    }
    var input by remember { mutableStateOf<EditorInputView?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val listener = remember(vm) {
        object : EditorInputListener {
            override fun commit(text: String) = vm.inputCommit(text)
            override fun compose(text: String) = vm.inputCompose(text)
            override fun deleteAround(before: Int, after: Int) = vm.inputDelete(before, after)
            override fun command(id: String, params: String) = vm.inputCommand(id, params)
            override fun shortcut(combo: String): Boolean = vm.inputShortcut(combo)
            override fun textAround(before: Int, after: Int): TextAround = vm.inputTextAround(before, after)
        }
    }

    BackHandler {
        when {
            vm.overflowOpen -> vm.overflowOpen = false
            vm.reading -> vm.stopReadAloud()
            vm.linkPane -> vm.linkPane = false
            vm.linkEdit != null -> vm.linkEdit = null
            vm.commentPane -> vm.commentPane = false
            vm.commentsOpen && vm.commentWriting && vm.commentFocus != null -> vm.commentWriting = false
            vm.commentsOpen -> vm.closeComments()
            vm.headingsOpen -> vm.headingsOpen = false
            vm.checkSettings -> vm.checkSettings = false
            vm.checkOpen -> vm.checkOpen = false
            vm.findSettingsOpen -> vm.findSettingsOpen = false
            vm.findOpen -> vm.closeFind()
            vm.tabsOpen -> vm.toggleTabs()
            vm.tellMeOpen -> vm.tellMeOpen = false
            vm.insertBar -> vm.insertBar = false
            vm.panels.isNotEmpty() -> vm.popPanel()
            vm.paletteOpen -> vm.closePalette()
            vm.editing -> vm.stopEditing()
            else -> vm.close()
        }
    }

    // the thread the Comments pane shows is marked on the pages
    LaunchedEffect(vm.commentsOpen, vm.commentFocus, vm.comments) { if (vm.commentsOpen) vm.markActiveComment() }

    // where the reader is, kept for the next time the document opens (once the view rests)
    LaunchedEffect(viewport) {
        androidx.compose.runtime.snapshotFlow { viewport.scrollFraction to (viewport.visiblePages().firstOrNull() ?: 0) }
            .collectLatest { (fraction, page) ->
                kotlinx.coroutines.delay(800)
                vm.notePlace(fraction, page)
            }
    }

    // the pages follow the theme (the engine draws them dark)
    val dark = WordColors.dark
    LaunchedEffect(dark, vm.lightBackground) { vm.setDarkPages(dark && !vm.lightBackground) }
    // the keyboard starts afresh when the caret moved other than by typing
    LaunchedEffect(vm.inputGeneration) { input?.restartInput() }
    // the app's own text fields (Alt Text, the link and comment panes) take the keyboard: the
    // document's input lets go of the focus meanwhile, or the keyboard would type into the page.
    // It comes first, so a palette closing with a keyboard request (its collapse button) has the
    // input focusable again before the request takes the focus.
    val fieldOpen = vm.paletteOpen || vm.linkPane || vm.linkEdit != null || vm.commentPane
    LaunchedEffect(fieldOpen, input) {
        val v = input ?: return@LaunchedEffect
        v.isFocusable = !fieldOpen
        v.isFocusableInTouchMode = !fieldOpen
        if (fieldOpen) v.clearFocus()
    }
    LaunchedEffect(vm.hideKeyboardRequests) { if (vm.hideKeyboardRequests > 0) input?.hideKeyboard() }
    LaunchedEffect(vm.keyboardRequests) { if (vm.keyboardRequests > 0 && vm.editing) input?.showKeyboard() }
    LaunchedEffect(vm.paletteOpen, vm.editing) { if (vm.paletteOpen || !vm.editing) input?.hideKeyboard() }
    LaunchedEffect(vm.editing, input) { if (!vm.editing) input?.textEditor = false }
    // closing the document: its keyboard goes with it
    val root = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(root) {
        onDispose {
            root.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(root.windowToken, 0)
        }
    }
    // the overflow sheet takes the whole screen: the keyboard goes
    LaunchedEffect(vm.overflowOpen) {
        if (vm.overflowOpen) {
            input?.hideKeyboard()
            keyboard?.hide()
        }
    }
    // Find settings take the keyboard's place
    LaunchedEffect(vm.findSettingsOpen) {
        if (vm.findSettingsOpen) {
            focusManager.clearFocus()
            keyboard?.hide()
            input?.hideKeyboard()
        }
    }
    // the View tab's zoom rows act on the pages' view
    LaunchedEffect(viewport) {
        for (z in vm.zoomRequests) {
            when (z) {
                "100" -> viewport.zoomTo(density * 160f / 72f)
                "in" -> viewport.zoomStep(true)
                "out" -> viewport.zoomStep(false)
                "onePage" -> viewport.fitPage(vm.selection?.focus?.page?.toInt() ?: 0)
                "pageWidth" -> viewport.fitWidth()
                // Zoom In on a chart or diagram: it fills the view
                "object" -> vm.selection?.objectFrame?.let { viewport.fitArea(it.page.toInt(), it.x, it.y, it.width, it.height) }
            }
        }
    }
    // crop mode ends when the picture is no longer selected
    LaunchedEffect(vm.format?.`object`) { if (vm.format?.`object` != "picture") vm.cropping = false }
    // the pages' area height, as the keyboard, the palette or a sheet below it shrinks it
    var docHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // the sentence being read stays in view, also when Audio Settings shrinks the pages' area
    LaunchedEffect(vm.readingRects, docHeight) {
        val r = vm.readingRects.firstOrNull() ?: return@LaunchedEffect
        if (!viewport.gesturing) viewport.reveal(r.page.toInt(), r.x, r.y, r.height, 48f * density)
    }
    val toolbarOn = vm.editing && !vm.paletteOpen && !vm.findSettingsOpen && !vm.reading && !vm.commentsOpen && !vm.headingsOpen && !vm.checkOpen
    // a finger scrolling towards the end hides the floating toolbar; scrolling back, a double tap,
    // a long press, a tap that brings the keyboard up or another view brings it back
    var toolbarAway by remember { mutableStateOf(false) }
    LaunchedEffect(viewport) {
        var last = viewport.scrollY
        androidx.compose.runtime.snapshotFlow { viewport.scrollY }.collect { y ->
            val moved = (y - last) * viewport.scale
            last = y
            if (viewport.gesturing && !viewport.zooming) {
                if (moved > 2f * density) toolbarAway = true else if (moved < -2f * density) toolbarAway = false
            }
        }
    }
    LaunchedEffect(vm.editing, vm.viewMode, vm.keyboardRequests) { toolbarAway = false }
    val away by androidx.compose.animation.core.animateFloatAsState(if (toolbarAway) 1f else 0f, tween(io.github.artcraftandroid.wordcraft.ui.Motion.FLOATING, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "toolbarAway")
    // switching between the pages and Mobile View: the canvas is covered, then shows through
    val cover = remember { androidx.compose.animation.core.Animatable(0f) }
    var lastMode by remember { mutableStateOf(vm.viewMode) }
    LaunchedEffect(vm.viewMode) {
        if (vm.viewMode == lastMode) return@LaunchedEffect
        lastMode = vm.viewMode
        cover.snapTo(1f)
        kotlinx.coroutines.delay(250)
        cover.animateTo(0.5f, tween(500, easing = androidx.compose.animation.core.LinearEasing))
        cover.snapTo(0f)
    }
    // keep the caret in view as it moves, and when the keyboard or the palette shrinks the pages'
    // area (clear of the floating toolbar)
    LaunchedEffect(vm.selection, docHeight) {
        val c = vm.selection?.focus ?: return@LaunchedEffect
        val below = if (toolbarOn) (48f + 2 * 16f) * density else 24f * density
        if (!viewport.gesturing && vm.editing) viewport.reveal(c.page.toInt(), c.x, c.top, c.height, 24f * density, below)
    }
    // Find's selected match shows in its own colour, not as the selection
    val find = vm.findView.takeIf { vm.findOpen }
    // the selected match comes up near the top, under the Find bar
    LaunchedEffect(find?.currentRects) {
        val r = find?.currentRects?.firstOrNull() ?: return@LaunchedEffect
        if (!viewport.gesturing) viewport.placeAt(r.page.toInt(), r.x, r.y, (40f + 24f) * density)
    }
    val showingMatch = find?.currentRects?.isNotEmpty() == true
    val selection = vm.selection?.let { if (showingMatch) it.copy(rects = emptyList()) else it }

    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val title = titleOf(vm.document?.name.orEmpty())
    // writing a comment, the light Comments pane takes the whole screen, the bars round it too
    val commentScreen = vm.commentsOpen && vm.commentWriting
    Box(Modifier.fillMaxSize().background(if (commentScreen) COMMENTS_GROUND else WordColors.StatusBand)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().statusBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
                .background(if (commentScreen) COMMENTS_GROUND else WordColors.Surface).navigationBarsPadding().imePadding(),
        ) {
            // the palette takes up to two fifths of the height (all of its 419 dp in portrait)
            val paletteHeight = min(PALETTE_HEIGHT, maxHeight * 0.4f)
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                // the Comments pane hides the bar, as the reference's does
                if (!vm.editing && !vm.findSettingsOpen && !vm.commentsOpen) {
                    ViewTopBar(
                        title = title,
                        onClose = vm::close,
                        onEdit = vm::startEditing,
                        onShare = vm::save,
                        onFind = vm::openFind,
                        onMore = { vm.overflowOpen = true },
                    )
                }
                // sideways, typing gets the whole height: the bar goes while the keyboard is up
                AnimatedVisibility(visible = vm.editing && !vm.paletteOpen && !vm.findSettingsOpen && !(landscape && imeBottom > 0), enter = HEADER_IN, exit = HEADER_OUT) {
                    EditTopBar(
                        canUndo = info.canUndo,
                        mobileView = mobile,
                        onDone = vm::stopEditing,
                        onShowCommands = {
                            if (vm.findOpen) vm.closeFind()
                            vm.togglePalette()
                        },
                        onShare = vm::save,
                        onFind = vm::openFind,
                        onMobileView = vm::toggleMobileView,
                        onUndo = vm::undo,
                        onMore = { vm.overflowOpen = true },
                    )
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().onSizeChanged {
                        vm.setViewWidth(it.width, density)
                        docHeight = it.height
                    },
                ) {
                    DocumentView(
                        engine = engine,
                        info = info,
                        selection = selection,
                        composing = vm.composing,
                        viewport = viewport,
                        mobile = mobile,
                        selectionColor = WordColors.Selection,
                        caretColor = if (vm.pagesDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
                        showCaret = vm.editing && !showingMatch,
                        onTap = { page, x, y -> vm.tap(page, x, y) },
                        onDoubleTap = { page, x, y ->
                            toolbarAway = false
                            vm.selectWord(page, x, y)
                        },
                        highlights = vm.readingRects,
                        wordHighlights = vm.readingWord,
                        commentMarks = if (vm.review?.showMarkup != false) vm.commentMarks.map { it.line } else emptyList(),
                        activeMark = vm.commentMarks.firstOrNull { it.id.toInt() == vm.activeComment }?.line,
                        activeRange = vm.commentRange,
                        modifier = Modifier.fillMaxSize(),
                        ink = if (vm.editing && vm.inkOn) {
                            val hl = vm.inkTool == "highlighter"
                            InkSettings(vm.inkTool, hexColor(if (hl) vm.highlighterColor else vm.penColor), if (hl) vm.highlighterWidth else vm.penWidth)
                        } else null,
                        onStroke = { page, points -> vm.stroke(page, points) },
                        onErase = { page, x, y -> vm.eraseAt(page, x, y) },
                        handleColor = if (vm.editing && !vm.inkOn && !showingMatch) WordColors.Accent else androidx.compose.ui.graphics.Color.Unspecified,
                        onHandleDrag = { end, page, x, y -> vm.moveSelectionEnd(end, page, x, y) },
                        dark = vm.pagesDark,
                        findMatches = find?.matches.orEmpty(),
                        findCurrent = find?.currentRects.orEmpty(),
                        objectHandles = vm.editing && !vm.inkOn && vm.format?.`object` != null,
                        objectMovable = vm.format?.objectInline == false,
                        keepRatio = vm.format?.`object` == "picture",
                        cropping = vm.cropping && vm.format?.`object` == "picture",
                        onObjectEdit = vm::objectEdit,
                    )
                    if (cover.value > 0f) Box(Modifier.matchParentSize().graphicsLayer { alpha = cover.value }.background(if (vm.pagesDark) WordColors.DarkPage else androidx.compose.ui.graphics.Color.White))
                    Scrubber(viewport, Modifier.align(Alignment.TopStart))
                    if (vm.contextBar && (vm.editing || vm.link != null || vm.format?.noteHere != null) && !vm.paletteOpen) {
                        val sel = vm.selection
                        ContextBar(
                            link = vm.link != null && sel?.collapsed != false,
                            editing = vm.editing,
                            onOpenLink = vm::openLink,
                            note = vm.format?.noteHere,
                            onGoToNote = vm::goToNote,
                            onEditLink = vm::startEditLink,
                            onRemoveLink = vm::removeLink,
                            hasSelection = sel?.collapsed == false,
                            equation = sel?.equation != null,
                            onEquationForm = { form -> vm.command("equation.convert", json("to" to form)) },
                            objectKind = vm.format?.`object`,
                            objectIcon = vm.format?.objectIcon == true,
                            canPaste = vm.canPaste(),
                            onDelete = { vm.command("text.delete") },
                            onReplace = {
                                vm.contextBar = false
                                vm.pictureRequests.trySend("replace")
                            },
                            onEditText = vm::editObjectText,
                            onZoomIn = {
                                vm.contextBar = false
                                vm.zoom("object")
                            },
                            suggestions = vm.suggestions,
                            onSuggestion = vm::applySuggestion,
                            onIgnore = { vm.dismissSpelling("review.ignoreAll") },
                            onAddWord = { vm.dismissSpelling("review.addToDictionary") },
                            onCut = { vm.copy(cut = true) },
                            onCopy = { vm.copy(cut = false) },
                            onPaste = vm::paste,
                            onComment = {
                                vm.contextBar = false
                                vm.openComments(write = true)
                            },
                            // centred over the selection, kept inside the view; under it when there's no room above
                            modifier = Modifier.layout { measurable, constraints ->
                                val margin = 8.dp.roundToPx()
                                val bar = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)))
                                layout(bar.width, bar.height) {
                                    val first = sel?.objectFrame ?: sel?.rects?.firstOrNull()
                                    val top = first?.let { viewport.onPage(it.page.toInt(), it.x + it.width / 2f, it.y) }
                                        ?: sel?.focus?.let { viewport.onPage(it.page.toInt(), it.x, it.top) }
                                        ?: androidx.compose.ui.geometry.Offset.Zero
                                    val bottom = sel?.focus?.let { viewport.onPage(it.page.toInt(), it.x, it.top + it.height) } ?: top
                                    val gap = 12.dp.toPx()
                                    val y = if (top.y - bar.height - gap > 0f) top.y - bar.height - gap else bottom.y + 30.dp.toPx()
                                    val x = (top.x - bar.width / 2f).coerceIn(margin.toFloat(), (constraints.maxWidth - bar.width - margin).toFloat().coerceAtLeast(margin.toFloat()))
                                    bar.place(x.toInt(), y.toInt())
                                }
                            },
                        )
                    }
                    AndroidView(
                        factory = { ctx -> EditorInputView(ctx).also { it.listener = listener; input = it } },
                        modifier = Modifier.size(1.dp),
                    )
                    if (vm.findOpen) FindBar(vm, Modifier.align(Alignment.TopCenter))
                    vm.formPick?.let { FormPicker(vm, it) }
                    vm.resumeOffer?.let { (fraction, page) ->
                        ResumeCallout(
                            page = page + 1,
                            onGo = {
                                viewport.scrollToFraction(fraction)
                                vm.dismissResume()
                            },
                            onClose = vm::dismissResume,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        )
                        LaunchedEffect(fraction, page) {
                            kotlinx.coroutines.delay(RESUME_SHOWN_MS)
                            vm.dismissResume()
                        }
                    }
                    // the drawings message, a card over the pages above the pill, as the reference's
                    Shown(
                        visible = !vm.editing && !vm.findOpen && !vm.reading && !vm.commentsOpen && !vm.headingsOpen && !vm.checkOpen && mobile && vm.hasDrawings && !vm.messageBarClosed,
                        enter = fadeIn(tween(io.github.artcraftandroid.wordcraft.ui.Motion.BASIC, delayMillis = io.github.artcraftandroid.wordcraft.ui.Motion.QUICKEST)),
                        exit = fadeOut(tween(io.github.artcraftandroid.wordcraft.ui.Motion.SHORT)),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (16 + 56 + 26).dp),
                    ) {
                        MessageCard(
                            text = "This document has pictures, shapes or drawings that Mobile View can't show the way the pages do.",
                            onPrintLayout = vm::toggleMobileView,
                            onClose = { vm.messageBarClosed = true },
                        )
                    }
                    if (!vm.editing && !vm.findOpen && !vm.reading && !vm.commentsOpen && !vm.headingsOpen && !vm.checkOpen) {
                        ViewPill(
                            mobileView = mobile,
                            onToggleView = vm::toggleMobileView,
                            onReadAloud = vm::startReadAloud,
                            onComments = { vm.openComments() },
                            onShare = vm::save,
                            modifier = if (landscape) Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 12.dp) else Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        )
                    }
                    // the toolbar floats over the pages above the keyboard: it slides up into place
                    // (from behind the keyboard), and goes at once
                    Shown(
                        visible = toolbarOn,
                        enter = slideInVertically(tween(io.github.artcraftandroid.wordcraft.ui.Motion.FLOATING, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { (80 * density).toInt() },
                        exit = ExitTransition.None,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = PILL_MARGIN).graphicsLayer {
                            translationY = away * 96f * density
                            alpha = 1f - away
                        },
                    ) {
                        if (vm.insertBar) {
                            InsertToolbar(
                                onClose = { vm.insertBar = false },
                                onPicture = { vm.pictureRequests.trySend("pick") },
                                onCamera = { vm.pictureRequests.trySend("camera") },
                                onPageBreak = { vm.command("insert.pageBreak") },
                                onTable = { vm.openInsert("table") },
                                onLink = { vm.linkPane = true },
                                onShapes = { vm.openInsert("shapes") },
                                onComment = { vm.openComments(write = true) },
                                onMore = { vm.openInsert() },
                            )
                        } else {
                            // a table cell's, picture's or shape's own buttons first; a picture or shape has no text buttons
                            val text = if (vm.format?.`object` != null) emptyList() else textPillItems(
                                state = vm.format,
                                highlight = hexColor(vm.lastHighlight.second),
                                fontColor = hexColor(vm.lastFontColor),
                                canUndo = info.canUndo,
                                onCommand = { vm.command(it) },
                                onHighlight = { vm.openPalette("highlight") },
                                onFontColor = { vm.openPalette("fontColor") },
                                onComment = { vm.openComments(write = true) },
                                onLink = { vm.linkPane = true },
                                onPicture = { vm.pictureRequests.trySend("pick") },
                                onTable = { vm.openInsert("table") },
                                onUndo = vm::undo,
                                onDictate = { vm.dictationRequests.trySend(Unit) },
                            )
                            // a selected picture, icon or shape's bar has no Insert (+), as the reference's has none
                            val insert = if (vm.format?.`object` != null) null else ({ vm.insertBar = true })
                            FloatingToolbar(contextPillItems(vm, contextTab(vm.format)) + text, onInsert = insert, onMore = { vm.openPalette() })
                        }
                    }
                }
                if (vm.reading) {
                    if (vm.audioSettingsOpen) AudioSettingsSheet(vm)
                    ReadAloudBar(vm)
                } else if (vm.commentsOpen && !vm.commentWriting) {
                    CommentsSheet(vm, Modifier.fillMaxWidth().height(paletteHeight))
                } else if (vm.headingsOpen) {
                    HeadingsSheet(vm, Modifier.fillMaxWidth().height(paletteHeight))
                } else if (vm.checkOpen) {
                    CheckSheet(vm, Modifier.fillMaxWidth().height(paletteHeight))
                } else if (vm.findSettingsOpen) {
                    FindSettingsSheet(vm, Modifier.fillMaxWidth().height(paletteHeight))
                } else if (vm.editing && vm.paletteOpen && (imeBottom == 0 || vm.paletteTyping)) {
                    PaletteSheet(vm, onFind = vm::openFind, modifier = Modifier.fillMaxWidth().height(paletteHeight))
                }
            }
            // writing a reply or a new comment: the Comments pane over everything, above the keyboard
            if (commentScreen) CommentsSheet(vm, Modifier.fillMaxSize())
            // the palette's tab list, over the pages and the palette, centred on the palette's
            // header; it grows out of its middle as it fades in
            val tabCount = PALETTE_TABS.size + (if (contextTab(vm.format) != null) 1 else 0)
            val listHeight = ROW * tabCount
            Shown(
                visible = vm.editing && vm.paletteOpen && imeBottom == 0 && vm.tabsOpen,
                enter = fadeIn(tween(io.github.artcraftandroid.wordcraft.ui.Motion.SHORT, easing = io.github.artcraftandroid.wordcraft.ui.Motion.EaseOutSharp)),
                exit = fadeOut(tween(io.github.artcraftandroid.wordcraft.ui.Motion.SHORT, easing = io.github.artcraftandroid.wordcraft.ui.Motion.EaseInSharp)),
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = (paletteHeight - ROW / 2 - listHeight / 2).coerceAtLeast(0.dp)),
            ) {
                val grow by transition.animateFloat(
                    transitionSpec = { tween(io.github.artcraftandroid.wordcraft.ui.Motion.SHORT, easing = if (targetState == androidx.compose.animation.EnterExitState.Visible) io.github.artcraftandroid.wordcraft.ui.Motion.EaseOutSharp else io.github.artcraftandroid.wordcraft.ui.Motion.EaseInSharp) },
                    label = "tabsGrow",
                ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0.75f }
                TabSwitcher(vm, Modifier.graphicsLayer {
                    scaleY = grow
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                })
            }
            Shown(visible = vm.linkPane, enter = PANE_IN, exit = PANE_OUT) {
                FormPane("Link", "Insert", listOf("Text to display", "Address"), onDone = { (text, address) -> vm.insertLink(text, address) }, onCancel = { vm.linkPane = false }, modifier = Modifier.fillMaxSize())
            }
            val editing = vm.linkEdit
            Shown(visible = editing != null, enter = PANE_IN, exit = PANE_OUT) {
                val l = editing ?: return@Shown
                FormPane(
                    "Edit Link",
                    "Update",
                    listOf("Text to display", "Address"),
                    onDone = { (text, address) -> vm.editLink(text, address) },
                    onCancel = { vm.linkEdit = null },
                    modifier = Modifier.fillMaxSize(),
                    initial = listOf(l.text, l.url),
                )
            }
            Shown(visible = vm.commentPane, enter = PANE_IN, exit = PANE_OUT) {
                FormPane("Comment", "Post", listOf("Comment"), onDone = { (text) -> vm.addComment(text) }, onCancel = { vm.commentPane = false }, modifier = Modifier.fillMaxSize())
            }
            OverflowSheet(vm, visible = vm.overflowOpen, onSaveAs = { vm.saveAs() })
            if (vm.confirmClose) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { vm.confirmClose = false },
                    title = { Text("Save changes?") },
                    text = { Text("Do you want to keep the changes to ${titleOf(vm.document?.name.orEmpty())}?") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.answerClose(save = true) }) { Text("Save") } },
                    dismissButton = {
                        Row {
                            androidx.compose.material3.TextButton(onClick = { vm.confirmClose = false }) { Text("Cancel") }
                            androidx.compose.material3.TextButton(onClick = { vm.answerClose(save = false) }) { Text("Don't Save") }
                        }
                    },
                    containerColor = WordColors.Surface,
                    titleContentColor = WordColors.OnSurface,
                    textContentColor = WordColors.OnSurface,
                )
            }
        }
    }
}

/** [AnimatedVisibility] for content over the pages (outside any row or column's scope). */
@Composable
private fun Shown(
    visible: Boolean,
    enter: androidx.compose.animation.EnterTransition,
    exit: ExitTransition,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit,
) = AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit, content = content)

/**
 * The navigation scrubber: a thin tab on the left edge at how far down the document is; drag it
 * to jump through the document.
 */
@Composable
private fun Scrubber(viewport: DocumentViewport, modifier: Modifier = Modifier) {
    val tabH = 95.dp
    BoxWithConstraints(modifier.fillMaxSize()) {
        val travel = maxHeight - tabH
        val density = LocalDensity.current
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(0, (viewport.scrollFraction * travel.toPx()).toInt()) }
                .size(14.dp, tabH)
                .pointerInput(viewport) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        val range = with(density) { travel.toPx() }
                        if (range > 0f) viewport.scrollToFraction(viewport.scrollFraction + dy / range)
                    }
                },
        ) {
            Box(Modifier.size(4.dp, tabH).background(WordColors.Surface, shape).border(1.dp, WordColors.PageBorder, shape))
        }
    }
}

/** A grey bar under the title: one line and a chevron, or opened, the whole text and two buttons. */
/**
 * Mobile View's message for a document with drawings it moves, as a card over the pages above the
 * pill (as the reference's): two lines of it, Print Layout, and a chevron that opens it to the
 * whole message and Close.
 */
@Composable
private fun MessageCard(text: String, onPrintLayout: () -> Unit, onClose: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(WordColors.MessageCard, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 61.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                color = WordColors.OnMessageCard,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = if (open) 6 else 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
            )
            Text(
                "Print Layout",
                color = WordColors.OnMessageCard,
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onPrintLayout).padding(horizontal = 14.dp, vertical = 12.dp),
            )
            BarButton((if (open) LucideIcons.ChevronDown else LucideIcons.ChevronUp).glyph(), WordColors.OnMessageCard, { open = !open }, width = 44.dp, height = 48.dp)
        }
        if (open) {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                Text("Close", color = WordColors.OnMessageCard, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onClose).padding(12.dp))
            }
        }
    }
}

/** Read Aloud's player: previous, play or pause, next, the rate, and close. */
@Composable
private fun ReadAloudBar(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().height(56.dp).background(WordColors.Bar).topEdge(WordColors.StripMore), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        BarButton(LucideIcons.SkipBack.glyph(), WordColors.OnPill, { vm.skipSentence(-1) }, width = 52.dp, height = 56.dp)
        BarButton((if (vm.readingPaused) LucideIcons.Play else LucideIcons.Pause).glyph(), WordColors.Accent, vm::toggleReading, width = 52.dp, height = 56.dp)
        BarButton(LucideIcons.SkipForward.glyph(), WordColors.OnPill, { vm.skipSentence(1) }, width = 52.dp, height = 56.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        BarButton(LucideIcons.Settings.glyph(), if (vm.audioSettingsOpen) WordColors.Accent else WordColors.OnPill, { vm.audioSettingsOpen = !vm.audioSettingsOpen }, width = 52.dp, height = 56.dp)
        BarButton(LucideIcons.X.glyph(), WordColors.OnPill, vm::stopReadAloud, width = 52.dp, height = 56.dp)
    }
}

/** Read Aloud's Audio Settings: the reading speed, and the voice it reads in. */
@Composable
private fun AudioSettingsSheet(vm: AppViewModel) {
    var rate by remember(vm.readingRate) { mutableStateOf(vm.readingRate) }
    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).background(WordColors.Surface).topEdge(WordColors.StripMore).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Caption("Reading Speed")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Slow", color = WordColors.OnSurface, fontSize = 14.sp)
            androidx.compose.material3.Slider(
                value = rate,
                onValueChange = { rate = it },
                onValueChangeFinished = { vm.chooseReadingRate(rate) },
                valueRange = io.github.artcraftandroid.wordcraft.ui.MIN_READING_RATE..io.github.artcraftandroid.wordcraft.ui.MAX_READING_RATE,
                steps = 7,
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = WordColors.Accent, activeTrackColor = WordColors.Accent),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text("Fast", color = WordColors.OnSurface, fontSize = 14.sp)
        }
        if (vm.readingVoices.isNotEmpty()) {
            Caption("Voice")
            ChoiceRow("Default", vm.readingVoice.isEmpty() || vm.readingVoices.none { it.first == vm.readingVoice }, onClick = { vm.chooseReadingVoice("") }, indent = 16.dp, size = 16)
            for ((name, label) in vm.readingVoices) {
                ChoiceRow(label, vm.readingVoice == name, onClick = { vm.chooseReadingVoice(name) }, indent = 16.dp, size = 16)
            }
        }
    }
}

/** The floating bar over a selection: Cut, Copy, Paste, New Comment. */
@Composable
private fun ContextBar(
    hasSelection: Boolean,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onComment: () -> Unit,
    modifier: Modifier = Modifier,
    objectKind: String? = null,
    objectIcon: Boolean = false,
    canPaste: Boolean = false,
    onDelete: () -> Unit = {},
    onReplace: () -> Unit = {},
    onEditText: () -> Unit = {},
    onZoomIn: () -> Unit = {},
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    onIgnore: () -> Unit = {},
    onAddWord: () -> Unit = {},
    link: Boolean = false,
    note: String? = null,
    onGoToNote: () -> Unit = {},
    editing: Boolean = true,
    onOpenLink: () -> Unit = {},
    onEditLink: () -> Unit = {},
    onRemoveLink: () -> Unit = {},
    equation: Boolean = false,
    onEquationForm: (String) -> Unit = {},
) {
    androidx.compose.material3.Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = WordColors.Bar, shadowElevation = 6.dp) {
        Row(Modifier.height(41.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun item(label: String, onClick: () -> Unit) {
                Text(label, color = WordColors.OnPill, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp))
            }
            if (equation) {
                // the equation being edited: built up, or as a line of text
                item("Professional") { onEquationForm("professional") }
                item("Linear") { onEquationForm("linear") }
                return@Row
            }
            if (note != null && objectKind == null && !hasSelection) {
                // the caret on a footnote's or endnote's mark: to its note
                item(if (note == "endnote") "Go to Endnote" else "Go to Footnote", onGoToNote)
                if (!editing) return@Row
                item("Paste", onPaste)
                item("New Comment", onComment)
                return@Row
            }
            if (link && objectKind == null) {
                // the caret in a link: open it; while editing also change or remove it
                item("Open Link", onOpenLink)
                if (!editing) return@Row
                item("Edit Link", onEditLink)
                item("Remove Link", onRemoveLink)
                if (suggestions.isEmpty()) {
                    item("Paste", onPaste)
                    item("New Comment", onComment)
                    return@Row
                }
            }
            if (!hasSelection && objectKind == null) {
                // a misspelled word at the caret: its suggestions, then Ignore All and Add to Dictionary
                for (word in suggestions) {
                    Text(word, color = WordColors.OnPill, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.clickable { onSuggestion(word) }.padding(horizontal = 14.dp, vertical = 10.dp))
                }
                item("Ignore All", onIgnore)
                item("Add to Dictionary", onAddWord)
                return@Row
            }
            if (objectKind != null) {
                // a picture: Cut, Copy, Paste (with something to paste), Delete, Replace, Zoom In (an
                // icon), New Comment; a shape: Cut, Copy, Delete, Edit Text; a chart or diagram: Cut,
                // Copy, Delete, Zoom In, New Comment
                item("Cut", onCut)
                item("Copy", onCopy)
                if (objectKind == "picture" && canPaste) item("Paste", onPaste)
                item("Delete", onDelete)
                if (objectKind == "picture") {
                    item("Replace", onReplace)
                    if (objectIcon) item("Zoom In", onZoomIn)
                    item("New Comment", onComment)
                } else if (objectKind == "shape") {
                    item("Edit Text", onEditText)
                } else if (objectKind == "chart" || objectKind == "smartArt") {
                    item("Zoom In", onZoomIn)
                    item("New Comment", onComment)
                }
                return@Row
            }
            if (hasSelection) {
                item("Cut", onCut)
                item("Copy", onCopy)
            }
            item("Paste", onPaste)
            item("New Comment", onComment)
        }
    }
}

/**
 * Find, over the top of the pages: its settings, the field (the count, Clear and Search inside
 * it), the previous and next match, and Close; with Replace on, a second row to replace them.
 */
@Composable
private fun FindBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val view = vm.findView
    val count = view?.count?.toInt()
    val hasQuery = vm.findQuery.isNotBlank()
    Column(modifier.fillMaxWidth().shadow(if (WordColors.dark) 0.dp else 3.dp).background(WordColors.FindBar)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(4.dp))
            BarButton(LucideIcons.Settings.glyph(), WordColors.OnStrip, { vm.findSettingsOpen = true }, width = 32.dp, height = 32.dp, iconSize = 22.dp)
            Spacer(Modifier.width(4.dp))
            Row(Modifier.weight(1f).height(32.dp).background(WordColors.FindField), verticalAlignment = Alignment.CenterVertically) {
                FindField(
                    value = vm.findQuery,
                    hint = "Find...",
                    onValueChange = vm::findSearch,
                    imeAction = ImeAction.Search,
                    onAction = { vm.findStep(1) },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (hasQuery && view != null && count != null) {
                    val current = view.current
                    val label = when {
                        count == 0 -> "No results"
                        current != null -> "${current.toInt() + 1} of $count"
                        else -> "$count results"
                    }
                    Text(label, color = WordColors.OnStrip, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp))
                }
                if (vm.findQuery.isNotEmpty()) BarButton(LucideIcons.X.glyph(), WordColors.OnStrip, { vm.findSearch("") }, width = 30.dp, height = 32.dp, iconSize = 20.dp)
                BarButton(LucideIcons.Search.glyph(), WordColors.OnStrip, { vm.findStep(1) }, width = 30.dp, height = 32.dp, iconSize = 20.dp)
            }
            Spacer(Modifier.width(4.dp))
            BarButton(LucideIcons.ChevronLeft.glyph(), WordColors.OnStrip, { vm.findStep(-1) }, enabled = (count ?: 0) > 0, width = 32.dp, height = 32.dp)
            Spacer(Modifier.width(4.dp))
            BarButton(LucideIcons.ChevronRight.glyph(), WordColors.OnStrip, { vm.findStep(1) }, enabled = (count ?: 0) > 0, width = 32.dp, height = 32.dp)
            Box(Modifier.padding(horizontal = 2.dp).size(1.dp, 15.dp).background(WordColors.Disabled))
            BarButton(LucideIcons.X.glyph(), WordColors.OnStrip, { vm.closeFind() }, width = 32.dp, height = 32.dp)
            Spacer(Modifier.width(4.dp))
        }
        if (vm.replaceOn) {
            Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 40.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(32.dp).background(WordColors.FindField), contentAlignment = Alignment.CenterStart) {
                    FindField(value = vm.replaceText, hint = "Replace...", onValueChange = { vm.replaceText = it }, imeAction = ImeAction.Next, onAction = { vm.replaceOne() })
                }
                val tint = if (hasQuery) WordColors.OnStrip else WordColors.Disabled
                Text("Replace", color = tint, fontSize = 16.sp, modifier = Modifier.clickable(enabled = hasQuery) { vm.replaceOne() }.padding(horizontal = 8.dp, vertical = 8.dp))
                Box(Modifier.size(1.dp, 15.dp).background(WordColors.Disabled))
                Text("Replace All", color = tint, fontSize = 16.sp, modifier = Modifier.clickable(enabled = hasQuery) { vm.replaceAll() }.padding(horizontal = 8.dp, vertical = 8.dp))
            }
        }
    }
}

/** A one-line field of the Find bar, its hint showing while it is empty. */
@Composable
private fun FindField(value: String, hint: String, onValueChange: (String) -> Unit, imeAction: ImeAction, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(start = 6.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(hint, color = WordColors.FindHint, fontSize = 16.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = WordColors.OnSurface, fontSize = 16.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(WordColors.Accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onSearch = { onAction() }, onNext = { onAction() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Find settings, in the keyboard's place (as tall as the palette): Replace, then how the text matches. */
@Composable
private fun FindSettingsSheet(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier.background(WordColors.Surface)) {
        Row(Modifier.fillMaxWidth().height(48.dp).background(WordColors.Strip).bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
            Text("Find settings", color = WordColors.OnSurface, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
            BarButton(LucideIcons.X.glyph(), WordColors.OnSurface, { vm.findSettingsOpen = false }, width = 56.dp, height = 48.dp)
        }
        Spacer(Modifier.height(8.dp))
        CheckRow("Replace", vm.replaceOn) { vm.setFindOption("replace", it) }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(WordColors.SettingsLine))
        Spacer(Modifier.height(8.dp))
        CheckRow("Match Word", vm.matchWord) { vm.setFindOption("word", it) }
        CheckRow("Match Case", vm.matchCase) { vm.setFindOption("case", it) }
        CheckRow("Sounds Like (English)", vm.soundsLike) { vm.setFindOption("sounds", it) }
    }
}

@Composable
private fun CheckRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(19.dp))
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
        Box(
            Modifier.size(18.dp).background(if (on) WordColors.Accent else WordColors.CheckOff, shape).border(1.dp, if (on) WordColors.Accent else WordColors.CheckEdge, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) GlyphIcon(LucideIcons.Check.glyph(), androidx.compose.ui.graphics.Color.White, size = 14.dp)
        }
        Spacer(Modifier.width(9.dp))
        Text(label, color = WordColors.OnSurface, fontSize = 17.sp)
    }
}

/** How long Pick Up Where You Left Off stays offered, ms. */
private const val RESUME_SHOWN_MS = 10_000L

/** A form's list control (its entries, the chosen one marked) or date control (the system's date picker), to fill it in. */
@Composable
private fun FormPicker(vm: AppViewModel, control: io.github.artcraftandroid.wordcraft.core.FormControl) {
    if (control.kind == "date") {
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.DisposableEffect(control) {
            val today = java.util.Calendar.getInstance()
            val parts = control.date.split("-").mapNotNull { it.toIntOrNull() }
            val (y, m, d) = if (parts.size == 3) Triple(parts[0], parts[1] - 1, parts[2]) else Triple(today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH), today.get(java.util.Calendar.DAY_OF_MONTH))
            val dialog = android.app.DatePickerDialog(context, { _, year, month, day -> vm.setFormDate("%04d-%02d-%02d".format(year, month + 1, day)) }, y, m, d)
            dialog.setOnCancelListener { vm.cancelFormPick() }
            dialog.show()
            onDispose { dialog.setOnCancelListener(null); dialog.dismiss() }
        }
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = vm::cancelFormPick,
        title = { Text(control.title.ifBlank { "Choose an item" }) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                for ((i, item) in control.items.withIndex()) {
                    Row(Modifier.fillMaxWidth().height(48.dp).clickable { vm.chooseFormItem(i) }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(
                            selected = i == control.chosen,
                            onClick = { vm.chooseFormItem(i) },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = WordColors.Accent),
                        )
                        Text(item, color = WordColors.OnSurface, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = vm::cancelFormPick) { Text("Cancel") } },
        containerColor = WordColors.Surface,
        titleContentColor = WordColors.OnSurface,
    )
}
