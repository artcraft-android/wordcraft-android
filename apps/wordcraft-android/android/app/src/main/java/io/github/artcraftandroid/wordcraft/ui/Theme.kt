package io.github.artcraftandroid.wordcraft.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The app's colours, light and dark (the system's theme picks). Greys and layout colours are
 * measured from the reference app's screens; the accent is WordCraft's own blue (its app icon's)
 * and a lighter tint of it for dark, not the reference app's brand colours.
 */
object WordColors {
    /** The dark colours are in use ([WordcraftTheme] follows the system). */
    var dark by mutableStateOf(false)
        internal set

    private fun pick(light: Long, dark: Long) = Color(if (this.dark) dark else light)

    /** Tab and panel titles, their icons, the selection. */
    val Accent get() = pick(0xFF3B5BDB, 0xFF7C93F0)

    /** Small coloured parts of icons (list bullets, the strip's more arrow). */
    val AccentLight get() = pick(0xFF4C6EF5, 0xFF7C93F0)
    val AccentDisabled get() = pick(0xFF99A7E1, 0xFF4A5583)

    /** The tab list: its rows, the current tab's row, and their labels. */
    val PopupFill get() = pick(0xFF3B5BDB, 0xFF212121)
    val PopupCurrent get() = pick(0xFFF3F3F3, 0xFF000000)
    val OnPopup = Color(0xFFFFFFFF)
    val OnPopupCurrent get() = pick(0xFF2B44A8, 0xFF5B84EC)

    /** The palette header's Tell Me, Undo and Redo. */
    val HeaderIcon get() = pick(0xFF3B5BDB, 0xFFCACACA)

    /**
     * The palette header's tab-switch arrows, collapse arrow and Back: the accent in light; in
     * dark white, light grey and grey, as the phone app draws them.
     */
    val SwitchIcon get() = pick(0xFF3B5BDB, 0xFFFFFFFF)
    val CollapseIcon get() = pick(0xFF2B44A8, 0xFFE8E8E8)
    val BackIcon get() = pick(0xFF3B5BDB, 0xFF6E6E6E)
    val HeaderIconDisabled get() = pick(0xFF99A7E1, 0xFF575757)

    /** Bottom sheets (the header's overflow). */
    val Sheet get() = pick(0xFFFFFFFF, 0xFF1F1F1F)

    /** The floating toolbar's icons, and the circle behind a button that is on. */
    val OnPill get() = pick(0xFF242424, 0xFFFFFFFF)
    val PillOn get() = pick(0xFFDADADA, 0xFF575757)

    /** Bars at the top, the floating toolbar and pill. */
    val Bar get() = pick(0xFFFFFFFF, 0xFF292929)
    val BarEdge get() = pick(0xFFE4E4E4, 0xFF303030)

    /** Behind the status bar. */
    val StatusBand get() = pick(0xFFFFFFFF, 0xFF212121)

    /** Text and icons on the bars and in the palette. */
    val OnBar get() = pick(0xFF505050, 0xFFE1E1E1)
    val Disabled get() = pick(0xFFB9B9B9, 0xFF666666)

    /** The palette's header. */
    val Strip get() = pick(0xFFE6E6E6, 0xFF212121)
    val OnStrip get() = pick(0xFF3A3A38, 0xFFD4D4D4)
    val StripMore get() = pick(0xFFD2D2D2, 0xFF303030)
    val HeaderDivider get() = pick(0xFFC5C5C5, 0xFF303030)

    /** The palette's lists. */
    val Surface get() = pick(0xFFFFFFFF, 0xFF141414)
    val OnSurface get() = pick(0xFF505050, 0xFFE1E1E1)
    val Divider get() = pick(0xFFF3F3F3, 0xFF292929)
    val Section get() = pick(0xFFE6E6E6, 0xFF404040)
    val Pressed get() = pick(0xFFD2D2D2, 0xFF303030)

    /** Around and between the pages, and the pages themselves (dark pages are near-black). */
    val Canvas get() = pick(0xFFF7F7F7, 0xFF141414)
    val Page get() = pick(0xFFFFFFFF, 0xFF141414)
    val DarkPage = Color(0xFF141414)

    /** Settings: the page behind them, section titles' rules. */
    val SettingsBack get() = pick(0xFFFFFFFF, 0xFF000000)
    val OnSettings get() = pick(0xFF242424, 0xFFE1E1E1)
    val SettingsValue get() = pick(0xFF616161, 0xFFACACAC)
    val PageBorder get() = pick(0xFFC6C6C6, 0xFF303030)

    /** Find: its bar, the field in it, the hint, and the matches (the selected one darker). */
    val FindBar get() = pick(0xFFE6E6E6, 0xFF303030)
    val FindField get() = pick(0xFFFFFFFF, 0xFF212121)
    val FindHint get() = pick(0xFF8A8A8A, 0xFFACACAC)
    // dark: screened over the page to the reference's olives (measured, converted from P3)
    val MatchCurrent get() = pick(0xFFC7BA64, 0xFF7C6E26)

    /**
     * Mobile View's message card over the pages, and its text (the dark one measured on the
     * reference; screenshots there read in Display P3, so the measured values are converted to sRGB).
     */
    val MessageCard get() = pick(0xFFDCE5F7, 0xFF19428A)
    val OnMessageCard get() = pick(0xFF1F3B78, 0xFF81ABF1)
    val MatchOther get() = pick(0xFFFFEE80, 0xFF5A4500)

    /** Under the selected match's text, to its line's foot: the selection's grey (screened on a dark page to #474747). */
    val MatchSelected get() = pick(0xFFC6C6C6, 0xFF373737)

    /** Find settings: an unticked box, its edge, and the line under Replace. */
    val CheckOff get() = pick(0xFFFFFFFF, 0xFF6E6E6E)
    val CheckEdge get() = pick(0xFF8A8A8A, 0xFF666666)
    val SettingsLine get() = pick(0xFFC6C6C6, 0xFF969696)

    /** Word Count's and Language's text (and Word Count's ticks), its boxes' edges, the rule under the counts. */
    val OnLists get() = pick(0xFF505050, 0xFFD2D2D2)
    val CountsBoxEdge get() = pick(0xFFD2D2D2, 0xFF666666)
    val CountsRule = Color(0xFFBDBDBD)

    /** Language's chosen language: grey in light, the reference's blue in dark (measured, converted from P3). */
    val ListChosen get() = pick(0xFFD2D2D2, 0xFF3D7CE8)

    /** Over the pages behind a bottom sheet. */
    val Scrim = Color(0x52000000)

    val Selection get() = pick(0x553B5BDB, 0x667C93F0)
    val SwitcherDivider get() = pick(0xFFCDCDCD, 0xFF303030)
    val Caret get() = pick(0xFF000000, 0xFFFFFFFF)
    val FontColorDefault = Color(0xFFFF0000)
}

/** The app's theme: [choice] is `system` (the phone's), `light` or `dark`. */
@Composable
fun WordcraftTheme(choice: String = "system", content: @Composable () -> Unit) {
    val dark = when (choice) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    // set before anything below reads a colour
    if (WordColors.dark != dark) WordColors.dark = dark
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(primary = WordColors.Accent, surface = WordColors.Surface, onSurface = WordColors.OnSurface)
        } else {
            lightColorScheme(primary = WordColors.Accent, surface = WordColors.Surface, onSurface = WordColors.OnSurface)
        },
    ) {
        // pressed controls turn grey, as in the reference app, rather than rippling; text is set
        // without the extra letter spacing Material adds
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides PressedIndication,
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            content = content,
        )
    }
}
