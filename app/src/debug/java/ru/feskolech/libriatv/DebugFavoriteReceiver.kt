package ru.feskolech.libriatv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import ru.feskolech.libriatv.data.repo.AuthRepository
import ru.feskolech.libriatv.data.repo.AuthState
import ru.feskolech.libriatv.data.repo.FavoriteEpisodeStore
import ru.feskolech.libriatv.data.repo.FavoritesRepository
import ru.feskolech.libriatv.data.repo.ApiResult

/** Debug-only emulator hook: adb broadcast lowers one saved episode ordinal for the signed-in user. */
@AndroidEntryPoint
class DebugFavoriteReceiver : BroadcastReceiver() {
    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var store: FavoriteEpisodeStore
    @Inject lateinit var favorites: FavoritesRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = auth.state.first { it !is AuthState.Loading } as? AuthState.Authorized
                if (intent.action == "ru.feskolech.libriatv.debug.VERIFY_FAVORITES") {
                    Log.i("DebugFavoriteReceiver", "get_favorites_ids=" + favorites.refreshIds())
                    val releases = favorites.allReleases()
                    Log.i("DebugFavoriteReceiver", "favorite_episodes=" +
                        (releases as? ApiResult.Success)?.value?.map { release ->
                            "${release.id}: latest=${release.latestEpisode?.ordinal}, episodes=${release.episodes.size}, max=${release.episodes.mapNotNull { it.ordinal }.maxOrNull()}"
                        })
                } else if (intent.action == "ru.feskolech.libriatv.debug.LOWER_FAVORITE_ORDINAL") {
                    Log.i("DebugFavoriteReceiver", "lowered=" + (user?.let { store.lowerFirstSavedOrdinal(it.user.id) } ?: false))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
