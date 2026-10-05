package ru.feskolech.libriatv.data.api

import android.content.Context
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import okhttp3.Dns

/**
 * System DNS with a deadline. TV boxes waking from standby were seen spending ~15 s on the first
 * lookup, which froze the whole home screen. If the system resolver is slower than [timeoutMs],
 * the last addresses that worked for that host (persisted) are used instead; the system answer
 * still refreshes the cache whenever it arrives in time.
 */
class FallbackDns(context: Context, private val timeoutMs: Long = 3_000) : Dns {
    private val prefs = context.getSharedPreferences("dns_cache", Context.MODE_PRIVATE)
    private val executor = Executors.newCachedThreadPool { r -> Thread(r, "dns").apply { isDaemon = true } }

    override fun lookup(hostname: String): List<InetAddress> {
        val future = executor.submit<List<InetAddress>> { Dns.SYSTEM.lookup(hostname) }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS).also { remember(hostname, it) }
        } catch (e: TimeoutException) {
            cached(hostname) ?: future.get() // nothing cached yet: keep waiting for the system
        } catch (e: java.util.concurrent.ExecutionException) {
            cached(hostname) ?: throw (e.cause as? UnknownHostException ?: UnknownHostException(hostname))
        }
    }

    private fun remember(host: String, addresses: List<InetAddress>) {
        val ips = addresses.mapNotNull { it.hostAddress }.joinToString(",")
        if (ips.isNotEmpty()) prefs.edit().putString(host, ips).apply()
    }

    private fun cached(host: String): List<InetAddress>? =
        prefs.getString(host, null)?.split(',')?.filter { it.isNotBlank() }
            ?.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() } // literal IPs: no lookup
            ?.takeIf { it.isNotEmpty() }
            ?.also { android.util.Log.w("LibriaNet", "DNS slow for $host, using cached addresses") }
}
