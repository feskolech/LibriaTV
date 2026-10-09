package ru.feskolech.libriatv.ui.release

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.data.repo.PlaybackProgress
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.ui.components.AccentButton

/** Episodes per range chip and tiles per grid row. */
internal const val EPISODE_RANGE = 50
internal const val GRID_COLUMNS = 10

/** A run of episodes shown together in the grid, labelled by their real numbers. */
internal data class EpisodeRange(val first: Int, val last: Int, val episodes: List<Episode>) {
    val label: String get() = if (first == last) "$first" else "$first–$last"
}

/**
 * Splits [ordered] episodes into ranges of [EPISODE_RANGE] by episode number, not by position: a release the
 * API serves from episode 370 gets "370–400, 401–450…", aligned to fifties like the rest. Up to
 * [EPISODE_RANGE] episodes stay one range (the picker then shows no chips). Episodes without a number count
 * as 0 and go to the first range.
 */
internal fun episodeRanges(ordered: List<Episode>): List<EpisodeRange> {
    if (ordered.isEmpty()) return emptyList()
    fun number(e: Episode) = (e.ordinal ?: 0.0).toInt()
    if (ordered.size <= EPISODE_RANGE) {
        return listOf(EpisodeRange(number(ordered.first()), number(ordered.last()), ordered))
    }
    return ordered.groupBy { (number(it) - 1).coerceAtLeast(0) / EPISODE_RANGE }.values.map { group ->
        EpisodeRange(number(group.first()), number(group.last()), group)
    }
}

/**
 * The release's episodes as range chips over a grid of numbers, with the focused episode's frame and
 * progress beside it. Built for long series: any episode is a few presses away, and whatever follows
 * the grid (similar titles) is one Down from its last row.
 *
 * Focus: Down from a chip lands on the episode to continue (or the range's first); Up from the grid's
 * top row returns to the selected chip, never to the chip that happens to sit above.
 */
@Composable
internal fun EpisodePicker(
    ordered: List<Episode>,
    progress: Map<String, PlaybackProgress>,
    resumeId: String?,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranges = remember(ordered) { episodeRanges(ordered) }
    if (ranges.isEmpty()) return
    val resumeRange = ranges.indexOfFirst { range -> range.episodes.any { it.id == resumeId } }.coerceAtLeast(0)
    var selected by rememberSaveable(ordered.firstOrNull()?.id) { mutableIntStateOf(resumeRange) }
    val range = ranges[selected.coerceIn(ranges.indices)]
    val target = range.episodes.firstOrNull { it.id == resumeId } ?: range.episodes.first()
    var focused by remember(range) { mutableStateOf<Episode?>(null) }
    val selectedChip = remember { FocusRequester() }
    val targetTile = remember { FocusRequester() }

    Column(modifier
        // Coming down from the page's buttons: land on the selected range or the episode to continue, not on
        // whichever tile happens to sit under the button.
        .focusProperties {
            onEnter = {
                if (requestedFocusDirection == FocusDirection.Down)
                    runCatching { (if (ranges.size > 1) selectedChip else targetTile).requestFocus() }
            }
        }
        .focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (ranges.size > 1) {
            // A plain scrolling Row, not a lazy one: every chip stays composed, so the selected one can
            // always take the focus back (requesting an off-screen lazy item throws).
            Row(Modifier.horizontalScroll(rememberScrollState()).focusRestorer(selectedChip).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ranges.forEachIndexed { index, item ->
                    RangeChip(item, isSelected = index == selected, progress = progress,
                        // Like the schedule tabs: moving along the chips switches the grid right away.
                        onFocus = { selected = index },
                        modifier = Modifier
                            .then(if (index == selected) Modifier.focusRequester(selectedChip) else Modifier)
                            .focusProperties { down = targetTile })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                range.episodes.chunked(GRID_COLUMNS).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { episode ->
                            EpisodeTile(episode, progress[episode.id], isResume = episode.id == resumeId,
                                onFocus = { focused = episode }, onClick = { onPlay(episode.id) },
                                modifier = Modifier.weight(1f)
                                    .then(if (episode.id == target.id) Modifier.focusRequester(targetTile) else Modifier)
                                    .then(if (rowIndex == 0 && ranges.size > 1) Modifier.focusProperties { up = selectedChip } else Modifier))
                        }
                        // Keep a short last row's tiles the same size as the full rows above.
                        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            EpisodePreview(focused ?: target, progress[(focused ?: target).id], Modifier.width(260.dp))
        }
    }
}

@Composable
private fun RangeChip(range: EpisodeRange, isSelected: Boolean, progress: Map<String, PlaybackProgress>,
    onFocus: () -> Unit, modifier: Modifier) {
    val watched = range.episodes.all { progress[it.id]?.watched == true }
    val started = !watched && range.episodes.any { progress[it.id] != null }
    AccentButton(onClick = {}, modifier = modifier
        .onFocusChanged { if (it.isFocused) onFocus() }
        .then(if (isSelected) Modifier.border(2.dp, Color(0xFFB4B4BC), RoundedCornerShape(50)) else Modifier)) {
        Text(range.label, fontSize = 18.sp)
        // Green dot: the whole range is watched; grey: started.
        if (watched || started) Box(Modifier.padding(start = 8.dp).width(8.dp).height(8.dp)
            .background(if (watched) WatchedText else Color(0xFFB4B4BC), RoundedCornerShape(50)))
    }
}

@Composable
private fun EpisodeTile(episode: Episode, progress: PlaybackProgress?, isResume: Boolean,
    onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier) {
    val watched = progress?.watched == true
    Surface(onClick = onClick,
        modifier = modifier.height(44.dp).onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (watched) WatchedTile else Color(0xFF34343B),
            contentColor = if (watched) WatchedText else Color(0xFFE4E4E8),
            focusedContainerColor = Color.White, focusedContentColor = Color(0xFF111111)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("${(episode.ordinal ?: 0.0).toInt()}", fontSize = 18.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center)
            // Started but not finished: a red bar along the bottom.
            if (isResume || (progress != null && !watched)) Box(Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 4.dp).fillMaxWidth(0.7f).height(3.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun EpisodePreview(episode: Episode, progress: PlaybackProgress?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF18181B))) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF262B35))) {
            // Many long series have no frames in the API: the number stands in for the picture.
            if (episode.previewUrl == null) Text("${(episode.ordinal ?: 0.0).toInt()}", Modifier.align(Alignment.Center),
                fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7D7D86))
            else AsyncImage(episode.previewUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            val fraction = when {
                progress == null -> 0f
                progress.watched -> 1f
                progress.durationMs > 0 -> (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
                else -> 0f
            }
            if (fraction > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction).height(4.dp)
                .background(MaterialTheme.colorScheme.primary))
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.episode_number, (episode.ordinal ?: 0.0).toInt()), fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, color = Color.White)
            if (episode.name.isNotBlank()) Text(episode.name, fontSize = 16.sp, color = Color(0xFFB4B4BC),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (progress != null) Text(
                if (progress.watched) stringResource(R.string.watched)
                else stringResource(R.string.progress_minutes, progress.positionMs / 60000),
                fontSize = 16.sp, color = Color(0xFF7D7D86))
        }
    }
}

private val WatchedTile = Color(0xFF233A26)
private val WatchedText = Color(0xFFA5D6A7)
