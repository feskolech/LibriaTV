package ru.feskolech.libriatv.ui.home

import ru.feskolech.libriatv.ui.components.rowFocusItem
import ru.feskolech.libriatv.ui.components.rowFocusMemory
import ru.feskolech.libriatv.ui.components.rememberRowFocusMemory
import ru.feskolech.libriatv.ui.components.DrawerBrowsing
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Surface
import androidx.tv.material3.MaterialTheme
import ru.feskolech.libriatv.data.repo.NewFavoriteEpisode
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.ScheduleItem
import ru.feskolech.libriatv.ui.components.PosterCard
import ru.feskolech.libriatv.data.repo.ContinueItem

@Composable
fun HomeScreen(
    active: Boolean,
    onContentFocus: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenRelease: (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Back on Home: refresh within the 5-minute throttle only, but always update "Continue watching".
    LaunchedEffect(Unit) { viewModel.refresh(); viewModel.refreshContinue() }
    when (val content = state) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(48.dp)) {
            Text(stringResource(R.string.home_loading))
        }
        is HomeUiState.Error -> Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.home_error))
            Text(content.message)
            Button(onClick = { viewModel.refresh(force = true) }) { Text(stringResource(R.string.retry)) }
        }
        is HomeUiState.Content -> {
            HomeContent(content, active, onContentFocus, onOpenFeed, onOpenRelease, viewModel::dismissEpisodeDialog, viewModel::loadMoreLatest)
        }
    }
}

@Composable
private fun HomeContent(
    content: HomeUiState.Content,
    active: Boolean,
    onContentFocus: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenRelease: (Int) -> Unit,
    dismissEpisodeDialog: () -> Unit,
    onLoadMoreLatest: () -> Unit,
) {
    // Not keyed on content: appending pages to the endless row must not reset focus or the backdrop.
    var focusedRelease by remember { mutableStateOf(content.latest.firstOrNull()) }
    var backgroundRelease by remember { mutableStateOf(focusedRelease) }
    val firstPoster = remember { FocusRequester() }
    // Card that had focus last (row + release). Saved across navigation so Back from a release card
    // and leaving the drawer land on it again instead of the first card / another row.
    var lastRow by rememberSaveable { mutableIntStateOf(-1) }
    var lastId by rememberSaveable { mutableIntStateOf(-1) }
    val restore = remember { FocusRequester() }
    fun restoreFor(row: Int, id: Int): Modifier = if (row == lastRow && id == lastId) Modifier.focusRequester(restore) else Modifier
    val rowsState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Scroll only when focus moves to another row. Re-snapping on every horizontal move fought the
    // focus system's own bring-into-view and made the rows bounce up and down (seen on Ugoos SK4).
    var focusedRow by remember { mutableIntStateOf(-1) }
    fun focusRow(index: Int, release: Release) {
        focusedRelease = release
        lastRow = index
        lastId = release.id
        if (index == focusedRow) return
        focusedRow = index
        scope.launch { rowsState.animateScrollToItem(index) }
    }
    LaunchedEffect(Unit) {
        // Lazy rows compose their items a frame later; requesting earlier leaves focus in the drawer.
        withFrameNanos { }
        if (DrawerBrowsing.active) return@LaunchedEffect
        if (lastId < 0 || runCatching { restore.requestFocus() }.isFailure) runCatching { firstPoster.requestFocus() }
    }
    LaunchedEffect(focusedRelease?.id) {
        delay(300)
        backgroundRelease = focusedRelease
    }
    // clipToBounds: the blur render effect otherwise bleeds left under the drawer as a light strip.
    Box(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background).onFocusChanged { if (it.hasFocus) onContentFocus() }
        // Coming back from the drawer: return to the card that had focus, not the nearest one.
        .focusRestorer()) {
        HomeBackdrop(backgroundRelease, content.videoPreviewEnabled && active)
        Column(Modifier.fillMaxSize()) {
            if (content.newEpisodes.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp)
                    .background(Color(0xFF402020), RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Text(stringResource(R.string.favorite_new_episodes), color = Color.White, fontWeight = FontWeight.Bold)
                    content.newEpisodes.forEach { Text(newEpisodeText(it), color = Color.White) }
                }
            }
            LazyColumn(
                state = rowsState,
                modifier = Modifier.weight(1f).graphicsLayer { clip = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp, bottom = 440.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            // Row order agreed with users: new episodes first, then continue watching.
            val c = if (content.continueItems.isNotEmpty()) 1 else 0
            fun sel(row: Int) = if (focusedRow == row) focusedRelease else null
            item {
                PosterRow(
                    title = stringResource(R.string.new_episodes),
                    releases = content.latest,
                    badge = { it.latestEpisode?.ordinal?.let { n -> stringResource(R.string.episode_number, n.toInt()) } },
                    timeBadge = { it.freshAt?.let { value -> freshLabel(value) } },
                    favoriteIds = content.favoriteIds,
                    firstPoster = firstPoster,
                    onFocus = { focusRow(0, it) },
                    cardModifier = { restoreFor(0, it.id) },
                    // Users asked for the release card on OK (with a TV remote a direct start is too easy to trigger).
                    onClick = onOpenRelease,
                    onLongClick = { onOpenRelease(it.id) },
                    onNearEnd = onLoadMoreLatest,
                    selected = sel(0),
                    episodeLabel = { it.latestEpisode?.ordinal?.let { n -> stringResource(R.string.episode_number, n.toInt()) } },
                )
            }
            if (c == 1) item {
                ContinueRow(content.continueItems, { restoreFor(1, it.id) },
                    onFocus = { focusRow(1, it) }, onOpen = onOpenRelease)
            }
            item {
                ScheduleRow(stringResource(R.string.today), content.today, content.favoriteIds,
                    { focusRow(1 + c, it) }, onOpenRelease, { restoreFor(1 + c, it.id) }, sel(1 + c))
            }
            item {
                ScheduleRow(stringResource(R.string.tomorrow), content.tomorrow, content.favoriteIds,
                    { focusRow(2 + c, it) }, onOpenRelease, { restoreFor(2 + c, it.id) }, sel(2 + c))
            }
            if (content.recommended.isNotEmpty()) item {
                PosterRow(stringResource(R.string.recommended), content.recommended,
                    badge = { it.year?.toString() }, favoriteIds = content.favoriteIds,
                    onFocus = { focusRow(3 + c, it) }, onClick = onOpenRelease, cardModifier = { restoreFor(3 + c, it.id) },
                    selected = sel(3 + c))
            }
            }
        }
    }
    if (content.showEpisodeDialog) {
        val closeFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = dismissEpisodeDialog) {
            Surface {
                Column(Modifier.width(560.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.favorite_new_episodes), style = MaterialTheme.typography.headlineSmall)
                    content.newEpisodes.forEach { Text(newEpisodeText(it)) }
                    Button(onClick = dismissEpisodeDialog, modifier = Modifier.focusRequester(closeFocus)) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
    }
}

