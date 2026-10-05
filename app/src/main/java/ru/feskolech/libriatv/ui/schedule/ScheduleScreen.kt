package ru.feskolech.libriatv.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.PosterCard

@Composable
fun ScheduleScreen(
    onOpenRelease: (Int) -> Unit,
    onContentFocus: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val days = stringArrayResource(R.array.week_days)
    val tabFocus = remember { FocusRequester() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onFocusChanged { if (it.hasFocus) onContentFocus() }
            .padding(top = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.schedule), Modifier.padding(horizontal = 48.dp),
            style = MaterialTheme.typography.headlineLarge, color = Color.White)
        when (val current = state) {
            ScheduleUiState.Loading -> Text(stringResource(R.string.home_loading), Modifier.padding(horizontal = 48.dp), color = Color.LightGray)
            is ScheduleUiState.Error -> Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(current.message, color = Color.White)
                Button(onClick = { viewModel.refresh() }, modifier = Modifier.focusRequester(tabFocus)) { Text(stringResource(R.string.retry)) }
                LaunchedEffect(Unit) { withFrameNanos { }; runCatching { tabFocus.requestFocus() } }
            }
            is ScheduleUiState.Content -> {
                var selected by rememberSaveable { mutableIntStateOf(current.today) }
                // TV tabs switch on focus, like the system launcher.
                TabRow(selectedTabIndex = selected, modifier = Modifier.padding(horizontal = 48.dp)) {
                    days.forEachIndexed { index, day ->
                        Tab(
                            selected = index == selected,
                            onFocus = { selected = index },
                            modifier = if (index == selected) Modifier.focusRequester(tabFocus) else Modifier,
                        ) {
                            val label = if (index == current.today) stringResource(R.string.schedule_today, day) else day
                            Text(label, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                LaunchedEffect(Unit) { withFrameNanos { }; runCatching { tabFocus.requestFocus() } }

                val items = current.days[selected]
                if (items.isEmpty()) {
                    Text(stringResource(R.string.schedule_empty_day), Modifier.padding(horizontal = 48.dp), color = Color.LightGray)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 27.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        items(items, key = { it.release.id }) { item ->
                            PosterCard(
                                item.release,
                                badge = item.nextEpisodeNumber?.let { stringResource(R.string.episode_number, it) },
                                onFocus = onContentFocus,
                                onClick = { onOpenRelease(item.release.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
