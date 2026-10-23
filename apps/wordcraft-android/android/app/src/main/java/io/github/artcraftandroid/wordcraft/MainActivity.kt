package io.github.artcraftandroid.wordcraft

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.github.artcraftandroid.wordcraft.engine.DocumentFiles
import io.github.artcraftandroid.wordcraft.ui.AppRoot
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.WordcraftTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // keep the right to write back to it
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            vm.open(uri, writable = true)
        }
    }

    private val saveAs = registerForActivityResult(ActivityResultContracts.CreateDocument(DocumentFiles.DOCX_TYPE)) { uri ->
        if (uri != null) vm.saveTo(uri)
    }

    /** Insert ▸ Pictures ▸ Photos: the system's photo picker (no permission needed). */
    private var replacing = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val replace = replacing
        replacing = false
        if (uri != null) lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            when {
                bytes == null -> vm.tell("The picture can't be read.")
                replace -> vm.replacePicture(bytes)
                else -> vm.insertPicture(bytes)
            }
        }
    }

    /** Insert ▸ Pictures ▸ Camera and Scan Document: the camera app takes the picture. */
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) lifecycleScope.launch {
            val bytes = withContext(Dispatchers.Default) {
                java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
            }
            vm.insertPicture(bytes)
        }
    }

    /** Dictation: the phone's speech recognizer listens, and what it heard is typed at the caret. */
    private val listen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (heard != null) vm.dictate(heard)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // the bars' icons follow the theme: dark on the light bars, light on the dark ones
        enableEdgeToEdge(SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        setContent {
            WordcraftTheme(vm.themeChoice) {
                // the bars' icons follow the app's theme (which may differ from the phone's)
                val dark = io.github.artcraftandroid.wordcraft.ui.WordColors.dark
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                AppRoot(vm, onOpen = ::pickDocument)
            }
        }
        lifecycleScope.launch {
            for (name in vm.saveAsRequests) launchSafely { saveAs.launch(name) }
        }
        lifecycleScope.launch {
            for ((uri, type) in vm.shareRequests) {
                val send = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                launchSafely { startActivity(Intent.createChooser(send, null)) }
            }
        }
        lifecycleScope.launch {
            for ((name, pdf) in vm.printRequests) {
                launchSafely {
                    val pm = getSystemService(android.print.PrintManager::class.java)
                    pm?.print(name, PdfPrintAdapter(name, pdf), null)
                }
            }
        }
        lifecycleScope.launch {
            for (kind in vm.pictureRequests) {
                launchSafely {
                    if (kind == "camera") {
                        takePhoto.launch(null)
                    } else {
                        replacing = kind == "replace"
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
        }
        lifecycleScope.launch {
            for (url in vm.linkRequests) {
                launchSafely { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            }
        }
        lifecycleScope.launch {
            for (unit in vm.dictationRequests) {
                val ask = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
                launchSafely { listen.launch(ask) }
            }
        }
        if (savedInstanceState == null) handle(intent)
    }

    /** Leaving the app saves the changes to the document's own file (AutoSave). */
    override fun onStop() {
        super.onStop()
        vm.autoSaveNow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun pickDocument() = launchSafely { openDocument.launch(DocumentFiles.OPEN_TYPES) }

    private fun launchSafely(block: () -> Unit) {
        try {
            block()
        } catch (_: ActivityNotFoundException) {
            vm.tell("No app on this phone can do that.")
        } catch (_: SecurityException) {
            vm.tell("WordCraft isn't allowed to do that.")
        }
    }

    /** Documents other apps open (VIEW) or hand over to edit (EDIT) with WordCraft, and home-screen shortcuts to documents. */
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> vm.open(uri, writable = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            Intent.ACTION_EDIT -> vm.open(uri, writable = true)
            io.github.artcraftandroid.wordcraft.ui.ACTION_OPEN_PINNED -> vm.openPinned(uri)
        }
    }
}
