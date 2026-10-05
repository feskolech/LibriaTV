package ru.feskolech.libriatv.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import ru.feskolech.libriatv.data.repo.ChangelogEntry
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ru.feskolech.libriatv.BuildConfig
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.makeQr

@Composable
fun AboutScreen(notes: String, checkUpdates: () -> Unit, updateState: UpdateUiState,
    loadChangelog: suspend () -> List<ChangelogEntry>?, onBack: () -> Unit) {
    var history by remember { mutableStateOf(false) }
    // "What's new" describes the installed version; the cached notes of the last update check
    // (possibly an older release) are only a fallback while offline.
    var installedNotes by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        installedNotes = loadChangelog()?.firstOrNull { it.version == BuildConfig.VERSION_NAME }?.notes
    }
    if (history) ChangelogDialog(loadChangelog, onDismiss = { history = false })
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    val repo = "https://github.com/${BuildConfig.UPDATE_REPO}"
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    LaunchedEffect(Unit) { first.requestFocus() }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 48.dp, vertical = 27.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground) }
        item { Button(onClick = onBack, modifier = Modifier.focusRequester(first)) { Text(stringResource(R.string.about_back)) } }
        item { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onBackground) }
        item { Text(stringResource(R.string.about_whats_new), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground) }
        // A short teaser of the latest release; the whole history of versions is one button away.
        item {
            val teaser = (installedNotes ?: notes).replace(Regex("\\s+"), " ").trim()
            Text(if (teaser.isBlank()) stringResource(R.string.about_no_notes)
                else if (teaser.length > 100) teaser.take(100).trimEnd() + "…" else teaser,
                color = MaterialTheme.colorScheme.onBackground)
        }
        item { Button(onClick = { history = true }) { Text(stringResource(R.string.about_more)) } }
        item { Button(onClick = checkUpdates) { Text(stringResource(R.string.settings_check_updates)) } }
        item {
            if (updateState == UpdateUiState.Checking) Text(stringResource(R.string.settings_checking),
                color = MaterialTheme.colorScheme.onBackground)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val qr = remember(repo) { makeQr(repo, 180) }
                Image(qr.asImageBitmap(), contentDescription = stringResource(R.string.settings_github),
                    modifier = Modifier.size(130.dp).background(Color.White).padding(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Both link buttons share the width of the longer one.
                    Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { open(repo) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_repository), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                        Button(onClick = { open("$repo/issues") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_issues), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    }
                    Text(repo, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        item { Text(stringResource(R.string.about_license), color = MaterialTheme.colorScheme.onBackground) }
        item { Text(stringResource(R.string.about_disclaimer), color = MaterialTheme.colorScheme.onBackground) }
    }
}

@Composable
private fun ChangelogDialog(load: suspend () -> List<ChangelogEntry>?, onDismiss: () -> Unit) {
    var entries by remember { mutableStateOf<List<ChangelogEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        val loaded = load()
        if (loaded == null) failed = true else entries = loaded
    }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.tv.material3.Surface(Modifier.width(820.dp).height(560.dp), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.about_history), style = MaterialTheme.typography.headlineSmall)
                val list = entries
                when {
                    failed -> {
                        Text(stringResource(R.string.about_history_error))
                        Button(onClick = { attempt++ }, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.retry)) }
                        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
                    }
                    list == null -> Text(stringResource(R.string.about_history_loading))
                    else -> {
                        // Each version is focusable so the remote can scroll through the list.
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(list, key = { _, e -> e.version }) { index, entry ->
                                ChangelogItem(entry, if (index == 0) Modifier.focusRequester(focus) else Modifier)
                            }
                        }
                        LaunchedEffect(list) { withFrameNanos { }; runCatching { focus.requestFocus() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogItem(entry: ChangelogEntry, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
        .background(Color(0xFF242428), RoundedCornerShape(12.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(listOfNotNull(stringResource(R.string.settings_version, entry.version), entry.date?.let { formatReleaseDate(it) })
            .joinToString("  •  "), style = MaterialTheme.typography.titleMedium)
        Text(entry.notes.ifBlank { stringResource(R.string.about_no_notes) }, color = Color(0xFFD8D8D8))
    }
}

private fun formatReleaseDate(isoDate: String): String =
    runCatching { java.time.LocalDate.parse(isoDate).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) }
        .getOrDefault(isoDate)
