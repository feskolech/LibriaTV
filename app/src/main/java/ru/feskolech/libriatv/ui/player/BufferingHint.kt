package ru.feskolech.libriatv.ui.player

/** Counts completed stalls, excluding startup and user-initiated seeks. */
internal class BufferingHint {
    private val stalls = ArrayDeque<Long>()
    private var startedAt: Long? = null
    var dismissed = false
        private set

    fun start(now: Long) { if (startedAt == null) startedAt = now }

    fun finish(now: Long, quality: Int, lowerQualityAvailable: Boolean): Boolean {
        val start = startedAt ?: return false
        startedAt = null
        if (now - start <= 2_000) return false
        stalls.addLast(now)
        while (stalls.isNotEmpty() && now - stalls.first() > 120_000) stalls.removeFirst()
        return !dismissed && lowerQualityAvailable && quality > 480 && stalls.size >= 3
    }

    fun clear() { stalls.clear(); startedAt = null; dismissed = false }
    fun interrupt() { startedAt = null }
    fun dismiss() { dismissed = true }
}
