package ru.feskolech.libriatv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.data.repo.PlaybackProgress
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.ui.components.AccentButton
import ru.feskolech.libriatv.ui.release.episodeRanges

/**
 * The player's episodes as a strip of cards (frame, number, title, progress), reached with Down from the
 * playback controls and left with Up. A long series gets range chips above the strip ("1–50", "51–100"…),
 * like the release page, so any episode is a few presses away. Opens on the playing episode, which takes
 * [focus].
 */
@Composable
internal fun PlayerEpisodeStrip(content: PlayerContent, focus: FocusRequester, onPlay: (String) -> Unit, onUp: () -> Unit) {
    val ordered = remember(content.release) { content.release.orderedEpisodes() }
    val ranges = remember(ordered) { episodeRanges(ordered) }
    if (ranges.isEmpty()) return
    val playingRange = ranges.indexOfFirst { range -> range.episodes.any { it.id == content.episode.id } }.coerceAtLeast(0)
    var selected by remember(content.release.id) { mutableIntStateOf(playingRange) }
    val range = ranges[selected.coerceIn(ranges.indices)]
    val target = range.episodes.firstOrNull { it.id == content.episode.id } ?: range.episodes.first()
    val targetIndex = range.episodes.indexOf(target)
    val selectedChip = remember { FocusRequester() }
    // Start scrolled to the playing episode so its card is composed and can take the focus.
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (targetIndex - 1).coerceAtLeast(0))
    LaunchedEffect(range) { rowState.scrollToItem((targetIndex - 1).coerceAtLeast(0)) }
    // Up leaves the strip: from the chips, or from the cards when there are no chips.
    fun upLeaves(event: KeyEvent) = event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color(0xFFB4B4BC), modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.player_back_to_controls), color = Color(0xFFB4B4BC), fontSize = 16.sp)
        }
        Text(stringResource(R.string.episodes), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (ranges.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()).focusRestorer(selectedChip).focusGroup()
                .onPreviewKeyEvent { if (upLeaves(it)) { onUp(); true } else false },
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ranges.forEachIndexed { index, item ->
                    AccentButton(onClick = {}, modifier = Modifier
                        .then(if (index == selected) Modifier.focusRequester(selectedChip) else Modifier)
                        .focusProperties { down = focus }
                        .onFocusChanged { if (it.isFocused) selected = index }
                        .then(if (index == selected) Modifier.border(2.dp, Color(0xFFB4B4BC), RoundedCornerShape(50)) else Modifier)) {
                        Text(item.label, fontSize = 16.sp)
                    }
                }
            }
        }
        // A little room at the sides: the focused card grows and must not be clipped by the row's edge.
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.onPreviewKeyEvent { if (ranges.size == 1 && upLeaves(it)) { onUp(); true } else false }) {
            items(range.episodes, key = { it.id }) { episode ->
                val playing = episode.id == content.episode.id
                // The playing episode's progress is the live one, not the last saved.
                val progress = if (playing && content.durationMs > 0) PlaybackProgress(content.positionMs, content.durationMs)
                    else content.episodeProgress[episode.id]
                EpisodeCard(episode, progress, playing, onClick = { onPlay(episode.id) },
                    modifier = Modifier
                        .then(if (episode.id == target.id) Modifier.focusRequester(focus) else Modifier)
                        .then(if (ranges.size > 1) Modifier.focusProperties { up = selectedChip } else Modifier))
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, progress: PlaybackProgress?, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val number = (episode.ordinal ?: 0.0).toInt()
    val watched = progress?.watched == true && !playing
    Surface(onClick = onClick, modifier = modifier.width(CARD_WIDTH),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White))),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1E1E22), focusedContainerColor = Color(0xFF2A2A30),
            contentColor = Color.White, focusedContentColor = Color.White)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF262B35))) {
                // Many long series have no frames in the API: the number stands in for the picture.
                if (episode.previewUrl == null) Text("$number", Modifier.align(Alignment.Center),
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7D7D86))
                else AsyncImage(episode.previewUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    alpha = if (watched) 0.55f else 1f)
                if (playing) Text(stringResource(R.string.now_playing), Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                val fraction = when {
                    progress == null -> 0f
                    progress.watched -> 1f
                    progress.durationMs > 0 -> (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (fraction > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                    Box(Modifier.fillMaxWidth(fraction).height(4.dp)
                        .background(if (watched) Color(0xFFA5D6A7) else MaterialTheme.colorScheme.primary))
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.episode_number, number) + if (watched) " · " + stringResource(R.string.watched) else "",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (watched) Color(0xFFA5D6A7) else Color.White)
                // Always one line high, so cards with and without a title line up.
                Text(episode.name, fontSize = 14.sp, color = Color(0xFFB4B4BC), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val CARD_WIDTH = 196.dp
