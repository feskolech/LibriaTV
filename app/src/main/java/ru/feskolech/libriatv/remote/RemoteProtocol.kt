package ru.feskolech.libriatv.remote

import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal fun newRemoteToken(): String = ByteArray(16).also(SecureRandom()::nextBytes)
    .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

internal fun validRemoteToken(expected: String, supplied: String?): Boolean =
    supplied != null && MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), supplied.toByteArray(Charsets.US_ASCII))

internal fun allowedRemoteAddress(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val ipv4 = raw.matches(Regex("[0-9.]+"))
    val ipv6 = raw.contains(':') && raw.matches(Regex("[0-9a-fA-F:.%]+"))
    if (!ipv4 && !ipv6) return false
    return runCatching {
        val address = InetAddress.getByName(raw)
        address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
            (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)
    }.getOrDefault(false)
}

internal fun validRemoteHost(host: String?, port: Int): Boolean {
    if (host == null) return false
    val match = Regex("^(?:([0-9.]+)|\\[([0-9a-fA-F:.%]+)])\\:([0-9]+)$").matchEntire(host) ?: return false
    if (match.groupValues[3].toIntOrNull() != port) return false
    return allowedRemoteAddress(match.groupValues[1].ifEmpty { match.groupValues[2] })
}

sealed interface RemoteCommand {
    data class OpenRelease(val id: Int) : RemoteCommand
    data class OpenEpisode(val releaseId: Int, val episodeId: String) : RemoteCommand
    data object Pause : RemoteCommand
    data object SeekBack : RemoteCommand
    data object SeekForward : RemoteCommand
    data object NextEpisode : RemoteCommand
}

/** A small, testable routing boundary: only explicit commands may reach navigation or the player. */
internal fun routeCommand(path: String, parameters: Map<String, String>): RemoteCommand? {
    val id = parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
    return when (path) {
        "/api/open/release" -> id?.let(RemoteCommand::OpenRelease)
        "/api/open/episode" -> {
            val episode = parameters["episode"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
            if (id != null && episode != null) RemoteCommand.OpenEpisode(id, episode) else null
        }
        "/api/remote/pause" -> RemoteCommand.Pause
        "/api/remote/back" -> RemoteCommand.SeekBack
        "/api/remote/forward" -> RemoteCommand.SeekForward
        "/api/remote/next" -> RemoteCommand.NextEpisode
        else -> null
    }
}

/** Failed attempts are counted per peer; the tenth failure starts a five minute lockout. */
internal class PinGate(private val now: () -> Long = System::currentTimeMillis) {
    private data class Failure(var count: Int = 0, var blockedUntil: Long = 0)
    private val failures = object : LinkedHashMap<String, Failure>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Failure>): Boolean = size > 256
    }
    private var globalFailures = 0
    private var globalBlockedUntil = 0L

    @Synchronized fun allow(address: String, expected: String, supplied: String?): Boolean {
        val time = now()
        if (globalBlockedUntil > time) return false
        if (globalBlockedUntil != 0L) { globalFailures = 0; globalBlockedUntil = 0L }
        val entry = failures.getOrPut(address) { Failure() }
        if (entry.blockedUntil > time) return false
        if (entry.blockedUntil != 0L) { entry.count = 0; entry.blockedUntil = 0 }
        var difference = expected.length xor (supplied?.length ?: 0)
        for (index in 0 until 4) {
            difference = difference or (expected[index].code xor (supplied?.getOrNull(index)?.code ?: 0))
        }
        val valid = difference == 0
        if (valid) { failures.remove(address); globalFailures = 0; return true }
        entry.count++
        if (entry.count >= 10) entry.blockedUntil = time + 300_000
        globalFailures++
        if (globalFailures >= 30) globalBlockedUntil = time + 300_000
        return false
    }
}
