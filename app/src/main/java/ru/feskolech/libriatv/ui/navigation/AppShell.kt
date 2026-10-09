package ru.feskolech.libriatv.ui.navigation

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.rememberDrawerState
import kotlinx.coroutines.delay
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.AppDialog
import ru.feskolech.libriatv.ui.components.DialogButton
import ru.feskolech.libriatv.ui.components.DrawerBrowsing
import ru.feskolech.libriatv.ui.components.LocalDrawerFocus

/** How long OK on a menu item keeps waiting for a loading section to have something to focus. */
private const val ENTER_SECTION_TIMEOUT_MS = 30_000L
private const val ENTER_SECTION_STEP_MS = 50L
/** Cross-fade between menu sections, and between a section and a page (release, player…). */
private const val SECTION_FADE_MS = 350
private const val PAGE_FADE_MS = 150

/** What the screens of the graph get from the shell. */
internal class ShellScope(
    /** The route on screen now. */
    val currentRoute: () -> String,
    /** Opens a page (release, player…) without the focus passing through the menu on the way. */
    val openPage: (String) -> Unit,
    /** A screen took the focus: the menu collapses. */
    val closeMenu: () -> Unit,
    /** A screen's own "menu" control: moves the focus into the side menu. */
    val focusMenu: () -> Unit,
)

/**
 * The app's frame: side menu, screen switching and the exit dialog, with every focus rule between
 * the menu and the screens. The screens themselves come from [graph], so the frame can be tested with
 * stand-in screens.
 */
