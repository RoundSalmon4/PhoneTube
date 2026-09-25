package com.roundsalmon4.phonetube.core.cast

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roundsalmon4.phonetube.core.engine.model.SubtitleTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.castDataStore by preferencesDataStore(name = "cast_preferences")

@Serializable
data class CastDevice(
    val name: String,
    val host: String,
    val port: Int = 8484
)

/** Outcome of a one-shot connectivity check against a cast device. */
data class ProbeResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long? = null
)

@Serializable
data class CastCommand(
    val type: String,
    val url: String? = null,
    val title: String? = null,
    val position: Long? = null,
    val volume: Float? = null,
    val speed: Float? = null,
    val subtitles: List<CastSubtitle>? = null,
    val quality: Int? = null,
    val activeSubtitleIndex: Int? = null,
    val subtitleIndex: Int? = null,
    val message: String? = null
)

@Serializable
data class CastSubtitle(
    val url: String,
    val languageCode: String,
    val name: String,
    val mimeType: String
)

/**
 * Maps the app's subtitle tracks into the cast protocol, converting TTML to
 * WebVTT the same way the local player does so the TV renders them reliably.
 */
fun List<SubtitleTrack>.toCastSubtitles(): List<CastSubtitle> =
    map { track ->
        val useVtt = track.mimeType.contains("ttml")
        CastSubtitle(
            url = if (useVtt) track.baseUrl.replace("fmt=ttml", "fmt=vtt") else track.baseUrl,
            languageCode = track.languageCode,
            name = track.name,
            mimeType = if (useVtt) "text/vtt" else track.mimeType.ifBlank { "text/vtt" }
        )
    }

@Serializable
data class TvCastStatus(
    val type: String = "status",
    val state: String = "idle",
    val position: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val title: String? = null,
    val error: String? = null
)

sealed interface CastConnectionState {
    data object Disconnected : CastConnectionState
    data class Connecting(val device: CastDevice) : CastConnectionState
    data class Connected(val device: CastDevice) : CastConnectionState
}

