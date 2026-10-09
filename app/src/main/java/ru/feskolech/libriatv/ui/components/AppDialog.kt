package ru.feskolech.libriatv.ui.components

import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * The app's one dialog look: a rounded card of fixed width, a title, free content and a row of
 * equal-width actions at the bottom. Exit, update, sign-out and "new in favorites" all use it.
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    title: String? = null,
    width: Dp = 520.dp,
    /** Share of the screen width instead of [width], for dialogs that show long text. */
    widthFraction: Float? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    // Without usePlatformDefaultWidth = false the system caps every dialog at its own narrow width
    // (about 440 dp), whatever width is asked for.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(if (widthFraction != null) Modifier.fillMaxWidth(widthFraction) else Modifier.width(width),
            shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
                if (actions != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        content = actions)
                }
            }
        }
    }
}

/** One of the dialog's actions; all of them share the row equally. [initialFocus] focuses it when shown. */
@Composable
fun RowScope.DialogButton(text: String, onClick: () -> Unit, initialFocus: Boolean = false, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    AccentButton(onClick = onClick, modifier = Modifier.weight(1f).focusRequester(focus).then(modifier)) {
        Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1)
    }
    if (initialFocus) LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

/**
 * A lone action (e.g. "Close"): centred at half the row so it does not stretch into a bar.
 * [initialFocus] is off when a list above it should take the focus first.
 */
@Composable
fun RowScope.SingleDialogButton(text: String, onClick: () -> Unit, initialFocus: Boolean = true, modifier: Modifier = Modifier) {
    Spacer(Modifier.weight(0.5f))
    DialogButton(text, onClick, initialFocus, modifier)
    Spacer(Modifier.weight(0.5f))
}
