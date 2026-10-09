package ru.feskolech.libriatv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.SettingsStore
import ru.feskolech.libriatv.remote.PhoneRemote
import ru.feskolech.libriatv.ui.components.DrawerBrowsing
import ru.feskolech.libriatv.ui.components.UiSounds
import ru.feskolech.libriatv.ui.navigation.AppNavigation
import ru.feskolech.libriatv.ui.theme.LibriaTvTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var phoneRemote: PhoneRemote
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var userMessages: ru.feskolech.libriatv.ui.components.UserMessages
    private val deepLink = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
    private val voiceQuery = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { voiceQuery.tryEmit(it) }
        }
    }
    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_voice_prompt))
        if (intent.resolveActivity(packageManager) != null) voiceLauncher.launch(intent)
        else voiceQuery.tryEmit("")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLink.value = intent?.data
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                userMessages.messages.collect { text ->
                    android.widget.Toast.makeText(this@MainActivity, text, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        setContent {
            LibriaTvTheme(settingsStore) {
                // While the drawer collapses, its slot is briefly wider than its content; without a themed
                // background under everything that gap showed the window's grey (#303030) as a light strip.
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppNavigation(phoneRemote, deepLink, voiceQuery, onExit = { finishAndRemoveTask() })
                }
            }
        }
    }
    /**
     * Compose does not play the system navigation clicks that View-based TV apps have; add them for
     * D-pad moves and OK. playSoundEffect honours the system "touch sounds" setting.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Back is not counted: it usually closes a page, and the focus passing through the menu meanwhile
        // must not open it (that also stopped Home from restoring the card the page was opened from).
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            DrawerBrowsing.lastMenuKeyAt = android.os.SystemClock.uptimeMillis()
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_SEARCH || event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST)) {
            startVoiceSearch()
            return true
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN && UiSounds.enabled) {
            val effect = when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> android.view.SoundEffectConstants.NAVIGATION_UP
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> android.view.SoundEffectConstants.NAVIGATION_DOWN
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> android.view.SoundEffectConstants.NAVIGATION_LEFT
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> android.view.SoundEffectConstants.NAVIGATION_RIGHT
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> android.view.SoundEffectConstants.CLICK
                else -> null
            }
            if (effect != null && event.repeatCount == 0) window.decorView.playSoundEffect(effect)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.data
    }
    override fun onResume() { super.onResume(); lifecycleScope.launch { phoneRemote.foreground(true) } }
    override fun onPause() { lifecycleScope.launch { phoneRemote.foreground(false) }; super.onPause() }
}
