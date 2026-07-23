package app.phonetube.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.database.SubscriptionDao
import app.phonetube.core.database.entity.LocalSubscription
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.model.ChannelSection
import app.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: YouTubeEngine,
    private val subscriptionDao: SubscriptionDao
) : ViewModel() {

    private val channelId: String = savedStateHandle["channelId"]!!

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    init {
        loadChannel()
        observeSubscription()
    }

    private fun loadChannel() {
        _uiState.value = ChannelUiState.Loading
        viewModelScope.launch {
            engine.getChannel(channelId)
                .catch { e ->
                    _uiState.value = ChannelUiState.Error(e.message ?: "Failed to load channel")
                }
                .firstOrNull()
                ?.let { result ->
                    val channel = result.channel
                    val sections = result.sections
                    if (channel == null) {
                        _uiState.value = ChannelUiState.Error("Channel not found")
                    } else {
                        _uiState.value = ChannelUiState.Success(
                            name = channel.name,
                            avatarUrl = channel.avatarUrl,
                            subscriberCount = channel.subscriberCount,
                            description = channel.description,
                            sections = sections
                        )
                    }
                }
        }
    }

    private fun observeSubscription() {
        viewModelScope.launch {
            subscriptionDao.isSubscribed(channelId).collect { subscribed ->
                _isSubscribed.value = subscribed
            }
        }
    }

    fun toggleSubscription() {
        viewModelScope.launch {
            if (_isSubscribed.value) {
                subscriptionDao.unsubscribe(channelId)
            } else {
                val state = _uiState.value
                val name = if (state is ChannelUiState.Success) state.name else channelId
                val avatar = if (state is ChannelUiState.Success) state.avatarUrl else null
                subscriptionDao.subscribe(
                    LocalSubscription(
                        channelId = channelId,
                        channelName = name,
                        thumbnailUrl = avatar.orEmpty(),
                        subscribedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun retry() {
        loadChannel()
    }
}

sealed interface ChannelUiState {
    data object Loading : ChannelUiState
    data class Error(val message: String) : ChannelUiState
    data class Success(
        val name: String,
        val avatarUrl: String?,
        val subscriberCount: String?,
        val description: String?,
        val sections: List<ChannelSection>
    ) : ChannelUiState
}
