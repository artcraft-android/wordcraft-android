package io.github.artcraftandroid.wordcraft.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.artcraftandroid.wordcraft.core.engineVersion
import io.github.artcraftandroid.wordcraft.ui.editor.EditorScreen
import io.github.artcraftandroid.wordcraft.ui.editor.SettingsScreen
import io.github.artcraftandroid.wordcraft.ui.editor.TextEncodingScreen

/**
 * The app's screens, and its one-time messages. Going on to a screen slides it in from the side
 * over the whole width; going back slides the other way.
 */
@Composable
fun AppRoot(vm: AppViewModel, onOpen: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.message = null
        }
    }
    Box {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                slideInHorizontally(tween(Motion.BASIC, easing = Motion.EaseOutSharp)) { it * dir } togetherWith
                    slideOutHorizontally(tween(Motion.BASIC, easing = Motion.EaseOutSharp)) { -it * dir }
            },
            label = "screens",
        ) { screen ->
            when (screen) {
                Screen.Start -> StartScreen(vm, onOpen)
                Screen.Editor -> EditorScreen(vm)
                Screen.Settings -> SettingsScreen(vm, runCatching { engineVersion() }.getOrDefault(""))
            }
        }
        // a text file's encoding is chosen over whatever screen is showing
        if (vm.encodingChoice != null) TextEncodingScreen(vm)
    }
}
