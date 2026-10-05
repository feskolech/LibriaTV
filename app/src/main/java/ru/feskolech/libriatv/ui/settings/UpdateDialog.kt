package ru.feskolech.libriatv.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R

@Composable
fun UpdateDialog(state: UpdateUiState, download: () -> Unit, dismiss: () -> Unit) {
    if (state !is UpdateUiState.Available && state !is UpdateUiState.Downloading &&
        state !is UpdateUiState.Ready && state !is UpdateUiState.Failed) return
    val context = LocalContext.current
    var permissionNeeded by remember(state) { mutableStateOf(false) }
    Dialog(onDismissRequest = dismiss) {
        Surface {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (state) {
                    is UpdateUiState.Available -> {
                        Text(stringResource(R.string.update_available, state.release.version), style = MaterialTheme.typography.headlineSmall)
                        if (state.release.notes.isNotBlank()) Text(state.release.notes, maxLines = 8)
                        Button(onClick = download) { Text(stringResource(R.string.update_now)) }
                        Button(onClick = dismiss) { Text(stringResource(R.string.update_later)) }
                    }
                    is UpdateUiState.Downloading -> {
                        Text(stringResource(R.string.update_downloading, state.percent), style = MaterialTheme.typography.headlineSmall)
                        Box(Modifier.fillMaxWidth().height(8.dp).background(Color.DarkGray)) {
                            Box(Modifier.fillMaxWidth(state.percent / 100f).height(8.dp).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    is UpdateUiState.Ready -> {
                        Text(stringResource(R.string.update_ready), style = MaterialTheme.typography.headlineSmall)
                        if (permissionNeeded) Text(stringResource(R.string.update_permission_hint))
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                                permissionNeeded = true
                                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}")))
                            } else {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", state.file)
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        }) { Text(stringResource(R.string.update_install)) }
                        Button(onClick = dismiss) { Text(stringResource(R.string.update_later)) }
                    }
                    UpdateUiState.Failed -> {
                        Text(stringResource(R.string.settings_download_failed))
                        Button(onClick = dismiss) { Text(stringResource(R.string.close)) }
                    }
                    else -> Unit
                }
            }
        }
    }
}
