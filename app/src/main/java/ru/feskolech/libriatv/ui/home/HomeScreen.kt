package ru.feskolech.libriatv.ui.home

import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.ScheduleItem
import ru.feskolech.libriatv.ui.components.PosterCard

@Composable
fun HomeScreen(
    onContentFocus: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenRelease: (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    when (val content = state) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(48.dp)) {
            Text(stringResource(R.string.home_loading))
        }
        is HomeUiState.Error -> Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.home_error))
            Text(content.message)
            Button(onClick = { viewModel.refresh(force = true) }) { Text(stringResource(R.string.retry)) }
        }
        is HomeUiState.Content -> HomeContent(content, onContentFocus, onOpenFeed, onOpenRelease)
    }
}

@Composable
private fun HomeContent(
    content: HomeUiState.Content,
    onContentFocus: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenRelease: (Int) -> Unit,
) {
    var focusedRelease by remember(content) { mutableStateOf(content.latest.firstOrNull()) }
    var backgroundRelease by remember(content) { mutableStateOf(focusedRelease) }
    val firstPoster = remember { FocusRequester() }
    LaunchedEffect(content) { firstPoster.requestFocus() }
    LaunchedEffect(focusedRelease?.id) {
        delay(300)
        backgroundRelease = focusedRelease
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF101010)).onFocusChanged { if (it.hasFocus) onContentFocus() }) {
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
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 27.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            item {
                PosterRow(
                    title = stringResource(R.string.new_episodes),
                    releases = content.latest,
                    badge = { it.latestEpisode?.ordinal?.let { n -> stringResource(R.string.episode_number, n.toInt()) } },
                    timeBadge = { it.freshAt?.let { value -> relativeHours(value)?.let { hours -> stringResource(R.string.hours_ago, hours) } } },
                    favoriteIds = content.favoriteIds,
                    onTitleClick = onOpenFeed,
                    firstPoster = firstPoster,
                    onFocus = { focusedRelease = it }, onClick = onOpenRelease,
                )
            }
            item {
                ScheduleRow(stringResource(R.string.today), content.today, content.favoriteIds,
                    { focusedRelease = it }, onOpenRelease)
            }
            item {
                ScheduleRow(stringResource(R.string.tomorrow), content.tomorrow, content.favoriteIds,
                    { focusedRelease = it }, onOpenRelease)
            }
            if (content.isAuthorized) item {
                PosterRow(stringResource(R.string.favorites), content.favorites,
                    badge = { null }, favoriteIds = content.favoriteIds,
                    onFocus = { focusedRelease = it }, onClick = onOpenRelease)
            }
            }
        }
    }
}

@Composable
private fun SelectedRelease(release: Release?) {
    Column(Modifier.fillMaxWidth().height(135.dp).padding(horizontal = 48.dp), verticalArrangement = Arrangement.Bottom) {
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
