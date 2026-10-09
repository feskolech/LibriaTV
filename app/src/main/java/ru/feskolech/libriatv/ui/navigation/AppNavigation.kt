package ru.feskolech.libriatv.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.crash.CrashReportViewModel
import ru.feskolech.libriatv.remote.PhoneRemote
import ru.feskolech.libriatv.remote.RemoteCommand
import ru.feskolech.libriatv.ui.auth.AuthScreen
import ru.feskolech.libriatv.ui.catalog.CatalogScreen
import ru.feskolech.libriatv.ui.components.AppDialog
import ru.feskolech.libriatv.ui.components.DialogButton
import ru.feskolech.libriatv.ui.favorites.FavoritesScreen
import ru.feskolech.libriatv.ui.home.FeedScreen
import ru.feskolech.libriatv.ui.home.HomeScreen
import ru.feskolech.libriatv.ui.player.PlayerScreen
import ru.feskolech.libriatv.ui.release.ReleaseScreen
import ru.feskolech.libriatv.ui.schedule.ScheduleScreen
import ru.feskolech.libriatv.ui.search.SearchScreen
import ru.feskolech.libriatv.ui.settings.SettingsScreen
import ru.feskolech.libriatv.ui.settings.UpdateDialog
import ru.feskolech.libriatv.ui.settings.UpdateUiState
import ru.feskolech.libriatv.ui.settings.UpdateViewModel
import ru.feskolech.libriatv.ui.torrents.TorrentsScreen

/** The app's screens inside [AppShell], plus deep links, the phone remote and the app-wide dialogs. */
@Composable
fun AppNavigation(phoneRemote: PhoneRemote, deepLink: MutableStateFlow<Uri?>,
    voiceQuery: SharedFlow<String>, onExit: () -> Unit) {
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsState()
    val latestNotes by updateViewModel.latestNotes.collectAsState(initial = "")
    LaunchedEffect(Unit) { updateViewModel.check() }
    val navController = rememberNavController()
    LaunchedEffect(voiceQuery) {
        voiceQuery.collect { query -> navController.navigate(Routes.search(query)) }
    }
    LaunchedEffect(deepLink) {
        deepLink.collect { uri ->
            val route = uri?.let { Routes.fromDeepLink(it.scheme, it.host, it.pathSegments) } ?: return@collect
            navController.navigate(route)
            deepLink.value = null
        }
    }
    LaunchedEffect(phoneRemote) {
        phoneRemote.commands.collect { command ->
            when (command) {
                is RemoteCommand.OpenRelease -> navController.navigate(Routes.release(command.id))
                is RemoteCommand.OpenEpisode -> navController.navigate(Routes.player(command.releaseId, command.episodeId))
                else -> Unit
            }
        }
    }

    AppShell(navController, onExit) { shell ->
        val openRelease: (Int) -> Unit = { shell.openPage(Routes.release(it)) }
        Destination.entries.forEach { destination ->
            composable(destination.route) {
                when (destination) {
                    Destination.Home -> HomeScreen(
                        active = shell.currentRoute() == Destination.Home.route,
                        onContentFocus = shell.closeMenu,
                        onOpenFeed = { navController.navigate(Routes.FEED) },
                        onOpenRelease = openRelease,
                    )
                    Destination.Search -> SearchScreen(onOpenRelease = openRelease, onContentFocus = shell.closeMenu)
                    Destination.Catalog -> CatalogScreen(onOpenRelease = openRelease, onContentFocus = shell.closeMenu)
                    Destination.Favorites -> FavoritesScreen(
                        onOpenRelease = openRelease,
                        onLogin = { navController.navigate(Destination.Profile.route) },
                        onContentFocus = shell.closeMenu,
                    )
                    Destination.Schedule -> ScheduleScreen(onOpenRelease = openRelease, onContentFocus = shell.closeMenu)
                    Destination.Profile -> AuthScreen(onContentFocus = shell.closeMenu, onOpenMenu = shell.focusMenu)
                    Destination.Settings -> SettingsScreen(
                        onContentFocus = shell.closeMenu,
                        checkUpdates = { updateViewModel.check(force = true) },
                        updateState = updateState,
                        latestNotes = latestNotes,
                        loadChangelog = { updateViewModel.changelog() },
                    )
                }
            }
        }
        composable(Routes.SEARCH_QUERY) { entry ->
            SearchScreen(onOpenRelease = openRelease, onContentFocus = shell.closeMenu,
                initialQuery = entry.arguments?.getString("query").orEmpty())
        }
        composable(Routes.FEED) { FeedScreen(onOpenRelease = openRelease, onContentFocus = shell.closeMenu) }
        composable(Routes.RELEASE) { entry ->
            val releaseId = entry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
            ReleaseScreen(
                onPlay = { shell.openPage(Routes.player(releaseId, it)) },
                onTorrents = { navController.navigate(Routes.torrents(it)) },
                onLogin = { navController.navigate(Destination.Profile.route) },
                onOpenRelease = openRelease,
            )
        }
        composable(Routes.TORRENTS) { TorrentsScreen() }
        composable(Routes.PLAYER) {
            PlayerScreen(onBack = { navController.popBackStack() }, remoteCommands = phoneRemote.commands)
        }
    }

    UpdateDialog(updateState,
        download = { (updateState as? UpdateUiState.Available)?.let { updateViewModel.download(it.release) } },
        dismiss = updateViewModel::dismiss)
    CrashReportPrompt()
}

@Composable
private fun CrashReportPrompt() {
    val crashViewModel: CrashReportViewModel = hiltViewModel()
    val prompt by crashViewModel.prompt.collectAsState()
    if (!prompt) return
    AppDialog(onDismiss = crashViewModel::decline, title = stringResource(R.string.crash_report_question), width = 680.dp,
        actions = {
            DialogButton(stringResource(R.string.crash_report_send), { crashViewModel.send(always = false) }, initialFocus = true)
            DialogButton(stringResource(R.string.crash_report_decline), crashViewModel::decline)
            DialogButton(stringResource(R.string.crash_report_always), { crashViewModel.send(always = true) })
        })
}