@Composable
internal fun AppShell(navController: NavHostController, onExit: () -> Unit, graph: NavGraphBuilder.(ShellScope) -> Unit) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Destination.Home.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val itemFocus = remember { Destination.entries.associateWith { FocusRequester() } }
    // A release page / player belongs to the section it was opened from (catalog, favorites…), so the
    // menu highlights that section and entering the menu lands on it, not on Home.
    var lastSection by rememberSaveable { mutableStateOf(Destination.Home.route) }
    val routeSection = Routes.section(currentRoute)
    LaunchedEffect(routeSection) { routeSection?.let { lastSection = it.route } }
    val selectedDestination = routeSection ?: Destination.entries.firstOrNull { it.route == lastSection } ?: Destination.Home
    var confirmExit by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // Opening a page: park the focus on an invisible anchor in the content area first. Otherwise the
    // focused card disappears with the old screen and the focus falls into the side menu for a moment,
    // which slides the menu open and shut ("the menu twitches"). The new page then takes the focus.
    val focusAnchor = remember { FocusRequester() }
    var parking by remember { mutableStateOf(false) }
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
    // OK on a menu item means "go into this section". A section that is still loading (the profile or
    // Home on a cold start over a slow link) has nothing to focus yet, so the request waits for it
    // instead of giving up after a second and leaving the menu open over a loaded screen. It is
    // dropped as soon as the focus leaves the menu some other way or the viewer moves on in it.
    var pendingEnter by remember { mutableStateOf<Destination?>(null) }
    LaunchedEffect(currentRoute, pendingEnter) {
        val target = pendingEnter ?: return@LaunchedEffect
        if (currentRoute != target.route) return@LaunchedEffect
        // Wait out the cross-fade: the old screen must be gone, or the focus would land on it.
        delay(SECTION_FADE_MS + 50L)
        for (attempt in 0 until ENTER_SECTION_TIMEOUT_MS / ENTER_SECTION_STEP_MS) {
            withFrameNanos { }
            if (!drawerFocused) break
            if (focusManager.moveFocus(FocusDirection.Right)) break
            delay(ENTER_SECTION_STEP_MS)
        }
        pendingEnter = null
    }
    LaunchedEffect(previewTarget) {
        val target = previewTarget ?: return@LaunchedEffect
        delay(300)
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
            SideMenu(
                drawerValue = drawerValue,
                selected = selectedDestination,
                hidden = Routes.isPlayer(currentRoute),
                pageOpen = Destination.entries.none { it.route == currentRoute },
                itemFocus = itemFocus,
                onFocusInside = { drawerFocused = it },
                onItemFocused = { destination ->
                    // Only moving inside an already open menu previews a section; focus that merely
                    // falls into the menu (e.g. while a release page is still loading) must not.
                    val browsing = drawerState.currentValue == DrawerValue.Open
                    if (!browsing) lastMenuItem = null
                    // Focus that merely passes through the menu while screens swap (opening a release
                    // page) must not slide the menu open for a moment: only Left/Back open it.
                    if (browsing || menuRecentlyAsked() || drawerFocused) drawerState.setValue(DrawerValue.Open)
                    // Moving within the menu opens the section as a preview (not the item the
                    // menu was entered on: that one is the current screen, e.g. a release page).
                    if (browsing && lastMenuItem != null && lastMenuItem != destination) previewTarget = destination
                    lastMenuItem = destination
                    // Moving on to another item cancels a pending "go into the section".
                    if (pendingEnter != null && pendingEnter != destination) pendingEnter = null
                },
                onItemClick = { destination ->
                    if (currentRoute == destination.route) {
                        // The section already opened while the item was focused: step into it now, or
                        // as soon as it has something to focus if it is still loading.
                        if (!focusManager.moveFocus(FocusDirection.Right)) pendingEnter = destination
                    } else {
                        // Scrolled quickly: the section is not open yet. Stepping right now would land in
                        // the old screen, which then disappears and drops the focus back into the menu.
                        previewTarget = null
                        openSection(destination)
                        pendingEnter = destination
                    }
                },
            )
        },
    ) {
        // Focusable only while parking, so arrow keys never land on it.
        Box(Modifier.size(1.dp).focusRequester(focusAnchor)
            .focusProperties { canFocus = parking }
            .onFocusChanged { if (!it.isFocused) parking = false }
            .focusable())
        // Screens use this to send Left from their leftmost controls straight to the side menu.
        CompositionLocalProvider(LocalDrawerFocus provides itemFocus.getValue(selectedDestination)) {
            // One scope and one graph for the shell's lifetime: a new builder lambda on every
            // recomposition would make NavHost rebuild the whole graph.
            val route by rememberUpdatedState(currentRoute)
            val graphNow by rememberUpdatedState(graph)
            val scope = remember(navController) {
                ShellScope(
                    currentRoute = { route },
                    openPage = { target ->
                        parking = true
                        runCatching { focusAnchor.requestFocus() }
                        navController.navigate(target)
                    },
                    closeMenu = { drawerState.setValue(DrawerValue.Closed) },
                    focusMenu = {
                        DrawerBrowsing.lastMenuKeyAt = SystemClock.uptimeMillis()
                        itemFocus.getValue(Destination.Home).requestFocus()
                    },
                )
            }
            val builder: NavGraphBuilder.() -> Unit = remember(scope) { { graphNow(this, scope) } }
            // Pages (release, player) cross-fade quickly: the old screen stays focusable while it fades out.
            // Menu sections fade slower and eased: Home with its poster backdrop is about twice as bright
            // as Search or the catalog, and a quick fade between them read as a brightness jump.
            fun fadeMs(from: String?, to: String?) =
                if (from != null && to != null && Routes.section(from) != null && Routes.section(to) != null) SECTION_FADE_MS
                else PAGE_FADE_MS
            NavHost(navController = navController, startDestination = Destination.Home.route,
                enterTransition = { fadeIn(tween(fadeMs(initialState.destination.route, targetState.destination.route), easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(fadeMs(initialState.destination.route, targetState.destination.route), easing = FastOutSlowInEasing)) },
                popEnterTransition = { fadeIn(tween(fadeMs(initialState.destination.route, targetState.destination.route), easing = FastOutSlowInEasing)) },
                popExitTransition = { fadeOut(tween(fadeMs(initialState.destination.route, targetState.destination.route), easing = FastOutSlowInEasing)) },
                builder = builder)
        }
    }

    if (confirmExit) {
        AppDialog(onDismiss = { confirmExit = false }, title = stringResource(R.string.exit_question), width = 460.dp,
            actions = {
                DialogButton(stringResource(R.string.exit), onExit, initialFocus = true)
                DialogButton(stringResource(R.string.cancel), { confirmExit = false })
            })
    }
}