@Composable
private fun ContinueRow(items: List<ContinueItem>, cardModifier: (Release) -> Modifier,
    onFocus: (Release) -> Unit, onOpen: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.continue_watching), Modifier.padding(start = 48.dp),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        val memory = rememberRowFocusMemory()
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.rowFocusMemory(memory)) {
            itemsIndexed(items, key = { _, it -> it.episode.id }) { index, item ->
                var focused by remember { mutableStateOf(false) }
                Column(Modifier.width(260.dp)
                    .rowFocusItem(memory, index)
                    .then(cardModifier(item.release))
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(item.release) }
                    .graphicsLayer { scaleX = if (focused) 1.04f else 1f; scaleY = scaleX }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF202020))
                    .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onOpen(item.release.id) }) {
                        Box(Modifier.fillMaxWidth().height(146.dp)) {
                            AsyncImage(item.episode.previewUrl ?: item.release.posterUrl, null,
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxWidth().height(5.dp).background(Color.DarkGray)
                                .align(androidx.compose.ui.Alignment.BottomStart))
                            Box(Modifier.fillMaxWidth((item.progress.positionMs.toFloat() /
                                item.progress.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f))
                                .height(5.dp).background(MaterialTheme.colorScheme.primary)
                                .align(androidx.compose.ui.Alignment.BottomStart))
                        }
                        Text(item.release.title, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.episode_number, item.episode.ordinal?.toInt() ?: 1),
                            Modifier.padding(start = 8.dp, bottom = 6.dp), color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
private fun newEpisodeText(item: NewFavoriteEpisode): String = item.release.title + " — " +
    if (item.from == item.to) stringResource(R.string.episode_number, item.to)
    else stringResource(R.string.favorite_episode_range, item.from, item.to)

@Composable
private fun ScheduleRow(
    title: String,
    items: List<ScheduleItem>,
    favoriteIds: Set<Int>,
    onFocus: (Release) -> Unit,
    onClick: (Int) -> Unit,
    cardModifier: (Release) -> Modifier = { Modifier },
    selected: Release? = null,
) {
    PosterRow(title, items.map { it.release }, cardModifier = cardModifier, selected = selected,
        episodeLabel = { release -> items.firstOrNull { it.release.id == release.id }?.nextEpisodeNumber?.let { stringResource(R.string.episode_number, it) } },
        badge = { release -> items.firstOrNull { it.release.id == release.id }?.nextEpisodeNumber?.let { stringResource(R.string.episode_number, it) } },
        favoriteIds = favoriteIds, onFocus = onFocus, onClick = onClick)
}

@Composable
private fun PosterRow(
    title: String,
    releases: List<Release>,
    badge: @Composable (Release) -> String?,
    timeBadge: @Composable (Release) -> String? = { null },
    favoriteIds: Set<Int>,
    onTitleClick: (() -> Unit)? = null,
    firstPoster: FocusRequester? = null,
    onFocus: (Release) -> Unit,
    onClick: (Int) -> Unit,
    onLongClick: (Release) -> Unit = { onClick(it.id) },
    onNearEnd: (() -> Unit)? = null,
    cardModifier: (Release) -> Modifier = { Modifier },
    /** Release focused in this row; its title and details are shown under the row. */
    selected: Release? = null,
    episodeLabel: @Composable (Release) -> String? = { null },
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title + if (onTitleClick != null) "  ›" else "",
            modifier = Modifier.padding(start = 48.dp)
                .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (releases.isEmpty()) {
            Text(stringResource(R.string.home_no_releases), Modifier.padding(start = 48.dp), color = Color.LightGray)
        } else {
            val memory = rememberRowFocusMemory()
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.rowFocusMemory(memory),
            ) {
                itemsIndexed(releases, key = { _, r -> r.id }) { index, release ->
                    PosterCard(release, badge(release), timeBadge(release), release.id in favoriteIds,
                        modifier = (if (firstPoster != null && release.id == releases.first().id) Modifier.focusRequester(firstPoster) else Modifier)
                            .rowFocusItem(memory, index)
                            .then(cardModifier(release)),
                        onFocus = {
                            onFocus(release)
                            if (onNearEnd != null && index >= releases.size - 10) onNearEnd()
                        }, onClick = { onClick(release.id) },
                        onLongClick = { onLongClick(release) }, showTitle = false)
                }
            }
            if (selected != null) SelectedInfo(selected, episodeLabel(selected))
        }
    }
}

