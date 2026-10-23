package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.core.FormatState
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.WordColors

/** A tappable icon of the bars. */
@Composable
fun BarButton(
    glyph: Glyph,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 42.dp,
    height: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    pressed: Boolean = false,
    disabledTint: Color = WordColors.Disabled,
) {
    Box(
        modifier
            .size(width, height)
            .background(if (pressed) WordColors.Pressed else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, if (enabled) tint else disabledTint, size = iconSize)
    }
}

/** The name the reading bar shows: the file's, without its extension. */
fun titleOf(name: String): String = name.substringBeforeLast('.', name)

/** The bars' height, and their buttons' width. */
private val BAR_HEIGHT = 56.dp
private val BAR_BUTTON = 48.dp

/** Reading: close, the name, then Edit, Share, Find and More. */
@Composable
fun ViewTopBar(title: String, onClose: () -> Unit, onEdit: () -> Unit, onShare: () -> Unit, onFind: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(BAR_HEIGHT).background(WordColors.Bar).padding(end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        BarButton(LucideIcons.X.glyph(), WordColors.OnBar, onClose, width = BAR_HEIGHT, height = BAR_HEIGHT)
        Text(title, color = WordColors.OnBar, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        BarButton(LucideIcons.PenLine.glyph(), WordColors.OnBar, onEdit, width = BAR_BUTTON, height = BAR_HEIGHT)
        BarButton(LucideIcons.Share2.glyph(), WordColors.OnBar, onShare, width = BAR_BUTTON, height = BAR_HEIGHT)
        BarButton(LucideIcons.Search.glyph(), WordColors.OnBar, onFind, width = BAR_BUTTON, height = BAR_HEIGHT)
        BarButton(LucideIcons.Ellipsis.glyph(), WordColors.OnBar, onMore, width = BAR_BUTTON, height = BAR_HEIGHT)
    }
}

/**
 * Editing: Done at the left, then Show Commands, Share, Find, Mobile View, Undo and More at the
 * right, one 56 dp row with a faint shadow under it.
 */
@Composable
fun EditTopBar(
    canUndo: Boolean,
    mobileView: Boolean,
    onDone: () -> Unit,
    onShowCommands: () -> Unit,
    onShare: () -> Unit,
    onFind: () -> Unit,
    onMobileView: () -> Unit,
    onUndo: () -> Unit,
    onMore: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(BAR_HEIGHT).shadow(2.dp).background(WordColors.Bar).padding(end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        BarButton(LucideIcons.Check.glyph(), WordColors.OnBar, onDone, width = BAR_HEIGHT, height = BAR_HEIGHT)
        Spacer(Modifier.weight(1f))
        BarButton(LucideIcons.PenTool.glyph(), WordColors.OnBar, onShowCommands, width = BAR_BUTTON, height = BAR_HEIGHT)
        BarButton(LucideIcons.Share2.glyph(), WordColors.OnBar, onShare, width = BAR_BUTTON, height = BAR_HEIGHT)
        BarButton(LucideIcons.Search.glyph(), WordColors.OnBar, onFind, width = BAR_BUTTON, height = BAR_HEIGHT)
        // it shows the other view: the pages while in Mobile View, Mobile View while on the pages
        BarButton((if (mobileView) LucideIcons.FileText else LucideIcons.Smartphone).glyph(), WordColors.OnBar, onMobileView, width = BAR_BUTTON, height = BAR_HEIGHT)
        BarButton(LucideIcons.Undo2.glyph(), WordColors.OnBar, onUndo, enabled = canUndo, width = BAR_BUTTON, height = BAR_HEIGHT)
        BarButton(LucideIcons.Ellipsis.glyph(), WordColors.OnBar, onMore, width = BAR_BUTTON, height = BAR_HEIGHT)
    }
}

/** The floating toolbar's height and its buttons' width; it keeps this far from the screen's edges. */
private val PILL_HEIGHT = 48.dp
private val PILL_BUTTON = 48.dp
val PILL_MARGIN = 16.dp

/** A rounded floating bar (white with a soft shadow, or dark grey in dark). */
@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(PILL_HEIGHT / 2), color = WordColors.Bar, shadowElevation = if (WordColors.dark) 0.dp else 6.dp, content = content)
}

