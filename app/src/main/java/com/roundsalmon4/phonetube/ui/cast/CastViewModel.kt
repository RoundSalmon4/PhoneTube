package com.roundsalmon4.phonetube.ui.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.cast.CastConnectionState
import com.roundsalmon4.phonetube.core.cast.CastDevice
import com.roundsalmon4.phonetube.core.cast.CastDiscoverer
import com.roundsalmon4.phonetube.core.cast.CastRepository
import com.roundsalmon4.phonetube.core.cast.CastSubtitle
import com.roundsalmon4.phonetube.core.cast.TvCastStatus
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CastViewModel @Inject constructor(
    private val repository: CastRepository,
    private val discoverer: CastDiscoverer,
    private val playerPreferences: PlayerPreferences
) : ViewModel() {

    val devices: StateFlow<List<CastDevice>> = repository.devices
    val nearby: StateFlow<List<CastDevice>> = discoverer.nearby
    val connectionState: StateFlow<CastConnectionState> = repository.connectionState
    val tvStatus: StateFlow<TvCastStatus> = repository.tvStatus
    val lastError: StateFlow<String?> = repository.lastError

    val isCasting: StateFlow<Boolean> = connectionState
        .map { it is CastConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _pendingSave = MutableStateFlow<PendingCast?>(null)
    val pendingSave: StateFlow<PendingCast?> = _pendingSave.asStateFlow()

    fun addDevice(name: String, host: String, port: Int) {
        repository.addDevice(CastDevice(name = name, host = host, port = port))
    }

    fun removeDevice(host: String) = repository.removeDevice(host)

    fun consumeError() = repository.consumeError()

    fun startDiscovery() = discoverer.start()

    fun stopDiscovery() = discoverer.stop()

    /**
     * Starts a cast. Devices already saved cast immediately. For a device
     * found via discovery, the "save casted devices" setting decides whether it
     * is added to the saved list silently or the UI is asked first.
     */
    fun startCast(
        device: CastDevice,
        url: String,
        title: String?,
        positionMs: Long?,
        subtitles: List<CastSubtitle>? = null,
        quality: Int? = null,
        speed: Float? = null,
        activeSubtitleIndex: Int? = null
    ) {
        val alreadySaved = repository.devices.value.any {
            it.host == device.host && it.port == device.port
        }
        if (alreadySaved) {
            startCastNow(device, url, title, positionMs, subtitles, quality, speed, activeSubtitleIndex)
            return
        }
        viewModelScope.launch {
            val autoSave = playerPreferences.uiState.first().saveCastedDevices
            if (autoSave) {
                repository.addDevice(device)
                startCastNow(device, url, title, positionMs, subtitles, quality, speed, activeSubtitleIndex)
            } else {
                _pendingSave.value = PendingCast(
                    device = device,
                    url = url,
                    title = title,
                    positionMs = positionMs,
                    subtitles = subtitles,
                    quality = quality,
                    speed = speed,
                    activeSubtitleIndex = activeSubtitleIndex
                )
            }
        }
    }

    /** Keep the device and cast. */
    fun confirmPendingSave() {
        val pending = _pendingSave.value ?: return
        _pendingSave.value = null
        repository.addDevice(pending.device)
        startCastNow(
            pending.device, pending.url, pending.title, pending.positionMs,
            pending.subtitles, pending.quality, pending.speed, pending.activeSubtitleIndex
        )
    }

    /** Cast without keeping the device. */
    fun dismissPendingSave() {
        val pending = _pendingSave.value ?: return
        _pendingSave.value = null
        startCastNow(
            pending.device, pending.url, pending.title, pending.positionMs,
            pending.subtitles, pending.quality, pending.speed, pending.activeSubtitleIndex
        )
    }

    private fun startCastNow(
        device: CastDevice,
        url: String,
        title: String?,
        positionMs: Long?,
        subtitles: List<CastSubtitle>?,
        quality: Int?,
        speed: Float?,
        activeSubtitleIndex: Int?
    ) {
        repository.connect(device)
        repository.sendPlay(url, title, positionMs, subtitles, quality, speed, activeSubtitleIndex)
    }

    fun stopCasting() = repository.disconnect()

    fun pause() = repository.sendPause()

    fun resume() = repository.sendResume()

    fun seekTo(positionMs: Long) = repository.sendSeek(positionMs)

    fun setVolume(volume: Float) = repository.sendVolume(volume)

    fun setSpeed(speed: Float) = repository.sendSpeed(speed)
}

data class PendingCast(
    val device: CastDevice,
    val url: String,
    val title: String?,
    val positionMs: Long?,
    val subtitles: List<CastSubtitle>?,
    val quality: Int?,
    val speed: Float?,
    val activeSubtitleIndex: Int?
)