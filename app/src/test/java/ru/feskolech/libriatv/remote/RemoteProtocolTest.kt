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
        for (index in 0 until 254) {
            assertFalse(gate.allow("peer-$index", "1234", "0000"))
            assertTrue(gate.allow("reset", "1234", "1234"))
        }
        // Accessing an entry moves it to the end of the LRU order.
        assertFalse(gate.allow("recent", "1234", "0000"))
        assertFalse(gate.allow("new", "1234", "0000"))
        assertFalse(gate.allow("old", "1234", "0000"))
        assertTrue(gate.allow("old", "1234", "1234"))
        assertFalse(gate.allow("recent", "1234", "1234"))
    }

    @Test fun globalLimitSurvivesAcrossPeersAndExpires() {
        var time = 1000L
        val gate = PinGate { time }
        repeat(30) { assertFalse(gate.allow("peer-$it", "1234", "0000")) }
        assertFalse(gate.allow("new", "1234", "1234"))
        time += 300_000
        assertTrue(gate.allow("new", "1234", "1234"))
    }

    @Test fun tokenIsRandomAndExact() {
        val first = newRemoteToken()
        val second = newRemoteToken()
        assertEquals(22, first.length)
        assertNotEquals(first, second)
        assertTrue(validRemoteToken(first, first))
        assertFalse(validRemoteToken(first, second))
        assertFalse(validRemoteToken(first, null))
    }

    @Test fun addressesAndHostsAreRestricted() {
        for (address in listOf("127.0.0.1", "192.168.1.2", "169.254.1.2", "::1", "fe80::1", "fc00::1", "fd12::1"))
            assertTrue(address, allowedRemoteAddress(address))
        for (address in listOf("8.8.8.8", "2001:4860:4860::8888", "example.com", ""))
            assertFalse(address, allowedRemoteAddress(address))
        assertTrue(validRemoteHost("192.168.1.2:8765", 8765))
        assertTrue(validRemoteHost("[fd00::1]:8765", 8765))
        for (host in listOf("evil.test:8765", "192.168.1.2:1234", "192.168.1.2", "192.168.1.2:8765.evil.test"))
            assertFalse(host, validRemoteHost(host, 8765))
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