@Singleton
class CastRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "CastRepository"
        val DEVICES_KEY = stringPreferencesKey("cast_devices")
        const val MAX_CONNECT_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 2000L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _connectionState =
        MutableStateFlow<CastConnectionState>(CastConnectionState.Disconnected)
    val connectionState: StateFlow<CastConnectionState> = _connectionState.asStateFlow()

    private val _tvStatus = MutableStateFlow(TvCastStatus())
    val tvStatus: StateFlow<TvCastStatus> = _tvStatus.asStateFlow()

    private var webSocket: WebSocket? = null
    // OkHttp 3.x drops frames sent before the socket finishes opening, so
    // commands issued right after connect() (e.g. the initial play) must be
    // held until onOpen and flushed there.
    private var pendingCommand: CastCommand? = null

    /**
     * Which video is currently on the TV. Lives on the singleton so it
     * survives player-screen navigation: returning to the same video (e.g.
     * via the mini player) resumes at the TV position instead of restarting.
     */
    @Volatile
    var lastCastVideoId: String? = null

    // Connection guard: the phone's path to the TV can flap transiently, so a
    // failed connect is retried briefly with backoff instead of giving up on
    // the first 10s timeout.
    private var connectAttempts = 0
    private var userDisconnected = true
    private val retryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun consumeError() {
        _lastError.value = null
    }

    init {
        scope.launch {
            context.castDataStore.data
                .map { prefs ->
                    val raw = prefs[DEVICES_KEY]
                    if (raw.isNullOrBlank()) emptyList()
                    else try {
                        json.decodeFromString<List<CastDevice>>(raw)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                .collect { _devices.value = it }
        }
    }

    fun addDevice(device: CastDevice) = scope.launch {
        val cleaned = device.copy(
            name = device.name.trim().ifBlank { device.host },
            host = device.host
                .trim()
                .removePrefix("ws://")
                .removePrefix("http://")
                .removePrefix("https://")
                .trimEnd('/', ' ')
        )
        val deviceAdded = cleaned.host.isNotBlank()
        val filtered = _devices.value.filterNot { it.host == cleaned.host && it.port == cleaned.port }
        val updated = if (deviceAdded) filtered + cleaned else filtered
        _devices.value = updated
        context.castDataStore.edit { it[DEVICES_KEY] = json.encodeToString(updated) }
        Log.i(TAG, "addDevice: ${cleaned.name} at ${cleaned.host}:${cleaned.port} (devices=${updated.size})")
    }

    fun removeDevice(host: String) = scope.launch {
        val updated = _devices.value.filterNot { it.host == host }
        _devices.value = updated
        context.castDataStore.edit { it[DEVICES_KEY] = json.encodeToString(updated) }
        val connected = _connectionState.value as? CastConnectionState.Connected
        if (connected != null && connected.device.host == host) {
            disconnect()
        }
        Log.i(TAG, "removeDevice: $host (devices=${updated.size})")
    }

    /** Replaces the whole saved device list (used by data import). */
    fun replaceDevices(devices: List<CastDevice>) {
        val cleaned = devices.map { device ->
            device.copy(
                name = device.name.trim().ifBlank { device.host },
                host = device.host
                    .trim()
                    .removePrefix("ws://")
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .trimEnd('/', ' ')
            )
        }.filter { it.host.isNotBlank() }
        _devices.value = cleaned
        scope.launch {
            context.castDataStore.edit { it[DEVICES_KEY] = json.encodeToString(cleaned) }
        }
        Log.i(TAG, "replaceDevices: ${cleaned.size} device(s)")
    }

    /**
     * One-shot connectivity check used by the "Test" button. Opens its own
     * WebSocket to the device and reports a readable verdict, leaving the
     * active cast session state untouched.
     */
    suspend fun probe(device: CastDevice, timeoutMs: Long = 5_000): ProbeResult =
        withContext(Dispatchers.IO) {
            val url = "ws://${device.host}:${device.port}".lowercase()
            val start = System.currentTimeMillis()
            val outcome = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<ProbeResult> { cont ->
                    val listener = object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            val latency = System.currentTimeMillis() - start
                            webSocket.close(1000, "probe done")
                            if (cont.isActive) {
                                cont.resume(ProbeResult(true, "Connected in $latency ms", latency))
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            if (cont.isActive) {
                                cont.resume(ProbeResult(false, probeFailureMessage(t), null))
                            }
                        }
                    }
                    val socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
                    cont.invokeOnCancellation { socket.cancel() }
                }
            }
            outcome ?: ProbeResult(
                false,
                "Timed out after ${timeoutMs / 1000} s. Is the TV powered on and on the same network?"
            )
        }

    private fun probeFailureMessage(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "Unknown host: ${t.message}. Check the saved address."
        is java.net.ConnectException -> "Connection refused. Is PhoneTV running on this device and on this port?"
        is java.net.SocketTimeoutException -> "Connection timed out. Check the network path to the TV."
        is java.io.IOException -> "Network error: ${t.message}"
        else -> "Probe failed: ${t.message ?: t.javaClass.simpleName}"
    }

    fun connect(device: CastDevice) {
        if (webSocket != null) {
            Log.d(TAG, "connect: closing existing socket first")
            webSocket?.close(1000, "reconnect")
            webSocket = null
        }
        userDisconnected = false
        connectAttempts = 0
        _lastError.value = null
        openSocket(device)
    }

    private fun openSocket(device: CastDevice) {
        _connectionState.value = CastConnectionState.Connecting(device)
        _tvStatus.value = TvCastStatus()

        val wsUrl = "ws://${device.host}:${device.port}"
        val request = Request.Builder().url(wsUrl).build()
        Log.i(TAG, "connect: opening $wsUrl (attempt ${connectAttempts + 1})")
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (this@CastRepository.webSocket === webSocket) {
                        Log.i(TAG, "onOpen: connected to ${device.name}")
                        connectAttempts = 0
                        _connectionState.value = CastConnectionState.Connected(device)
                        pendingCommand?.let { command ->
                            Log.d(TAG, "onOpen: flushing pending ${command.type}")
                            if (webSocket.send(json.encodeToString(command))) {
                                pendingCommand = null
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (this@CastRepository.webSocket !== webSocket) return
                    try {
                        val status = json.decodeFromString<TvCastStatus>(text)
                        if (status.type == "stopped") {
                            Log.i(TAG, "onMessage: TV stopped playback, ending cast session")
                            webSocket.close(1000, "tv stopped")
                            this@CastRepository.webSocket = null
                            userDisconnected = true
                            pendingCommand = null
                            _connectionState.value = CastConnectionState.Disconnected
                            // Keep the last position so the phone can resume
                            // playback where the TV stopped.
                            _tvStatus.value = _tvStatus.value.copy(state = "idle")
                        } else {
                            _tvStatus.value = status
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "onMessage: bad status payload: $text")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (this@CastRepository.webSocket !== webSocket) return
                    Log.w(TAG, "onFailure: ${t.message}")
                    this@CastRepository.webSocket = null
                    pendingCommand = null
                    _tvStatus.value = TvCastStatus()
                    if (!handleFailure(device, t)) {
                        _connectionState.value = CastConnectionState.Disconnected
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (this@CastRepository.webSocket !== webSocket) return
                    Log.i(TAG, "onClosed: $code $reason")
                    this@CastRepository.webSocket = null
                    pendingCommand = null
                    _tvStatus.value = TvCastStatus()
                    if (code == 1006 && !isCasting && !handleFailure(device, null)) {
                        _connectionState.value = CastConnectionState.Disconnected
                    }
                }
            }
        )
    }

    /**
     * Retries the cast connection with backoff while the network path may be
     * transiently flapping. Returns true when a retry was scheduled.
     */
    private fun handleFailure(device: CastDevice, error: Throwable?): Boolean {
        if (userDisconnected) return false
        if (connectAttempts >= MAX_CONNECT_ATTEMPTS - 1) {
            _lastError.value = "Couldn't reach ${device.name}. Check that both devices are on the same network and that your VPN allows local connections."
            _connectionState.value = CastConnectionState.Disconnected
            return false
        }
        connectAttempts++
        val attempt = connectAttempts
        Log.i(TAG, "connect: retrying (${attempt + 1}/$MAX_CONNECT_ATTEMPTS) after failure: ${error?.message}")
        retryScope.launch {
            delay(RETRY_DELAY_MS)
            if (!userDisconnected && webSocket == null) {
                openSocket(device)
            }
        }
        return true
    }

    fun disconnect() {
        Log.i(TAG, "disconnect")
        userDisconnected = true
        connectAttempts = 0
        webSocket?.close(1000, "user disconnect")
        webSocket = null
        pendingCommand = null
        _connectionState.value = CastConnectionState.Disconnected
        _tvStatus.value = TvCastStatus()
    }

    private val isCasting: Boolean
        get() = _connectionState.value is CastConnectionState.Connected

    fun sendPlay(
        url: String,
        title: String?,
        position: Long? = null,
        subtitles: List<CastSubtitle>? = null,
        quality: Int? = null,
        speed: Float? = null,
        activeSubtitleIndex: Int? = null
    ) {
        Log.d(TAG, "sendPlay: position=$position quality=$quality speed=$speed subtitle=$activeSubtitleIndex subs=${subtitles?.size}")
        send(
            CastCommand(
                type = "play",
                url = url,
                title = title,
                position = position,
                speed = speed,
                subtitles = subtitles,
                quality = quality,
                activeSubtitleIndex = activeSubtitleIndex
            )
        )
    }

    fun sendQuality(qualityHeight: Int) {
        Log.d(TAG, "sendQuality: $qualityHeight")
        send(CastCommand(type = "set_quality", quality = qualityHeight))
    }

    fun sendSubtitle(castSubtitleIndex: Int?) {
        Log.d(TAG, "sendSubtitle: $castSubtitleIndex")
        send(CastCommand(type = "set_subtitle", subtitleIndex = castSubtitleIndex))
    }

    /** Mirrors a transient notice (e.g. a SponsorBlock skip) to the TV. */
    fun sendToast(text: String) {
        Log.d(TAG, "sendToast: $text")
        send(CastCommand(type = "toast", message = text))
    }

    fun sendPause() = send(CastCommand(type = "pause"))

    fun sendResume() = send(CastCommand(type = "resume"))

    fun sendSeek(positionMs: Long) = send(CastCommand(type = "seek", position = positionMs))

    fun sendVolume(volume: Float) = send(CastCommand(type = "set_volume", volume = volume))

    fun sendSpeed(speed: Float) = send(CastCommand(type = "set_speed", speed = speed))

    private fun send(command: CastCommand) {
        val socket = webSocket
        if (socket == null) {
            // A connect() may be in flight; OkHttp 3.x drops frames sent
            // before onOpen, so hold the command to flush on open.
            Log.d(TAG, "send(${command.type}): holding until socket opens")
            pendingCommand = command
            return
        }
        val sent = socket.send(json.encodeToString(command))
        if (!sent) {
            Log.d(TAG, "send(${command.type}): socket not ready, holding")
            pendingCommand = command
        }
    }
}