/** Under the focused row, like the official app: full title, then year/season/genres/episode/freshness. */
@Composable
private fun SelectedInfo(release: Release, episode: String?) {
    Column(Modifier.padding(start = 48.dp, end = 48.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(release.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val parts = listOfNotNull(
            listOfNotNull(release.year?.toString(), release.season?.lowercase()).joinToString(" ").ifBlank { null },
            release.genres.take(2).joinToString(" • ").ifBlank { null },
            episode,
            release.freshAt?.let { freshLabel(it) },
        )
        Text(parts.joinToString("  •  "), fontSize = 14.sp, color = Color(0xFFCFCFCF), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "<1 h ago" within the first hour, "5 h ago" within a day, then "1 day ago", "12 days ago" up to two months, then the date. */
@Composable
private fun freshLabel(value: String): String? {
    val time = runCatching { OffsetDateTime.parse(value) }.getOrNull() ?: return null
    val hours = ChronoUnit.HOURS.between(time, OffsetDateTime.now()).coerceAtLeast(0)
    return when {
        hours < 1 -> stringResource(R.string.less_than_hour_ago)
        hours < 24 -> stringResource(R.string.hours_ago, hours)
        hours < 24 * 60 -> (hours / 24).toInt().let { days -> androidx.compose.ui.res.pluralStringResource(R.plurals.days_ago, days, days) }
        else -> time.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }
}

