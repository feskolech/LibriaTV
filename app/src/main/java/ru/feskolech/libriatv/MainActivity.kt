package ru.feskolech.libriatv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
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
import ru.feskolech.libriatv.ui.auth.AuthScreen
import ru.feskolech.libriatv.ui.home.HomeScreen
import ru.feskolech.libriatv.ui.home.FeedScreen
import ru.feskolech.libriatv.ui.release.ReleaseScreen
import ru.feskolech.libriatv.ui.player.PlayerScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LibriaTvTheme {
                AppNavigation(onExit = { finish() })
            }
        }
    }
}

private enum class Destination(val route: String, val title: Int, val icon: ImageVector) {
    Home("home", R.string.home, Icons.Default.Home),
    Search("search", R.string.search, Icons.Default.Search),
    Favorites("favorites", R.string.favorites, Icons.Default.Favorite),
    Schedule("schedule", R.string.schedule, Icons.Default.DateRange),
    Profile("profile", R.string.profile, Icons.Default.Person),
    Settings("settings", R.string.settings, Icons.Default.Settings),
}

@Composable
private fun AppNavigation(onExit: () -> Unit) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Destination.Home.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val firstItemFocus = remember { FocusRequester() }
    var confirmExit by remember { mutableStateOf(false) }

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
            Column(
                modifier = Modifier
                    .then(
                        when {
                            currentRoute.startsWith("player/") -> Modifier.width(0.dp)
                            drawerValue == DrawerValue.Open -> Modifier.width(230.dp)
                            // Collapsed: wrap the icon items so the background covers the whole drawer slot.
                            else -> Modifier.width(IntrinsicSize.Max)
                        }
                    )
                    .fillMaxHeight()
                    .background(Color(0xFF181818))
                    .padding(horizontal = 8.dp, vertical = 27.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Destination.entries.forEachIndexed { index, destination ->
                    NavigationDrawerItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            drawerState.setValue(DrawerValue.Closed)
                        },
                        leadingContent = { Icon(destination.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        modifier = (if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                            .then(if (drawerValue == DrawerValue.Open) Modifier else Modifier.width(72.dp))
                            .onFocusChanged { if (it.isFocused) drawerState.setValue(DrawerValue.Open) },
                    ) {
                        if (drawerValue == DrawerValue.Open) Text(stringResource(destination.title))
                    }
                }
            }
        },
    ) {
        NavHost(navController = navController, startDestination = Destination.Home.route) {
            Destination.entries.forEach { destination ->
                composable(destination.route) {
                    if (destination == Destination.Profile) {
                        AuthScreen(
                            onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                            onOpenMenu = { firstItemFocus.requestFocus() },
                        )
                    } else if (destination == Destination.Home) {
                        HomeScreen(
                            onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                            onOpenFeed = { navController.navigate("feed") },
                            onOpenRelease = { navController.navigate("release/$it") },
                        )
                    } else {
                        PlaceholderScreen(destination.title,
                            onContentFocus = { drawerState.setValue(DrawerValue.Closed) },
                            onOpenMenu = { firstItemFocus.requestFocus() })
                    }
                }
            }
            composable("feed") {
                FeedScreen(onOpenRelease = { navController.navigate("release/$it") },
                    onContentFocus = { drawerState.setValue(DrawerValue.Closed) })
            }
            composable("release/{id}") { entry ->
                ReleaseScreen(onPlay = { navController.navigate("player/${entry.arguments?.getString("id")}/$it") })
            }
            composable("player/{id}/{episodeId}") {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    if (confirmExit) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmExit = false }) {
            androidx.tv.material3.Surface {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(stringResource(R.string.exit_question), style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = onExit) { Text(stringResource(R.string.exit)) }
                    Button(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) }
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