/** A button of the floating toolbar: on shows a circle behind it. */
data class PillItem(val glyph: Glyph, val on: Boolean = false, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The floating toolbar's text buttons, in order; as many as fit show (all of them on a wide
 * screen), the rest are in the palette.
 */
fun textPillItems(
    state: FormatState?,
    highlight: Color,
    fontColor: Color,
    canUndo: Boolean,
    onCommand: (String) -> Unit,
    onHighlight: () -> Unit,
    onFontColor: () -> Unit,
    onComment: () -> Unit,
    onLink: () -> Unit,
    onPicture: () -> Unit,
    onTable: () -> Unit,
    onUndo: () -> Unit,
    onDictate: () -> Unit,
): List<PillItem> = listOf(
    // Dictate: the phone's speech recognizer types what it hears
    PillItem(LucideIcons.Mic.glyph(), onClick = onDictate),
    PillItem(Letters.Bold, state?.bold == true) { onCommand("format.bold") },
    PillItem(Letters.Italic, state?.italic == true) { onCommand("format.italic") },
    PillItem(Letters.Underline, state?.underline == true) { onCommand("format.underline") },
    PillItem(Glyph.WithBar(LucideIcons.Highlighter.glyph(), highlight), onClick = onHighlight),
    PillItem(Glyph.WithBar(Letters.FontColor, fontColor), onClick = onFontColor),
    PillItem(LucideIcons.List.glyph(), state?.bullets == true) { onCommand("para.bullets") },
    PillItem(LucideIcons.ListOrdered.glyph(), state?.numbering == true) { onCommand("para.numbering") },
    PillItem(LucideIcons.ListIndentIncrease.glyph()) { onCommand("para.indent") },
    PillItem(LucideIcons.ListIndentDecrease.glyph()) { onCommand("para.outdent") },
    PillItem(Glyph.Letters("›¶"), state?.rtl == false) { onCommand("para.ltr") },
    PillItem(Glyph.Letters("¶‹"), state?.rtl == true) { onCommand("para.rtl") },
    PillItem(LucideIcons.MessageSquarePlus.glyph(), onClick = onComment),
    PillItem(LucideIcons.Link.glyph(), onClick = onLink),
    PillItem(LucideIcons.Image.glyph(), onClick = onPicture),
    PillItem(LucideIcons.TextCursorInput.glyph()) { onCommand("insert.textBox") },
    PillItem(LucideIcons.Table.glyph(), onClick = onTable),
    PillItem(LucideIcons.Undo2.glyph(), enabled = canUndo, onClick = onUndo),
)

/**
 * Editing, while the palette is closed: a rounded bar floating over the pages above the keyboard,
 * from the start side: Insert (+) unless [onInsert] is null (a selected object's bar), as many of
 * [items] as fit, and More (⋮), which opens the palette. Cut short, it spans the width with More at the end; otherwise it is just long enough.
 */
@Composable
fun FloatingToolbar(items: List<PillItem>, onInsert: (() -> Unit)?, onMore: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = PILL_MARGIN)) {
        val fit = ((maxWidth - PILL_BUTTON * (if (onInsert != null) 2 else 1)) / PILL_BUTTON).toInt().coerceAtLeast(0)
        val shown = items.take(fit)
        val cut = shown.size < items.size
        Pill(if (cut) Modifier.fillMaxWidth() else Modifier) {
            Row(Modifier.height(PILL_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                if (onInsert != null) PillButton(LucideIcons.Plus.glyph(), onClick = onInsert)
                for (item in shown) PillButton(item.glyph, item.on, item.enabled, item.onClick)
                if (cut) Spacer(Modifier.weight(1f))
                PillButton(LucideIcons.EllipsisVertical.glyph(), onClick = onMore)
            }
        }
    }
}

/**
 * The floating toolbar after Insert (+): a narrower bar of what can go in at the caret, with
 * Close (X) to go back and More (⋮) for the Insert tab.
 */
