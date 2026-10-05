package ru.feskolech.libriatv.ui.torrents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/** Packages tried first, in order: TorrServe builds seen on TV boxes. */
private val TORRSERVE_PACKAGES = listOf("ru.yourok.torrserve", "ru.yourok.torrserve.server")

/**
 * Hands a magnet link to TorrServe if it is installed, otherwise to any app that handles magnets.
 * Returns false when nothing on the device can open it (the caller then shows the link as a QR code).
 */
fun openMagnet(context: Context, magnet: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(magnet)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (pkg in TORRSERVE_PACKAGES) {
        try {
            context.startActivity(Intent(view).setPackage(pkg))
            return true
        } catch (_: ActivityNotFoundException) {
            // try the next candidate
        }
    }
    // Needs the magnet <queries> entry in the manifest to see handlers on Android 11+.
    if (context.packageManager.queryIntentActivities(view, 0).isEmpty()) return false
    return try {
        context.startActivity(Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

fun openTorrServeStream(context: Context, url: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (pkg in TORRSERVE_PACKAGES) {
        try {
            context.startActivity(Intent(view).setPackage(pkg))
            return true
        } catch (_: ActivityNotFoundException) { }
    }
    return try {
        context.startActivity(Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) { false }
}

/** Short magnet (info-hash only) — fits a QR code that a phone camera can still read from the sofa. */
fun shortMagnet(magnet: String): String =
    Regex("xt=urn:btih:[0-9A-Za-z]+").find(magnet)?.let { "magnet:?${it.value}" } ?: magnet

private val RU_UNITS = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
private val EN_UNITS = listOf("B", "KB", "MB", "GB", "TB")

fun formatSize(bytes: Long?, locale: Locale = Locale.getDefault()): String? {
    if (bytes == null || bytes <= 0) return null
    val units = if (locale.language == "ru") RU_UNITS else EN_UNITS
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return if (unit <= 1) "${value.toLong()} ${units[unit]}" else String.format(Locale.US, "%.1f %s", value, units[unit])
}
