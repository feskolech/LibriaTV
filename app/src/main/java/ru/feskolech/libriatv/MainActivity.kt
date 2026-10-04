package ru.feskolech.libriatv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.rememberDrawerState
import dagger.hilt.android.AndroidEntryPoint
import ru.feskolech.libriatv.ui.theme.LibriaTvTheme

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

private enum class Destination(val route: String, val title: Int, val marker: Int) {
    Home("home", R.string.home, R.string.menu_marker_home),
    Search("search", R.string.search, R.string.menu_marker_search),
    Favorites("favorites", R.string.favorites, R.string.menu_marker_favorites),
    Schedule("schedule", R.string.schedule, R.string.menu_marker_schedule),
    Profile("profile", R.string.profile, R.string.menu_marker_profile),
    Settings("settings", R.string.settings, R.string.menu_marker_settings),
}

@Composable
private fun AppNavigation(onExit: () -> Unit) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Destination.Home.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val firstItemFocus = remember { FocusRequester() }
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { firstItemFocus.requestFocus() }
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
                    .width(if (drawerValue == DrawerValue.Open) 230.dp else 72.dp)
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
                        leadingContent = { Text(stringResource(destination.marker)) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
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
                    PlaceholderScreen(destination.title)
                }
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
private fun PlaceholderScreen(title: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
