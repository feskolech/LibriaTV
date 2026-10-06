package ru.feskolech.libriatv.data.repo

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

    fun publish(items: List<NewFavoriteEpisode>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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

    private fun channelId(): Long? {
        val saved = prefs.getLong(KEY, -1L)
        if (saved >= 0 && runCatching { helper.getPreviewChannel(saved) }.getOrNull() != null) return saved
        val logo = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(160, 160) ?: return null
        val id = runCatching {
            helper.publishChannel(PreviewChannel.Builder()
                .setDisplayName(context.getString(R.string.channel_new_episodes))
                .setAppLinkIntentUri(Uri.parse("libriatv://home"))
                .setLogo(logo)
                .build())
        }.getOrNull() ?: return null
        prefs.edit().putLong(KEY, id).apply()
        // The first channel of an app may be shown right away; later ones need the user's OK.
        runCatching { TvContractCompat.requestChannelBrowsable(context, id) }
        return id
    }

    private companion object {
        const val KEY = "new_episodes_channel"
        const val MAX_PROGRAMS = 20
    }
}
