package ru.feskolech.libriatv.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import ru.feskolech.libriatv.R

/** Side-menu sections, in menu order. */
internal enum class Destination(val route: String, val title: Int, val icon: ImageVector) {
    Home("home", R.string.home, Icons.Default.Home),
    Search("search", R.string.search, Icons.Default.Search),
    Catalog("catalog", R.string.catalog, Icons.Default.GridView),
    Favorites("favorites", R.string.favorites, Icons.Default.Favorite),
    Schedule("schedule", R.string.schedule, Icons.Default.DateRange),
    Profile("profile", R.string.profile, Icons.Default.Person),
    Settings("settings", R.string.settings, Icons.Default.Settings),
}

/**
 * Every route of the app in one place: the patterns the graph registers and the builders screens use.
 * Argument names here are the SavedStateHandle keys the view models read.
 */
internal object Routes {
    const val SEARCH_QUERY = "search?query={query}"
    const val FEED = "feed"
    const val RELEASE = "release/{id}"
    const val TORRENTS = "torrents/{releaseId}"
    const val PLAYER = "player/{id}/{episodeId}"

    fun release(id: Int) = "release/$id"
    fun torrents(releaseId: Int) = "torrents/$releaseId"
    fun player(releaseId: Int, episodeId: String) = "player/$releaseId/$episodeId"
    fun search(query: String) = "search?query=${Uri.encode(query)}"

    fun isPlayer(route: String) = route.startsWith("player/")

    /** The menu section a route belongs to, or null for pages opened from a section (release, player…). */
    fun section(route: String): Destination? =
        Destination.entries.firstOrNull { it.route == route } ?: if (route.startsWith("search?")) Destination.Search else null

    private val EPISODE_ID = Regex("[A-Za-z0-9_-]{1,80}")

    /**
     * Route for a `libriatv://play/<release>/<episode>` or `libriatv://release/<id>` link, or null.
     * Any installed app may send these intents: only well-formed ids pass, so a crafted segment
     * (e.g. "a%2Fb") cannot turn into an unknown route and crash the app.
     */
    fun fromDeepLink(scheme: String?, host: String?, segments: List<String>): String? {
        if (scheme != "libriatv") return null
        val releaseId = segments.firstOrNull()?.toIntOrNull() ?: return null
        return when {
            host == "play" && segments.size == 2 && segments[1].matches(EPISODE_ID) -> player(releaseId, segments[1])
            host == "release" -> release(releaseId)
            else -> null
        }
    }
}
