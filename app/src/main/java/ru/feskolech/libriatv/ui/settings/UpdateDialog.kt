package ru.feskolech.libriatv.ui.settings

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.RowScope
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import ru.feskolech.libriatv.ui.components.AppDialog
import ru.feskolech.libriatv.ui.components.DialogButton
import ru.feskolech.libriatv.ui.components.SingleDialogButton
import androidx.core.content.FileProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.data.repo.verifyDownloadedApk

@Composable
fun UpdateDialog(state: UpdateUiState, download: () -> Unit, dismiss: () -> Unit) {
    if (state !is UpdateUiState.Available && state !is UpdateUiState.Downloading &&
        state !is UpdateUiState.Ready && state !is UpdateUiState.Failed && state !is UpdateUiState.InvalidApk) return
    val context = LocalContext.current
    var permissionNeeded by remember(state) { mutableStateOf(false) }
    var invalidApk by remember(state) { mutableStateOf(false) }
    val install = {
        val file = (state as UpdateUiState.Ready).file
        if (!verifyDownloadedApk(context, file)) {
            file.delete()
            invalidApk = true
        } else if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            permissionNeeded = true
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
    val title = when (state) {
        is UpdateUiState.Available -> stringResource(R.string.update_available, state.release.version)
        is UpdateUiState.Downloading -> stringResource(R.string.update_downloading, state.percent)
        is UpdateUiState.Ready -> stringResource(R.string.update_ready)
        else -> null
    }
    val later = stringResource(R.string.update_later)
    val close = stringResource(R.string.close)
    val actions: (@Composable RowScope.() -> Unit)? = when (state) {
        is UpdateUiState.Available -> ({
            DialogButton(stringResource(R.string.update_now), download, initialFocus = true)
            DialogButton(later, dismiss)
        })
        is UpdateUiState.Ready -> ({
            DialogButton(stringResource(R.string.update_install), install, initialFocus = true)
            DialogButton(later, dismiss)
        })
        UpdateUiState.Failed, UpdateUiState.InvalidApk -> ({ SingleDialogButton(close, dismiss) })
        else -> null
    }
    AppDialog(onDismiss = dismiss, title = title, actions = actions) {
        when (state) {
            is UpdateUiState.Available -> if (state.release.notes.isNotBlank()) Text(state.release.notes, maxLines = 8)
            is UpdateUiState.Downloading ->
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(state.percent / 100f).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary))
                }
            is UpdateUiState.Ready -> {
                if (permissionNeeded) Text(stringResource(R.string.update_permission_hint))
                if (invalidApk) Text(stringResource(R.string.update_invalid_apk))
            }
            UpdateUiState.Failed -> Text(stringResource(R.string.settings_download_failed))
            UpdateUiState.InvalidApk -> Text(stringResource(R.string.update_invalid_apk))
            else -> Unit
        }
    }
}
