package ru.feskolech.libriatv.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.PosterCard

private val Accent = Color(0xFFB32121)

@Composable
fun SearchScreen(
    onOpenRelease: (Int) -> Unit,
    onContentFocus: () -> Unit,
    initialQuery: String = "",
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) viewModel.submit(initialQuery) }
    val results by viewModel.results.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }

    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.search_voice_prompt))
    }
    val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::submit)
        }
    }

    // Don't start in the text field: focusing it pops the on-screen keyboard right away. Start on the
    // mic so the viewer chooses: OK = voice, Left = type.
    val micFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!voiceAvailable || runCatching { micFocus.requestFocus() }.isFailure) runCatching { fieldFocus.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF101010)).onFocusChanged { if (it.hasFocus) onContentFocus() }
            .padding(top = 27.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                value = query,
                onChange = viewModel::onQueryChange,
                onSearch = {
                    keyboard?.hide()
                    viewModel.submit()
                },
                // A single-line field keeps D-pad Down for itself; hand focus to the results explicitly.
                modifier = Modifier.weight(1f).focusRequester(fieldFocus).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown &&
                        results is SearchResults.Content && (results as SearchResults.Content).releases.isNotEmpty()
                    ) {
                        keyboard?.hide()
                        runCatching { firstResult.requestFocus() }.isSuccess
                    } else false
                },
            )
            if (voiceAvailable) {
                Button(onClick = { voice.launch(voiceIntent) }, modifier = Modifier.focusRequester(micFocus)) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.search_voice))
                }
            }
        }

        when (val current = results) {
            SearchResults.Idle -> RecentQueries(recent, onPick = viewModel::submit, onClear = viewModel::clearHistory)
            SearchResults.Loading -> Text(stringResource(R.string.search_loading), Modifier.padding(horizontal = 48.dp), color = Color.LightGray)
            is SearchResults.Error -> Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(current.message, color = Color.White)
                Button(onClick = { viewModel.submit() }) { Text(stringResource(R.string.retry)) }
            }
            is SearchResults.Content -> if (current.releases.isEmpty()) {
                Text(stringResource(R.string.search_nothing, current.query), Modifier.padding(horizontal = 48.dp), color = Color.LightGray)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 27.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(current.releases, key = { it.id }) { release ->
                        PosterCard(
                            release,
                            badge = release.year?.toString(),
                            modifier = if (release.id == current.releases.first().id) Modifier.focusRequester(firstResult) else Modifier,
                            onFocus = onContentFocus,
                            onClick = {
                                viewModel.remember()
                                onOpenRelease(release.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        textStyle = TextStyle(color = Color.White, fontSize = 24.sp),
        cursorBrush = SolidColor(Color.White),
        modifier = modifier.height(60.dp).onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 1.dp, if (focused) Accent else Color(0xFF656565), RoundedCornerShape(30.dp))
            .background(Color(0xFF26262A), RoundedCornerShape(30.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(26.dp))
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) Text(stringResource(R.string.search_hint), color = Color(0xFF8B8B8B), fontSize = 22.sp)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun RecentQueries(recent: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    if (recent.isEmpty()) {
        Text(stringResource(R.string.search_empty_hint), Modifier.padding(horizontal = 48.dp), color = Color.LightGray)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.search_recent), Modifier.padding(horizontal = 48.dp),
            style = MaterialTheme.typography.titleLarge, color = Color.White)
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recent) { item -> Button(onClick = { onPick(item) }) { Text(item) } }
            item { Button(onClick = onClear) { Text(stringResource(R.string.search_clear_history)) } }
        }
    }
}
