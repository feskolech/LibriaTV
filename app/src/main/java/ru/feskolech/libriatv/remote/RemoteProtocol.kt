package ru.feskolech.libriatv.remote


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
    private val failures = mutableMapOf<String, Failure>()

    @Synchronized fun allow(address: String, expected: String, supplied: String?): Boolean {
        val entry = failures.getOrPut(address) { Failure() }
        val time = now()
        if (entry.blockedUntil > time) return false
        if (entry.blockedUntil != 0L) { entry.count = 0; entry.blockedUntil = 0 }
        var difference = expected.length xor (supplied?.length ?: 0)
        for (index in 0 until 4) {
            difference = difference or (expected[index].code xor (supplied?.getOrNull(index)?.code ?: 0))
        }
        val valid = difference == 0
        if (valid) { failures.remove(address); return true }
        entry.count++
        if (entry.count >= 10) entry.blockedUntil = time + 300_000
        return false
    }
}