@Composable
fun InsertToolbar(
    onClose: () -> Unit,
    onPicture: () -> Unit,
    onCamera: () -> Unit,
    onPageBreak: () -> Unit,
    onTable: () -> Unit,
    onLink: () -> Unit,
    onShapes: () -> Unit,
    onComment: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = PILL_MARGIN), contentAlignment = Alignment.Center) {
        Pill {
            Row(Modifier.height(PILL_HEIGHT).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                PillButton(LucideIcons.X.glyph(), onClick = onClose)
                PillButton(LucideIcons.Image.glyph(), onClick = onPicture)
                PillButton(LucideIcons.Camera.glyph(), onClick = onCamera)
                PillButton(LucideIcons.SeparatorHorizontal.glyph(), onClick = onPageBreak)
                PillButton(LucideIcons.Table.glyph(), onClick = onTable)
                PillButton(LucideIcons.Link.glyph(), onClick = onLink)
                PillButton(LucideIcons.Shapes.glyph(), onClick = onShapes)
                PillButton(LucideIcons.MessageSquarePlus.glyph(), onClick = onComment)
                PillButton(LucideIcons.EllipsisVertical.glyph(), onClick = onMore)
            }
        }
    }
}

@Composable
private fun PillButton(glyph: Glyph, on: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(PILL_BUTTON, PILL_HEIGHT).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(40.dp).background(WordColors.PillOn, CircleShape))
        GlyphIcon(glyph, if (enabled) WordColors.OnPill else WordColors.Disabled)
    }
}

/** Reading: the floating pill near the bottom (Mobile View or Print Layout, Read Aloud, Comments, Share). */
@Composable
fun ViewPill(mobileView: Boolean, onToggleView: () -> Unit, onReadAloud: () -> Unit, onComments: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = WordColors.Bar, shadowElevation = if (WordColors.dark) 0.dp else 6.dp) {
        Row(Modifier.height(56.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            // the button shows the other view
            BarButton((if (mobileView) LucideIcons.FileText else LucideIcons.Smartphone).glyph(), WordColors.OnPill, onToggleView, width = 55.dp, height = 56.dp)
            BarButton(LucideIcons.AudioLines.glyph(), WordColors.OnPill, onReadAloud, width = 55.dp, height = 56.dp)
            BarButton(LucideIcons.MessageSquare.glyph(), WordColors.OnPill, onComments, width = 55.dp, height = 56.dp)
            BarButton(LucideIcons.Share2.glyph(), WordColors.OnPill, onShare, width = 55.dp, height = 56.dp)
        }
    }
}

/** On reopening a document: a card offering to go back to page [page], where it was left. */
@Composable
fun ResumeCallout(page: Int, onGo: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.clickable(onClick = onGo), shape = RoundedCornerShape(12.dp), color = WordColors.Bar, shadowElevation = if (WordColors.dark) 0.dp else 6.dp) {
        Row(Modifier.height(64.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(LucideIcons.BookOpenText.glyph(), WordColors.Accent, size = 24.dp)
            Column(Modifier.padding(start = 14.dp, end = 8.dp)) {
                Text("Pick up where you left off", color = WordColors.OnPill, fontSize = 15.sp)
                Text("Page $page", color = WordColors.Accent, fontSize = 14.sp)
            }
            BarButton(LucideIcons.X.glyph(), WordColors.OnPill, onClose, width = 48.dp, height = 64.dp, iconSize = 20.dp)
        }
    }
}

/** A one-pixel line along the bottom edge. */
fun Modifier.bottomEdge(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
}

/** A one-pixel line along the top edge. */
fun Modifier.topEdge(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
}

/** Parse `RRGGBB` (or `auto`, which is black) into a colour. */
fun hexColor(hex: String?, fallback: Color = Color.Black): Color {
    val h = hex?.takeIf { it.length == 6 } ?: return fallback
    return runCatching { Color(0xFF000000 or h.toLong(16)) }.getOrDefault(fallback)
}
