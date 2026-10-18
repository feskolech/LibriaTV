package ru.feskolech.libriatv.data.repo

import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import ru.feskolech.libriatv.R

/**
 * "LibriaTV: new episodes in favorites" row on the Android TV home screen (a preview channel).
 * Programs open the release card through the libriatv://release/<id> deep link.
 * Launchers without channel support (Fire TV, old boxes) simply ignore it.
 */
@Singleton
class NewEpisodesChannel @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("tv_channel", Context.MODE_PRIVATE)
    private val helper by lazy { PreviewChannelHelper(context) }

    private val lock = kotlinx.coroutines.sync.Mutex()

    /** Content-provider IPC: runs on IO; serialized because the worker and Home may publish at once. */
    suspend fun publish(items: List<NewFavoriteEpisode>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext
        lock.withLock { publishLocked(items) }
    }

    private fun publishLocked(items: List<NewFavoriteEpisode>) {
        runCatching {
            val channelId = channelId() ?: return
            context.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null)
            Log.i("LibriaTV", "New episodes channel $channelId: ${items.size} item(s)")
            items.take(MAX_PROGRAMS).forEachIndexed { index, item ->
                val episode = if (item.from == item.to) context.getString(R.string.episode_number, item.to)
                    else context.getString(R.string.episodes_range, item.from, item.to)
                helper.publishPreviewProgram(PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_TV_SERIES)
                    .setTitle(item.release.title)
                    .setDescription(episode)
                    .setPosterArtUri(item.release.posterUrl?.let(Uri::parse))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                    .setIntentUri(Uri.Builder().scheme("libriatv").authority("release").appendPath(item.release.id.toString()).build())
                    .setInternalProviderId(item.release.id.toString())
                    .setWeight(MAX_PROGRAMS - index)
                    .build())
            }
        }.onFailure { Log.w("LibriaTV", "New episodes channel update failed", it) }
    }

    /**
     * The app's one channel. The channel was published without a description, and the library's reader
     * (PreviewChannel.fromCursor) throws on that, so every lookup by id failed and a new channel was published on
     * each update: TVs collected dozens of identical "new episodes" rows. Channel ids are now read straight from
     * the provider, extra copies are deleted, and a new channel is published only when the list was read and
     * holds none (an unreadable list publishes nothing).
     */
    private fun channelId(): Long? {
        val saved = prefs.getLong(KEY, -1L)
        val name = context.getString(R.string.channel_new_episodes)
        // Only this app's channels are listed, and it has no other: every one of them is a copy of this row.
        val ours = runCatching { channelIds() }
            .onFailure { Log.w("LibriaTV", "Cannot read the app's TV channels", it) }
            .getOrNull() ?: return saved.takeIf { it >= 0 }
        val keep = ours.firstOrNull { it == saved } ?: ours.firstOrNull()
        ours.filter { it != keep }.forEach { extra -> runCatching { helper.deletePreviewChannel(extra) } }
        if (ours.size > 1) Log.i("LibriaTV", "Removed ${ours.size - 1} duplicate new-episodes channel(s)")
        if (keep != null) {
            if (keep != saved) prefs.edit().putLong(KEY, keep).apply()
            return keep
        }
        val logo = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(160, 160) ?: return null
        val id = runCatching {
            helper.publishChannel(PreviewChannel.Builder()
                .setDisplayName(name)
                .setDescription(name)
                .setInternalProviderId(PROVIDER_ID)
                .setAppLinkIntentUri(Uri.parse("libriatv://home"))
                .setLogo(logo)
                .build())
        }.getOrNull() ?: return null
        prefs.edit().putLong(KEY, id).apply()
        // The first channel of an app may be shown right away; later ones need the user's OK.
        runCatching { TvContractCompat.requestChannelBrowsable(context, id) }
        return id
    }

    /** Ids of this app's channels (the provider lists only the caller's own), oldest first. */
    private fun channelIds(): List<Long> =
        context.contentResolver.query(TvContractCompat.Channels.CONTENT_URI, arrayOf(TvContractCompat.Channels._ID),
            null, null, TvContractCompat.Channels._ID)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        } ?: error("No cursor")

    private companion object {
        const val KEY = "new_episodes_channel"
        const val PROVIDER_ID = "new_episodes"
        const val MAX_PROGRAMS = 20
    }
}
