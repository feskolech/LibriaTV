package ru.feskolech.libriatv.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteProtocolTest {
    @Test fun pinIsRequiredAndExact() {
        val gate = PinGate()
        assertFalse(gate.allow("phone", "0042", null))
        assertFalse(gate.allow("phone", "0042", "42"))
        assertFalse(gate.allow("phone", "0042", "0043"))
        assertTrue(gate.allow("phone", "0042", "0042"))
    }

    @Test fun tenFailuresBlockOnlyThatAddressForFiveMinutes() {
        var time = 1_000L
        val gate = PinGate { time }
        repeat(10) { assertFalse(gate.allow("bad", "1234", "0000")) }
        assertFalse(gate.allow("bad", "1234", "1234"))
        assertTrue(gate.allow("other", "1234", "1234"))
        time += 299_999
        assertFalse(gate.allow("bad", "1234", "1234"))
        time++
        assertTrue(gate.allow("bad", "1234", "1234"))
    }

    @Test fun failureTableEvictsLeastRecentlyUsedAddress() {
        val gate = PinGate()
        repeat(9) { assertFalse(gate.allow("old", "1234", "0000")) }
        repeat(9) { assertFalse(gate.allow("recent", "1234", "0000")) }
        for (index in 0 until 254) assertFalse(gate.allow("peer-$index", "1234", "0000"))
        // Accessing an entry moves it to the end of the LRU order.
        assertFalse(gate.allow("recent", "1234", "0000"))
        assertFalse(gate.allow("new", "1234", "0000"))
        assertFalse(gate.allow("old", "1234", "0000"))
        assertTrue(gate.allow("old", "1234", "1234"))
        assertFalse(gate.allow("recent", "1234", "1234"))
    }

    @Test fun routesOnlyKnownCommandsWithValidIds() {
        assertEquals(RemoteCommand.OpenRelease(7), routeCommand("/api/open/release", mapOf("id" to "7")))
        assertEquals(RemoteCommand.OpenEpisode(7, "ep-9"), routeCommand("/api/open/episode", mapOf("id" to "7", "episode" to "ep-9")))
        assertEquals(RemoteCommand.Pause, routeCommand("/api/remote/pause", emptyMap()))
        assertEquals(RemoteCommand.SeekBack, routeCommand("/api/remote/back", emptyMap()))
        assertEquals(RemoteCommand.SeekForward, routeCommand("/api/remote/forward", emptyMap()))
        assertEquals(RemoteCommand.NextEpisode, routeCommand("/api/remote/next", emptyMap()))
        assertNull(routeCommand("/api/open/episode", mapOf("id" to "7", "episode" to "../other")))
        assertNull(routeCommand("/api/open/release", mapOf("id" to "-1")))
        assertNull(routeCommand("/api/remote/stop", emptyMap()))
    }
}
