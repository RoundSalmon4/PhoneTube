package com.roundsalmon4.phonetube.core.cast

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

@Serializable
data class CastCommand(
    val type: String,
    val url: String? = null,
    val title: String? = null,
    val position: Long? = null,
    val volume: Float? = null,
    val speed: Float? = null
)

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

    fun connect(device: CastDevice) {
        if (webSocket != null) {
            Log.d(TAG, "connect: closing existing socket first")
            webSocket?.close(1000, "reconnect")
            webSocket = null
        }
        _connectionState.value = CastConnectionState.Connecting(device)
        _tvStatus.value = TvCastStatus()

        val wsUrl = "ws://${device.host}:${device.port}"
        val request = Request.Builder().url(wsUrl).build()
        Log.i(TAG, "connect: opening $wsUrl")
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (this@CastRepository.webSocket === webSocket) {
                        Log.i(TAG, "onOpen: connected to ${device.name}")
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
                    if (this@CastRepository.webSocket === webSocket) {
                        Log.w(TAG, "onFailure: ${t.message}")
                        this@CastRepository.webSocket = null
                        pendingCommand = null
                        _connectionState.value = CastConnectionState.Disconnected
                        _tvStatus.value = TvCastStatus()
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (this@CastRepository.webSocket === webSocket) {
                        Log.i(TAG, "onClosed: $code $reason")
                        this@CastRepository.webSocket = null
                        pendingCommand = null
                        _connectionState.value = CastConnectionState.Disconnected
                        _tvStatus.value = TvCastStatus()
                    }
                }
            }
        )
    }

    fun disconnect() {
        Log.i(TAG, "disconnect")
        webSocket?.close(1000, "user disconnect")
        webSocket = null
        pendingCommand = null
        _connectionState.value = CastConnectionState.Disconnected
        _tvStatus.value = TvCastStatus()
    }

    fun sendPlay(url: String, title: String?, position: Long? = null) {
        send(CastCommand(type = "play", url = url, title = title, position = position))
    }

    fun sendPause() = send(CastCommand(type = "pause"))

    fun sendResume() = send(CastCommand(type = "resume"))

    fun sendSeek(positionMs: Long) = send(CastCommand(type = "seek", position = positionMs))

    fun sendStop() = send(CastCommand(type = "stop"))

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