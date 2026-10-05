package ru.feskolech.libriatv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Focus memory for a horizontal row of cards. Moving Up/Down into the row lands on its first card the
 * first time and on the last selected card afterwards, instead of the card that happens to sit
 * under the previous row's selection. If that card is scrolled out (not composed), the default
 * geometric search takes over, so the focus never gets stuck.
 */
class RowFocusMemory {
    internal val requesters = mutableMapOf<Int, FocusRequester>()
    internal var last = 0
}

@Composable
fun rememberRowFocusMemory(): RowFocusMemory = remember { RowFocusMemory() }

/** On the row container (LazyRow). */
fun Modifier.rowFocusMemory(memory: RowFocusMemory): Modifier =
    focusProperties { onEnter = { memory.requesters[memory.last]?.requestFocus() } }.focusGroup()

/** On each card of the row. */
@Composable
fun Modifier.rowFocusItem(memory: RowFocusMemory, index: Int): Modifier {
    val requester = remember { FocusRequester() }
    DisposableEffect(memory, index) {
        memory.requesters[index] = requester
        onDispose { if (memory.requesters[index] === requester) memory.requesters.remove(index) }
    }
    return focusRequester(requester).onFocusChanged { if (it.isFocused) memory.last = index }
}
