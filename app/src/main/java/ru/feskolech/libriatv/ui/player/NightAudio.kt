package ru.feskolech.libriatv.ui.player

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Log

/**
 * "Night mode": lift the overall level and squash peaks with a limiter, so dialogue stays audible
 * while explosions do not wake the neighbours. Needs DynamicsProcessing (Android 9+); a no-op below.
 */
class NightAudio {
    private var effect: Any? = null
    private var sessionId = 0

    fun apply(audioSessionId: Int, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || audioSessionId == 0) return
        if (audioSessionId != sessionId) {
            release()
            sessionId = audioSessionId
        }
        runCatching { if (enabled) enable() else (effect as? DynamicsProcessing)?.enabled = false }
            .onFailure { Log.w("NightAudio", "Night mode audio effect unavailable", it) }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun enable() {
        val dp = (effect as? DynamicsProcessing) ?: run {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, CHANNELS,
                false, 0, false, 0, false, 0, true,
            ).build()
            DynamicsProcessing(0, sessionId, config).also { effect = it }
        }
        dp.setInputGainAllChannelsTo(INPUT_GAIN_DB)
        dp.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, true, 0, ATTACK_MS, RELEASE_MS, RATIO, THRESHOLD_DB, 0f),
        )
        dp.enabled = true
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { (effect as? DynamicsProcessing)?.release() }
        effect = null
    }

    private companion object {
        const val CHANNELS = 2
        const val INPUT_GAIN_DB = 9f
        const val THRESHOLD_DB = -18f
        const val RATIO = 10f
        const val ATTACK_MS = 1f
        const val RELEASE_MS = 80f
    }
}
