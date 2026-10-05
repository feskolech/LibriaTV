package ru.feskolech.libriatv.ui.components
import androidx.tv.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.titleLetter

private val letters = ('А'..'Е').map(Char::toString) + "Ё" + ('Ж'..'Я').map(Char::toString) +
    ('A'..'Z').map(Char::toString) + "#"

@Composable
fun AlphabetGrid(releases: List<Release>, badge: @Composable (Release) -> String? = { null },
    favoriteIds: Set<Int> = emptySet(), onFocus: (Int) -> Unit = {}, onOpen: (Int) -> Unit) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val alphabetFocus = remember { FocusRequester() }
    val targetFocus = remember { FocusRequester() }
    var target by remember { mutableIntStateOf(-1) }
    Row(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f)) {
        val columns = ((maxWidth - 62.dp) / 164.dp).toInt().coerceAtLeast(1)
        LazyVerticalGrid(columns = GridCells.Fixed(columns), state = state, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 48.dp, end = 14.dp, top = 8.dp, bottom = 27.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            itemsIndexed(releases, key = { _, r -> r.id }) { index, release ->
                PosterCard(release, badge(release), isFavorite = release.id in favoriteIds,
                    modifier = Modifier.then(if (index == target) Modifier.focusRequester(targetFocus) else Modifier)
                        .then(if (index % columns == columns - 1 || index == releases.lastIndex)
                            Modifier.focusProperties { right = alphabetFocus } else Modifier),
                    onFocus = { onFocus(index) }, onClick = { onOpen(release.id) })
            }
        }
        }
        LazyColumn(Modifier.width(54.dp).fillMaxHeight().padding(end = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(letters.size) { letterIndex ->
                val letter = letters[letterIndex]
                var focused by remember { mutableStateOf(false) }
                Box(Modifier.width(42.dp).border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .background(if (focused) MaterialTheme.colorScheme.primary else Color(0xFF252525))
                    .then(if (letterIndex == 0) Modifier.focusRequester(alphabetFocus) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable {
                        val index = releases.indexOfFirst { it.titleLetter() == letter }
                        if (index >= 0) scope.launch {
                            target = index
                            state.scrollToItem(index)
                            delay(80)
                            runCatching { targetFocus.requestFocus() }
                        }
                    }.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                    Text(letter, color = Color.White)
                }
            }
        }
    }
}
