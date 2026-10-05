package ru.feskolech.libriatv.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ButtonShape
import androidx.tv.material3.MaterialTheme

/**
 * Pills do not grow on focus: a scaled focused pill looks like a bigger button than its
 * neighbours. The accent fill alone marks focus.
 */
val PillButtonScale: ButtonScale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.97f)

/** Common TV button whose focus follows the selected accent. */
@Composable
fun AccentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scale: ButtonScale = PillButtonScale,
    shape: ButtonShape = ButtonDefaults.shape(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, scale = scale, shape = shape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary), content = content)
}
