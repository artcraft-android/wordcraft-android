package io.github.artcraftandroid.wordcraft.ui.editor

import android.content.Context
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.view.inputmethod.EditorInfoCompat
import io.github.artcraftandroid.wordcraft.core.TextAround

/** What the keyboard does to the document (the editor's view model answers). */
interface EditorInputListener {
    /** Text to type at the caret, replacing the selection. */
    fun commit(text: String)

    /** The keyboard's word in progress, shown at the caret until committed. */
    fun compose(text: String)

    /** Delete characters before and after the caret. */
    fun deleteAround(before: Int, after: Int)

    /** An engine command for a key (Enter, arrows, Delete…). */
    fun command(id: String, params: String = "")

    /** A hardware keyboard's shortcut (`Mod+B`…) through the key map; whether it did something. */
    fun shortcut(combo: String): Boolean

    /** The text around the selection in its paragraph, in UTF-16 units. */
    fun textAround(before: Int, after: Int): TextAround
}

/**
 * The keyboard's way into the document: a view with no size that holds the input focus. The
 * document itself lives in the engine, so keyboard actions (the on-screen keyboard's, through
 * its connection, and a hardware keyboard's keys) are forwarded to it rather than editing text
 * of the view's own.
 */
class EditorInputView(context: Context) : View(context) {
    var listener: EditorInputListener? = null
    private val imm = context.getSystemService(InputMethodManager::class.java)

    /** The on-screen keyboard's word in progress, not in the document yet. */
    private var composing = ""

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Off while the document is only read: the system then doesn't bring the keyboard up for this
     * view by itself (as it does when a dialog closes and the focus comes back).
     */
    var textEditor = true

    override fun onCheckIsTextEditor(): Boolean = textEditor

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        val around = listener?.textAround(500, 500) ?: TextAround("", "", "")
        outAttrs.initialSelStart = around.before.length
        outAttrs.initialSelEnd = around.before.length + around.selected.length
        EditorInfoCompat.setInitialSurroundingText(outAttrs, around.before + around.selected + around.after)
        return EditorConnection(this)
    }

    /** Take the focus and show the keyboard. */
    fun showKeyboard() {
        if (!textEditor) {
            textEditor = true
            imm?.restartInput(this)
        }
        requestFocus()
        imm?.showSoftInput(this, 0)
    }

    fun hideKeyboard() {
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    /** The caret moved other than by typing: the keyboard forgets its word and starts afresh. */
    fun restartInput() {
        composing = ""
        imm?.restartInput(this)
    }

    /** A hardware keyboard's key (the on-screen keyboard's arrive through the connection). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = handleKey(event) || super.onKeyDown(keyCode, event)

    private fun commitComposing() {
        val text = composing
        composing = ""
        listener?.compose("")
        if (text.isNotEmpty()) listener?.commit(text)
    }

    /** One key pressed: a shortcut, an editing key, or a character to type. */
    private fun handleKey(event: KeyEvent): Boolean {
        val l = listener ?: return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            val name = keyName(event.keyCode) ?: return false
            val combo = buildString {
                if (event.isCtrlPressed || event.isMetaPressed) append("Mod+")
                if (event.isAltPressed) append("Alt+")
                if (event.isShiftPressed) append("Shift+")
                append(name)
            }
            commitComposing()
            return l.shortcut(combo)
        }
        val extend = if (event.isShiftPressed) """{"extend":true}""" else ""
        fun move(id: String) {
            commitComposing()
            l.command(id, extend)
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (composing.isNotEmpty()) {
                    composing = composing.dropLast(1)
                    l.compose(composing)
                } else {
                    l.deleteAround(1, 0)
                }
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> l.deleteAround(0, 1)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                commitComposing()
                if (event.isShiftPressed) l.command("text.lineBreak") else l.commit("\n")
            }
            KeyEvent.KEYCODE_TAB -> {
                commitComposing()
                l.command(if (event.isShiftPressed) "text.backTab" else "text.tab")
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> move("caret.left")
            KeyEvent.KEYCODE_DPAD_RIGHT -> move("caret.right")
            KeyEvent.KEYCODE_DPAD_UP -> move("caret.up")
            KeyEvent.KEYCODE_DPAD_DOWN -> move("caret.down")
            KeyEvent.KEYCODE_MOVE_HOME -> move("caret.home")
            KeyEvent.KEYCODE_MOVE_END -> move("caret.end")
            KeyEvent.KEYCODE_PAGE_UP -> move("caret.pageUp")
            KeyEvent.KEYCODE_PAGE_DOWN -> move("caret.pageDown")
            else -> {
                val ch = event.unicodeChar
                if (ch == 0 || Character.isISOControl(ch)) return false
                commitComposing()
                l.commit(String(Character.toChars(ch)))
            }
        }
        return true
    }

    /** The desktop key map's name for a key (`B`, `Z`, `Enter`, `Left`…), or null. */
    private fun keyName(code: Int): String? = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (code - KeyEvent.KEYCODE_0)).toString()
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_MOVE_HOME -> "Home"
        KeyEvent.KEYCODE_MOVE_END -> "End"
        KeyEvent.KEYCODE_PAGE_UP -> "PageUp"
        KeyEvent.KEYCODE_PAGE_DOWN -> "PageDown"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_DEL -> "Backspace"
        KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS -> "="
        KeyEvent.KEYCODE_MINUS -> "-"
        KeyEvent.KEYCODE_PERIOD -> "."
        KeyEvent.KEYCODE_COMMA -> ","
        KeyEvent.KEYCODE_LEFT_BRACKET -> "["
        KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> "F${code - KeyEvent.KEYCODE_F1 + 1}"
        else -> null
    }

    private class EditorConnection(private val view: EditorInputView) : BaseInputConnection(view, false) {
        private val listener get() = view.listener

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            view.composing = ""
            listener?.compose("")
            listener?.commit(text?.toString().orEmpty())
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            view.composing = text?.toString().orEmpty()
            listener?.compose(view.composing)
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean = false

        override fun finishComposingText(): Boolean {
            view.commitComposing()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            listener?.deleteAround(beforeLength, afterLength)
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            listener?.deleteAround(beforeLength, afterLength)
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) view.handleKey(event)
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            val before = listener?.textAround(n, 0)?.before.orEmpty() + view.composing
            return before.takeLast(n.coerceAtLeast(0))
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence =
            listener?.textAround(0, n)?.after.orEmpty().take(n.coerceAtLeast(0))

        override fun getSelectedText(flags: Int): CharSequence? =
            listener?.textAround(0, 0)?.selected?.takeIf { it.isNotEmpty() }

        override fun getCursorCapsMode(reqModes: Int): Int {
            val before = listener?.textAround(100, 0)?.before.orEmpty() + view.composing
            return TextUtils.getCapsMode(before, before.length, reqModes)
        }

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null

        override fun performEditorAction(actionCode: Int): Boolean {
            view.commitComposing()
            listener?.commit("\n")
            return true
        }

        override fun closeConnection() {
            view.commitComposing()
            super.closeConnection()
        }
    }
}
