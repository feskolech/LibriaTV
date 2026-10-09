package ru.feskolech.libriatv.data.repo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Every few hours, compares favorites with the episode numbers seen at the last app launch and
 * publishes the difference to the "new episodes" home-screen channel. It never moves that baseline
 * itself, so the on-launch notice still lists the same episodes; opening the app clears the channel.
 */
class NewEpisodesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun auth(): AuthRepository
        fun favorites(): FavoritesRepository
        fun store(): FavoriteEpisodeStore
        fun channel(): NewEpisodesChannel
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        // Offline: try later, keeping the channel as it is (it used to be wiped on any network error).
        val userId = try { deps.auth().currentUserIdForBackground() } catch (_: java.io.IOException) { return Result.retry() }
        if (userId == null) {
            deps.channel().publish(emptyList())
            return Result.success()
        }
        val favorites = deps.favorites().allReleases() as? ApiResult.Success ?: return Result.retry()
        deps.channel().publish(compareFavoriteEpisodes(deps.store().previous(userId), favorites.value))
        return Result.success()
    }

    companion object {
        private const val NAME = "new-episodes-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewEpisodesWorker>(4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
