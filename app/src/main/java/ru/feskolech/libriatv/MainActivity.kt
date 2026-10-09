package ru.feskolech.libriatv

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.focusable
import androidx.compose.runtime.withFrameNanos
import ru.feskolech.libriatv.ui.components.DrawerBrowsing
import ru.feskolech.libriatv.ui.components.LocalDrawerFocus
import androidx.compose.runtime.CompositionLocalProvider
import android.os.Bundle
import android.content.Intent
import android.app.Activity
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.rememberDrawerState
import dagger.hilt.android.AndroidEntryPoint
import ru.feskolech.libriatv.ui.theme.LibriaTvTheme
import ru.feskolech.libriatv.data.repo.SettingsStore
import ru.feskolech.libriatv.ui.auth.AuthScreen
import ru.feskolech.libriatv.ui.home.HomeScreen
import ru.feskolech.libriatv.ui.home.FeedScreen
import ru.feskolech.libriatv.ui.release.ReleaseScreen
import ru.feskolech.libriatv.ui.torrents.TorrentsScreen
import ru.feskolech.libriatv.ui.search.SearchScreen
import ru.feskolech.libriatv.ui.schedule.ScheduleScreen
import ru.feskolech.libriatv.ui.catalog.CatalogScreen
import ru.feskolech.libriatv.ui.favorites.FavoritesScreen
import ru.feskolech.libriatv.ui.player.PlayerScreen
import ru.feskolech.libriatv.ui.settings.SettingsScreen
import ru.feskolech.libriatv.ui.settings.UpdateDialog
import ru.feskolech.libriatv.ui.settings.UpdateUiState
import ru.feskolech.libriatv.ui.settings.UpdateViewModel
import ru.feskolech.libriatv.crash.CrashReportViewModel
import ru.feskolech.libriatv.remote.PhoneRemote
import ru.feskolech.libriatv.ui.components.UiSounds
import ru.feskolech.libriatv.remote.RemoteCommand
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var phoneRemote: PhoneRemote
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var userMessages: ru.feskolech.libriatv.ui.components.UserMessages
    private val deepLink = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
    private val voiceQuery = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { voiceQuery.tryEmit(it) }
        }
    }
    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_voice_prompt))
        if (intent.resolveActivity(packageManager) != null) voiceLauncher.launch(intent)
        else voiceQuery.tryEmit("")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLink.value = intent?.data
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                userMessages.messages.collect { text ->
                    android.widget.Toast.makeText(this@MainActivity, text, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        setContent {
            LibriaTvTheme(settingsStore) {
                // While the drawer collapses, its slot is briefly wider than its content; without a themed
                // background under everything that gap showed the window's grey (#303030) as a light strip.
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppNavigation(phoneRemote, deepLink, voiceQuery, ::startVoiceSearch, onExit = { finishAndRemoveTask() })
                }
            }
        }
    }
    /**
     * Compose does not play the system navigation clicks that View-based TV apps have; add them for
     * D-pad moves and OK. playSoundEffect honours the system "touch sounds" setting.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Back is not counted: it usually closes a page, and the focus passing through the menu meanwhile
        // must not open it (that also stopped Home from restoring the card the page was opened from).
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            DrawerBrowsing.lastMenuKeyAt = android.os.SystemClock.uptimeMillis()
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_SEARCH || event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST)) {
            startVoiceSearch()
            return true
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN && UiSounds.enabled) {
            val effect = when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> android.view.SoundEffectConstants.NAVIGATION_UP
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> android.view.SoundEffectConstants.NAVIGATION_DOWN
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> android.view.SoundEffectConstants.NAVIGATION_LEFT
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> android.view.SoundEffectConstants.NAVIGATION_RIGHT
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> android.view.SoundEffectConstants.CLICK
                else -> null
            }
            if (effect != null && event.repeatCount == 0) window.decorView.playSoundEffect(effect)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.data
    }
    override fun onResume() { super.onResume(); lifecycleScope.launch { phoneRemote.foreground(true) } }
    override fun onPause() { lifecycleScope.launch { phoneRemote.foreground(false) }; super.onPause() }
}

private enum class Destination(val route: String, val title: Int, val icon: ImageVector) {
    Home("home", R.string.home, Icons.Default.Home),
    Search("search", R.string.search, Icons.Default.Search),
    Catalog("catalog", R.string.catalog, Icons.Default.GridView),
    Favorites("favorites", R.string.favorites, Icons.Default.Favorite),
    Schedule("schedule", R.string.schedule, Icons.Default.DateRange),
    Profile("profile", R.string.profile, Icons.Default.Person),
    Settings("settings", R.string.settings, Icons.Default.Settings),
}

@Composable
private fun AppNavigation(phoneRemote: PhoneRemote, deepLink: kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>,
    voiceQuery: kotlinx.coroutines.flow.SharedFlow<String>, startVoiceSearch: () -> Unit, onExit: () -> Unit) {
    val crashViewModel: CrashReportViewModel = hiltViewModel()
    val crashPrompt by crashViewModel.prompt.collectAsState()
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsState()
    val latestNotes by updateViewModel.latestNotes.collectAsState(initial = "")
    LaunchedEffect(Unit) { updateViewModel.check() }
    val navController = rememberNavController()
    LaunchedEffect(voiceQuery) {
        voiceQuery.collect { query -> navController.navigate("search?query=${android.net.Uri.encode(query)}") }
    }
    LaunchedEffect(deepLink) {
        deepLink.collect { uri ->
            // Any installed app may send these intents: accept only well-formed ids, so a crafted
            // segment (e.g. "a%2Fb") cannot turn into an unknown nav route and crash the app.
            if (uri?.scheme == "libriatv" && uri.host == "play" && uri.pathSegments.size == 2 &&
                uri.pathSegments[0].toIntOrNull() != null && uri.pathSegments[1].matches(Regex("[A-Za-z0-9_-]{1,80}"))) {
                navController.navigate("player/${uri.pathSegments[0]}/${uri.pathSegments[1]}")
                deepLink.value = null
            } else if (uri?.scheme == "libriatv" && uri.host == "release" && uri.pathSegments.firstOrNull()?.toIntOrNull() != null) {
                navController.navigate("release/${uri.pathSegments[0]}")
                deepLink.value = null
            }
        }
    }
    LaunchedEffect(phoneRemote) {
        phoneRemote.commands.collect { command ->
            when (command) {
                is RemoteCommand.OpenRelease -> navController.navigate("release/${command.id}")
                is RemoteCommand.OpenEpisode -> navController.navigate("player/${command.releaseId}/${command.episodeId}")
                else -> Unit
            }
        }
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Destination.Home.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val firstItemFocus = remember { FocusRequester() }
    val itemFocus = remember { Destination.entries.associateWith { FocusRequester() } }
    // A release page / player belongs to the section it was opened from (catalog, favorites…), so the
    // menu highlights that section and entering the menu lands on it, not on Home.
    var lastSection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Destination.Home.route) }
    val routeSection = Destination.entries.firstOrNull { it.route == currentRoute }
        ?: if (currentRoute.startsWith("search?")) Destination.Search else null
    LaunchedEffect(routeSection) { routeSection?.let { lastSection = it.route } }
    val selectedDestination = routeSection ?: Destination.entries.firstOrNull { it.route == lastSection } ?: Destination.Home
    var confirmExit by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // Opening a page: park the focus on an invisible anchor in the content area first. Otherwise the
    // focused card disappears with the old screen and the focus falls into the side menu for a moment,
    // which slides the menu open and shut ("the menu twitches"). The new page then takes the focus.
    val focusAnchor = remember { FocusRequester() }
    var parking by remember { mutableStateOf(false) }
    fun openPage(route: String) {
        parking = true
        runCatching { focusAnchor.requestFocus() }
        navController.navigate(route)
    }
    fun openSection(destination: Destination) {
        if (currentRoute != destination.route) {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    // Side-menu browsing: the focused section opens after a short pause, so quickly scrolling
    // past items does not load every screen on the way.
    var drawerFocused by remember { mutableStateOf(false) }
    var lastMenuItem by remember { mutableStateOf<Destination?>(null) }
    var previewTarget by remember { mutableStateOf<Destination?>(null) }
    LaunchedEffect(drawerState.currentValue) {
        DrawerBrowsing.active = drawerState.currentValue == DrawerValue.Open
        if (drawerState.currentValue == DrawerValue.Closed) { lastMenuItem = null; previewTarget = null }
    }
    var enterAfterOpen by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute, enterAfterOpen) {
        if (!enterAfterOpen) return@LaunchedEffect
        // Wait out the cross-fade (the old screen must be gone), then give the new one up to ~1 s of
        // loading to have something to focus.
        kotlinx.coroutines.delay(200)
        repeat(20) {
            withFrameNanos { }
            if (focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)) { enterAfterOpen = false; return@LaunchedEffect }
            kotlinx.coroutines.delay(50)
        }
        enterAfterOpen = false
    }
    LaunchedEffect(previewTarget) {
        val target = previewTarget ?: return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        openSection(target)
    }

    BackHandler(confirmExit || drawerState.currentValue == DrawerValue.Open || currentRoute == Destination.Home.route) {
        when {
            confirmExit -> confirmExit = false
            drawerState.currentValue == DrawerValue.Open -> drawerState.setValue(DrawerValue.Closed)
            else -> confirmExit = true
        }
    }

    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            val drawerColor by androidx.compose.animation.animateColorAsState(
                if (drawerValue == DrawerValue.Open) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background, label = "drawer")
            Column(
                modifier = Modifier
                    .then(
                        when {
                            currentRoute.startsWith("player/") -> Modifier.width(0.dp)
                            drawerValue == DrawerValue.Open -> Modifier.width(280.dp)
                            // Fixed collapsed width: measuring intrinsics on every animation frame stuttered on TV boxes.
                            else -> Modifier.width(88.dp)
                        }
                    )
                    .fillMaxHeight()
                    // Same colour as the screens when collapsed, so no lighter strip shows while it animates;
                    // only the open drawer stands out a little.
                    .background(drawerColor)
                    .padding(horizontal = 8.dp, vertical = 27.dp)
                    .onFocusChanged { drawerFocused = it.hasFocus }
                    .selectableGroup()
                    // Entering the drawer from content lands on the current section, not on the nearest row.
                    .focusProperties {
                        onEnter = {
                            val byArrow = requestedFocusDirection == androidx.compose.ui.focus.FocusDirection.Left ||
                                requestedFocusDirection == androidx.compose.ui.focus.FocusDirection.Up ||
                                requestedFocusDirection == androidx.compose.ui.focus.FocusDirection.Down
                            val asked = android.os.SystemClock.uptimeMillis() - DrawerBrowsing.lastMenuKeyAt < 700
                            // Focus that only falls into the menu while one screen replaces another (opening a
                            // release page) is refused: the drawer would slide open for a moment. The new
                            // screen takes the focus a frame later.
                            if (!byArrow && !asked && currentRoute != Destination.Home.route &&
                                Destination.entries.none { it.route == currentRoute }) cancelFocusChange()
                            else itemFocus.getValue(selectedDestination).requestFocus()
                        }
                    }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Destination.entries.forEachIndexed { index, destination ->
                    NavigationDrawerItem(
                        selected = destination == selectedDestination,
                        // The section already opened while the item was focused; OK just steps into it.
                        onClick = {
                            if (currentRoute == destination.route) {
                                focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
                            } else {
                                // Scrolled quickly: the section is not open yet. Stepping right now would land in
                                // the old screen, which then disappears and drops the focus back into the menu.
                                previewTarget = null
                                openSection(destination)
                                enterAfterOpen = true
                            }
                        },
                        leadingContent = { Icon(destination.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        modifier = (if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                            .focusRequester(itemFocus.getValue(destination))
                            // Open: items fill the drawer exactly, otherwise their default width overflows and the focus pill is clipped.
                            .then(if (drawerValue == DrawerValue.Open) Modifier.fillMaxWidth() else Modifier.width(72.dp))
                            .onFocusChanged {
                                if (it.isFocused) {
                                    // Only moving inside an already open menu previews a section; focus that merely
                                    // falls into the menu (e.g. while a release page is still loading) must not.
                                    val browsing = drawerState.currentValue == DrawerValue.Open
                                    if (!browsing) lastMenuItem = null
                                    // Focus that merely passes through the menu while screens swap (opening a release
                                    // page) must not slide the menu open for a moment: only Left/Back open it.
                                    val askedForMenu = android.os.SystemClock.uptimeMillis() - DrawerBrowsing.lastMenuKeyAt < 700
                                    if (browsing || askedForMenu || drawerFocused) drawerState.setValue(DrawerValue.Open)
                                    // Moving within the menu opens the section as a preview (not the item the
                                    // menu was entered on: that one is the current screen, e.g. a release page).
                                    if (browsing && lastMenuItem != null && lastMenuItem != destination) previewTarget = destination
                                    lastMenuItem = destination
                                }
                            },
                    ) {
                        if (drawerValue == DrawerValue.Open) Text(stringResource(destination.title), fontSize = 20.sp)
                    }
                }
            }
        },
    ) {
        // Screens use this to send Left from their leftmost controls straight to the side menu.
        // Focusable only while parking, so arrow keys never land on it.
        androidx.compose.foundation.layout.Box(Modifier.size(1.dp).focusRequester(focusAnchor)
            .focusProperties { canFocus = parking }
            .onFocusChanged { if (!it.isFocused) parking = false }
            .focusable())
        CompositionLocalProvider(LocalDrawerFocus provides itemFocus.getValue(selectedDestination)) {
            // Short cross-fade: the old screen stays focusable while it fades out, and browsing the menu
            // swaps sections often, so the default 700 ms fade felt slow and could catch the focus.
            NavHost(navController = navController, startDestination = Destination.Home.route,
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
                popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
                popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) }) {
                Destination.entries.forEach { destination ->
                    composable(destination.route) {
                        if (destination == Destination.Profile) {
                            AuthScreen(
                                onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                                onOpenMenu = { DrawerBrowsing.lastMenuKeyAt = android.os.SystemClock.uptimeMillis(); firstItemFocus.requestFocus() },
                            )
                        } else if (destination == Destination.Catalog) {
                            CatalogScreen(
                                onOpenRelease = { openPage("release/$it") },
                                onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                            )
                        } else if (destination == Destination.Favorites) {
                            FavoritesScreen(
                                onOpenRelease = { openPage("release/$it") },
                                onLogin = { navController.navigate(Destination.Profile.route) },
                                onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                            )
                        } else if (destination == Destination.Schedule) {
                            ScheduleScreen(
                                onOpenRelease = { openPage("release/$it") },
                                onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                            )
                        } else if (destination == Destination.Search) {
                            SearchScreen(
                                onOpenRelease = { openPage("release/$it") },
                                onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                            )
                        } else if (destination == Destination.Home) {
                            HomeScreen(
                                active = currentRoute == Destination.Home.route,
                                onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                                onOpenFeed = { navController.navigate("feed") },
                                onOpenRelease = { openPage("release/$it") },
                            )
                        } else if (destination == Destination.Settings) {
                            SettingsScreen(
                                onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                                checkUpdates = { updateViewModel.check(force = true) },
                                updateState = updateState,
                                latestNotes = latestNotes,
                                loadChangelog = { updateViewModel.changelog() },
                            )
                        } else {
                            PlaceholderScreen(destination.title,
                                onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                                onOpenMenu = { DrawerBrowsing.lastMenuKeyAt = android.os.SystemClock.uptimeMillis(); firstItemFocus.requestFocus() })
                        }
                    }
                }
                composable("search?query={query}") { entry ->
                    SearchScreen(onOpenRelease = { openPage("release/$it") },
                        onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                        initialQuery = entry.arguments?.getString("query").orEmpty())
                }
                composable("feed") {
                    FeedScreen(onOpenRelease = { openPage("release/$it") },
                        onContentFocus = { drawerState.setValue(DrawerValue.Closed) })
                }
                composable("release/{id}") { entry ->
                    ReleaseScreen(
                        onPlay = { openPage("player/${entry.arguments?.getString("id")}/$it") },
                        onTorrents = { navController.navigate("torrents/$it") },
                        onLogin = { navController.navigate(Destination.Profile.route) },
                        onOpenRelease = { openPage("release/$it") },
                    )
                }
                composable("torrents/{releaseId}") { TorrentsScreen() }
                composable("player/{id}/{episodeId}") {
                    PlayerScreen(onBack = { navController.popBackStack() }, remoteCommands = phoneRemote.commands)
                }
            }
        }
    }

    UpdateDialog(updateState,
        download = { (updateState as? UpdateUiState.Available)?.let { updateViewModel.download(it.release) } },
        dismiss = updateViewModel::dismiss)

    if (crashPrompt) {
        androidx.compose.ui.window.Dialog(onDismissRequest = crashViewModel::decline) {
            androidx.tv.material3.Surface {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(stringResource(R.string.crash_report_question), style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { crashViewModel.send(always = false) }) {
                        Text(stringResource(R.string.crash_report_send))
                    }
                    Button(onClick = crashViewModel::decline) {
                        Text(stringResource(R.string.crash_report_decline))
                    }
                    Button(onClick = { crashViewModel.send(always = true) }) {
                        Text(stringResource(R.string.crash_report_always))
                    }
                }
            }
        }
    }

    if (confirmExit) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmExit = false }) {
            // Rounded card, both buttons on one line and of equal width.
            androidx.tv.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                Column(Modifier.width(460.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(stringResource(R.string.exit_question), style = MaterialTheme.typography.headlineSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onExit, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.exit), modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        Button(onClick = { confirmExit = false }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.cancel), modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: Int, onContentFocus: () -> Unit, onOpenMenu: () -> Unit,
    actionTitle: Int = R.string.open_menu) {
    val contentFocus = remember { FocusRequester() }
    LaunchedEffect(title) { contentFocus.requestFocus() }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onContentFocus() },
        colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.padding(horizontal = 48.dp, vertical = 27.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onOpenMenu, modifier = Modifier.focusRequester(contentFocus)) {
                Text(stringResource(actionTitle))
            }
        }
    }
}
