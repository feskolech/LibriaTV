package ru.feskolech.libriatv.ui.settings

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
fun AboutScreen(notes: String, checkUpdates: () -> Unit, updateState: UpdateUiState, onBack: () -> Unit) {
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
        item { Text(notes.ifBlank { stringResource(R.string.about_no_notes) }, color = MaterialTheme.colorScheme.onBackground) }
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
