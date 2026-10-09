package ru.feskolech.libriatv.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.data.repo.SettingsStore
import ru.feskolech.libriatv.domain.Release

data class PhoneRemoteState(val enabled: Boolean = false, val url: String? = null, val pin: String? = null, val error: Boolean = false)

@Singleton
class PhoneRemote @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val repository: ApiRepository,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(PhoneRemoteState())
    val state = _state.asStateFlow()
    private val _commands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 32)
    val commands = _commands.asSharedFlow()
    private var foreground = false
    private var enabled = false
    private var pin: String? = null
    private var server: RemoteServer? = null
    private val gate = PinGate()
    /** The phone page is static: read from assets once, not on every request. */
    private val remotePage by lazy { context.assets.open("remote.html").bufferedReader(Charsets.UTF_8).use { it.readText() } }

    suspend fun foreground(value: Boolean) = mutex.withLock {
        foreground = value
        if (value) enabled = settings.phoneRemoteEnabled()
        reconcile()
    }

    suspend fun setEnabled(value: Boolean) = mutex.withLock {
        if (value && !enabled) pin = "%04d".format(java.util.Locale.US, SecureRandom().nextInt(10_000))
        enabled = value
        settings.setPhoneRemoteEnabled(value)
        reconcile()
    }

    private suspend fun reconcile() {
        if (!foreground || !enabled) {
            withContext(Dispatchers.IO) { server?.stop() }
            server = null
            _state.value = PhoneRemoteState(enabled = enabled)
            return
        }
        if (pin == null) pin = "%04d".format(java.util.Locale.US, SecureRandom().nextInt(10_000))
        if (server == null) {
            server = withContext(Dispatchers.IO) {
                try {
                    RemoteServer(8765, pin!!, newRemoteToken()).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
                } catch (_: Exception) {
                    try { RemoteServer(0, pin!!, newRemoteToken()).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) } }
                    catch (_: Exception) { null }
                }
            }
        }
        val ip = localAddress()
        _state.value = PhoneRemoteState(enabled = true,
            url = if (server != null && ip != null) "http://$ip:${server!!.listeningPort}/?token=${server!!.token}" else null,
            pin = pin,
            error = server == null || ip == null)
    }

    private fun localAddress(): String? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = listOfNotNull(manager.activeNetwork) + manager.allNetworks.filter { it != manager.activeNetwork }
        networks.forEach { network ->
            manager.getLinkProperties(network)?.linkAddresses?.map { it.address }?.firstOrNull {
                it is Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress
            }?.let { return it.hostAddress }
        }
        @Suppress("DEPRECATION")
        val address = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.connectionInfo?.ipAddress ?: 0
        return if (address == 0) null else (0..3).joinToString(".") { ((address ushr (it * 8)) and 255).toString() }
    }

    private inner class RemoteServer(port: Int, private val secret: String, val token: String) : NanoHTTPD(port) {
        init {
            setAsyncRunner(object : AsyncRunner {
                private val handlers = java.util.concurrent.ConcurrentHashMap.newKeySet<ClientHandler>()
                private val pool = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                    ArrayBlockingQueue(16))
                override fun exec(handler: ClientHandler) {
                    handlers.add(handler)
                    try { pool.execute(handler) } catch (_: java.util.concurrent.RejectedExecutionException) {
                        handlers.remove(handler)
                        handler.close()
                    }
                }
                override fun closed(handler: ClientHandler) { handlers.remove(handler) }
                override fun closeAll() {
                    handlers.forEach { it.close() }
                    pool.shutdownNow()
                }
            })
        }

        override fun serve(session: IHTTPSession): Response {
            if (!allowedRemoteAddress(session.remoteIpAddress) ||
                !validRemoteHost(session.headers["host"], listeningPort)) {
                return reply(Response.Status.FORBIDDEN, "text/plain", "Forbidden")
            }
            val parameters = session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
            if (!validRemoteToken(token, parameters["token"]) &&
                !gate.allow(session.remoteIpAddress ?: "unknown", secret, parameters["pin"])) {
                return reply(Response.Status.FORBIDDEN, "text/plain", "Forbidden")
            }
            val path = session.uri
            if (session.method == Method.GET && path == "/") {
                val english = session.headers["accept-language"]?.trim()?.startsWith("en", true) == true
                val html = remotePage
                    .replace("<html lang=\"ru\">", if (english) "<html lang=\"en\">" else "<html lang=\"ru\">")
                return reply(Response.Status.OK, "text/html; charset=utf-8", html)
            }
            if (session.method == Method.GET && path == "/api/search") {
                val query = parameters["q"]?.trim().orEmpty()
                if (query.isEmpty() || query.length > 100) return reply(Response.Status.BAD_REQUEST, "application/json", "{}")
                return when (val result = runBlocking { withTimeoutOrNull(REQUEST_TIMEOUT_MS) { repository.search(query) } }) {
                    null -> reply(Response.Status.INTERNAL_ERROR, "application/json", "{}")
                    is ApiResult.Success -> {
                        val data = JsonArray(result.value.take(30).map(::releaseJson))
                        reply(Response.Status.OK, "application/json; charset=utf-8", data.toString())
                    }
                    is ApiResult.Failure -> reply(Response.Status.INTERNAL_ERROR, "application/json", "{}")
                }
            }
            if (session.method == Method.GET && path == "/api/release") {
                val id = parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
                    ?: return reply(Response.Status.BAD_REQUEST, "application/json", "{}")
                return when (val result = runBlocking { withTimeoutOrNull(REQUEST_TIMEOUT_MS) { repository.release(id.toString()) } }) {
                    null -> reply(Response.Status.INTERNAL_ERROR, "application/json", "{}")
                    is ApiResult.Success -> reply(Response.Status.OK, "application/json; charset=utf-8", releaseJson(result.value).toString())
                    is ApiResult.Failure -> reply(Response.Status.INTERNAL_ERROR, "application/json", "{}")
                }
            }
            if (session.method == Method.POST) {
                val command = routeCommand(path, parameters)
                    ?: return reply(Response.Status.BAD_REQUEST, "application/json", "{}")
                return if (_commands.tryEmit(command)) reply(Response.Status.OK, "application/json", "{\"ok\":true}")
                else reply(Response.Status.SERVICE_UNAVAILABLE, "application/json", "{}")
            }
            return reply(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }

        private fun releaseJson(release: Release) = buildJsonObject {
            put("id", release.id)
            put("title", release.title)
            put("episodes", JsonArray(release.episodes.map { episode -> buildJsonObject {
                put("id", episode.id)
                put("name", episode.name)
                episode.ordinal?.let { put("ordinal", it) }
            } }))
        }

        private fun reply(status: Response.Status, type: String, body: String): Response =
            newFixedLengthResponse(status, type, body).apply {
                addHeader("Cache-Control", "no-store")
                addHeader("X-Content-Type-Options", "nosniff")
                addHeader("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'")
            }
    }
}

/** A slow catalog request must not hold one of the server's few worker threads for long. */
private const val REQUEST_TIMEOUT_MS = 10_000L
