package com.roundsalmon4.phonetube.ui.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.cast.CastConnectionState
import com.roundsalmon4.phonetube.core.cast.CastDevice
import com.roundsalmon4.phonetube.core.cast.CastRepository
import com.roundsalmon4.phonetube.core.cast.CastSubtitle
import com.roundsalmon4.phonetube.core.cast.TvCastStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CastViewModel @Inject constructor(
    private val repository: CastRepository
) : ViewModel() {

    val devices: StateFlow<List<CastDevice>> = repository.devices
    val connectionState: StateFlow<CastConnectionState> = repository.connectionState
    val tvStatus: StateFlow<TvCastStatus> = repository.tvStatus

    val isCasting: StateFlow<Boolean> = connectionState
        .map { it is CastConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun addDevice(name: String, host: String, port: Int) {
        repository.addDevice(CastDevice(name = name, host = host, port = port))
    }

    fun removeDevice(host: String) = repository.removeDevice(host)

    fun connect(device: CastDevice) = repository.connect(device)

    fun disconnect() = repository.disconnect()

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
        repository.connect(device)
        repository.sendPlay(url, title, positionMs, subtitles, quality, speed, activeSubtitleIndex)
    }

    fun stopCasting() = repository.disconnect()

    fun pause() = repository.sendPause()

    fun resume() = repository.sendResume()

    fun seekTo(positionMs: Long) = repository.sendSeek(positionMs)

    fun setVolume(volume: Float) = repository.sendVolume(volume)

    fun setSpeed(speed: Float) = repository.sendSpeed(speed)

    fun setQuality(qualityHeight: Int) = repository.sendQuality(qualityHeight)

    fun setSubtitle(castSubtitleIndex: Int?) = repository.sendSubtitle(castSubtitleIndex)
}