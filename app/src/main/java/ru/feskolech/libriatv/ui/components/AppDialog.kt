package ru.feskolech.libriatv.ui.components

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
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.width(width), shape = RoundedCornerShape(20.dp)) {
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
fun RowScope.DialogButton(text: String, onClick: () -> Unit, initialFocus: Boolean = false) {
    val focus = remember { FocusRequester() }
    AccentButton(onClick = onClick, modifier = Modifier.weight(1f).focusRequester(focus)) {
        Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1)
    }
    if (initialFocus) LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

/** A lone action (e.g. "Close"): centred at half the row so it does not stretch into a bar. */
@Composable
fun RowScope.SingleDialogButton(text: String, onClick: () -> Unit) {
    Spacer(Modifier.weight(0.5f))
    DialogButton(text, onClick, initialFocus = true)
    Spacer(Modifier.weight(0.5f))
}
