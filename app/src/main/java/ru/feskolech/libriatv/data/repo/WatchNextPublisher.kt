package ru.feskolech.libriatv.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release

class WatchNextPublisher @Inject constructor(@ApplicationContext private val context: Context) {
    fun update(release: Release, episode: Episode, progress: PlaybackProgress?) {
        if (android.os.Build.VERSION.SDK_INT < 26 || progress == null) return
        val resolver = context.contentResolver
        val key = "${release.id}/${episode.id}"
        try {
            val existing = resolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(TvContractCompat.WatchNextPrograms._ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
                null, null, null,
            )?.use { cursor ->
                var found: Long? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == key) { found = cursor.getLong(0); break }
                }
                found
            }
            val uri = existing?.let { android.content.ContentUris.withAppendedId(TvContractCompat.WatchNextPrograms.CONTENT_URI, it) }
            if (progress.watched || progress.positionMs <= 0) {
                if (uri != null) resolver.delete(uri, null, null)
                return
            }
            val intentUri = Uri.Builder().scheme("libriatv").authority("play")
                .appendPath(release.id.toString()).appendPath(episode.id).build()
            val program = WatchNextProgram.Builder()
                .setType(TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setInternalProviderId(key)
                .setTitle(release.title)
                .setEpisodeTitle(episode.name)
                .setIntentUri(intentUri)
                .setLastPlaybackPositionMillis(progress.positionMs.toInt())
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setDurationMillis(progress.durationMs.toInt())
                .apply { (episode.previewUrl ?: release.posterUrl)?.let { setPosterArtUri(Uri.parse(it)) } }
                .build()
            if (uri == null) Log.i("WatchNextPublisher", "Inserted ${resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())}")
            else Log.i("WatchNextPublisher", "Updated $uri: ${resolver.update(uri, program.toContentValues(), null, null)}")
        } catch (error: Exception) {
            Log.w("WatchNextPublisher", "Unable to update Watch Next", error)
        }
    }
}
