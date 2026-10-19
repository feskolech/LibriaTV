package ru.feskolech.libriatv.data.repo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Where the store build sends the viewer for an update: the page of the store the app was installed from.
 * Android records the installer's package; stores that install through the system installer leave it empty,
 * and then the generic market:// link (understood by most stores) is tried, and GitHub last.
 */
internal object StoreLink {
    /** Store pages to try, in order, for the store with this installer package. */
    fun candidates(installer: String?, packageName: String, githubUrl: String): List<String> {
        val market = "market://details?id=$packageName"
        val store = when (installer) {
            "ru.vk.store" -> listOf("rustore://apps.rustore.ru/app/$packageName",
                "https://www.rustore.ru/catalog/app/$packageName")
            "com.huawei.appmarket" -> listOf("appmarket://details?id=$packageName",
                "https://appgallery.huawei.com/app/detail?id=$packageName")
            "com.android.vending" -> listOf(market, "https://play.google.com/store/apps/details?id=$packageName")
            "com.amazon.venezia" -> listOf("amzn://apps/android?p=$packageName")
            else -> emptyList()
        }
        return (store + market + githubUrl).distinct()
    }

    /** The package that installed this app, or null when unknown (installed by hand, or the store did not say). */
    fun installer(context: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        else @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(context.packageName)
    }.getOrNull()

    /** Opens the first page that some app can show; false when none could be opened. */
    fun open(context: Context, githubUrl: String): Boolean =
        candidates(installer(context), context.packageName, githubUrl).any { link ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
}
