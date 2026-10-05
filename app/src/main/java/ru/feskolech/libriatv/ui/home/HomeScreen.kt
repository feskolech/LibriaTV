package ru.feskolech.libriatv.ui.home

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.ScheduleItem
import ru.feskolech.libriatv.ui.components.PosterCard
import ru.feskolech.libriatv.data.repo.ContinueItem

@Composable
fun HomeScreen(
    onContentFocus: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenRelease: (Int) -> Unit,
    onPlay: (Int, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh(force = true) }
    when (val content = state) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(48.dp)) {
            Text(stringResource(R.string.home_loading))
        }
        is HomeUiState.Error -> Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.home_error))
            Text(content.message)
            Button(onClick = { viewModel.refresh(force = true) }) { Text(stringResource(R.string.retry)) }
        }
        is HomeUiState.Content -> HomeContent(content, onContentFocus, onOpenFeed, onOpenRelease, onPlay)
    }
}

@Composable
private fun HomeContent(
    content: HomeUiState.Content,
    onContentFocus: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenRelease: (Int) -> Unit,
    onPlay: (Int, String) -> Unit,
) {
    var focusedRelease by remember(content) { mutableStateOf(content.continueItems.firstOrNull()?.release ?: content.latest.firstOrNull()) }
    var backgroundRelease by remember(content) { mutableStateOf(focusedRelease) }
    val firstPoster = remember { FocusRequester() }
    val rowsState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun focusRow(index: Int, release: Release) {
        focusedRelease = release
        scope.launch { delay(80); rowsState.scrollToItem(index) }
    }
    LaunchedEffect(content) {
        // Lazy rows compose their items a frame later; requesting earlier leaves focus in the drawer.
        withFrameNanos { }
        runCatching { firstPoster.requestFocus() }
    }
    LaunchedEffect(focusedRelease?.id) {
        delay(300)
        backgroundRelease = focusedRelease
    }
    // clipToBounds: the blur render effect otherwise bleeds left under the drawer as a light strip.
    Box(Modifier.fillMaxSize().clipToBounds().background(Color(0xFF101010)).onFocusChanged { if (it.hasFocus) onContentFocus() }) {
        Crossfade(backgroundRelease?.posterUrl, label = "poster background") { url ->
            if (url != null) {
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        if (Build.VERSION.SDK_INT >= 31) {
                            renderEffect = RenderEffect.createBlurEffect(34f, 34f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                        }
                        alpha = 0.36f
                    },
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Color(0xB5101010)))
        Column(Modifier.fillMaxSize()) {
            SelectedRelease(focusedRelease)
            LazyColumn(
                state = rowsState,
                modifier = Modifier.weight(1f).graphicsLayer { clip = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp, bottom = 440.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            if (content.continueItems.isNotEmpty()) item {
                ContinueRow(content.continueItems, firstPoster,
                    onFocus = { focusRow(0, it) }, onPlay = onPlay)
            }
            item {
                PosterRow(
                    title = stringResource(R.string.new_episodes),
                    releases = content.latest,
                    badge = { it.latestEpisode?.ordinal?.let { n -> stringResource(R.string.episode_number, n.toInt()) } },
                    timeBadge = { it.freshAt?.let { value -> relativeHours(value)?.let { hours -> stringResource(R.string.hours_ago, hours) } } },
                    favoriteIds = content.favoriteIds,
                    onTitleClick = onOpenFeed,
                    firstPoster = if (content.continueItems.isEmpty()) firstPoster else null,
                    onFocus = { focusRow(if (content.continueItems.isEmpty()) 0 else 1, it) }, onClick = onOpenRelease,
                )
            }
            item {
                ScheduleRow(stringResource(R.string.today), content.today, content.favoriteIds,
                    { focusRow(if (content.continueItems.isEmpty()) 1 else 2, it) }, onOpenRelease)
            }
            item {
                ScheduleRow(stringResource(R.string.tomorrow), content.tomorrow, content.favoriteIds,
                    { focusRow(if (content.continueItems.isEmpty()) 2 else 3, it) }, onOpenRelease)
            }
            if (content.isAuthorized) item {
                PosterRow(stringResource(R.string.favorites), content.favorites,
                    badge = { null }, favoriteIds = content.favoriteIds,
                    onFocus = { focusRow(if (content.continueItems.isEmpty()) 3 else 4, it) }, onClick = onOpenRelease)
            }
            if (content.recommended.isNotEmpty()) item {
                PosterRow(stringResource(R.string.recommended), content.recommended,
                    badge = { it.year?.toString() }, favoriteIds = content.favoriteIds,
                    onFocus = { focusRow((if (content.isAuthorized) 4 else 3) + (if (content.continueItems.isEmpty()) 0 else 1), it) }, onClick = onOpenRelease)
            }
            }
        }
    }
}

@Composable
private fun ContinueRow(items: List<ContinueItem>, firstPoster: FocusRequester,
    onFocus: (Release) -> Unit, onPlay: (Int, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.continue_watching), Modifier.padding(start = 48.dp),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            items(items, key = { it.episode.id }) { item ->
                var focused by remember { mutableStateOf(false) }
                Column(Modifier.width(260.dp)
                    .then(if (item == items.first()) Modifier.focusRequester(firstPoster) else Modifier)
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(item.release) }
                    .graphicsLayer { scaleX = if (focused) 1.04f else 1f; scaleY = scaleX }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF202020))
                    .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onPlay(item.release.id, item.episode.id) }) {
                        Box(Modifier.fillMaxWidth().height(146.dp)) {
                            AsyncImage(item.episode.previewUrl ?: item.release.posterUrl, null,
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxWidth().height(5.dp).background(Color.DarkGray)
                                .align(androidx.compose.ui.Alignment.BottomStart))
                            Box(Modifier.fillMaxWidth((item.progress.positionMs.toFloat() /
                                item.progress.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f))
                                .height(5.dp).background(Color(0xFFB32121))
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
private fun SelectedRelease(release: Release?) {
    Column(Modifier.fillMaxWidth().height(155.dp).padding(start = 48.dp, end = 48.dp, bottom = 16.dp), verticalArrangement = Arrangement.Bottom) {
        Text(release?.title ?: stringResource(R.string.home), fontSize = 26.sp,
            fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (release != null) {
            val metadata = listOfNotNull(release.year?.toString(), release.type, release.season, release.publishDay)
            Text((metadata + release.genres.take(3)).joinToString("  •  "), color = Color(0xFFE0D9D9), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(release.description.orEmpty().replace(Regex("<[^>]*>"), "").replace('\n', ' '),
                color = Color(0xFFC7C1C1), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ScheduleRow(
    title: String,
    items: List<ScheduleItem>,
    favoriteIds: Set<Int>,
    onFocus: (Release) -> Unit,
    onClick: (Int) -> Unit,
) {
    PosterRow(title, items.map { it.release },
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title + if (onTitleClick != null) "  ›" else "",
            modifier = Modifier.padding(start = 48.dp)
                .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (releases.isEmpty()) {
            Text(stringResource(R.string.home_no_releases), Modifier.padding(start = 48.dp), color = Color.LightGray)
        } else {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(releases, key = { it.id }) { release ->
                    PosterCard(release, badge(release), timeBadge(release), release.id in favoriteIds,
                        modifier = if (firstPoster != null && release.id == releases.first().id) Modifier.focusRequester(firstPoster) else Modifier,
                        onFocus = { onFocus(release) }, onClick = { onClick(release.id) })
                }
            }
        }
    }
}

private fun relativeHours(value: String): Long? = try {
    val hours = ChronoUnit.HOURS.between(OffsetDateTime.parse(value), OffsetDateTime.now())
    hours.coerceAtLeast(0)
} catch (_: Exception) { null }
