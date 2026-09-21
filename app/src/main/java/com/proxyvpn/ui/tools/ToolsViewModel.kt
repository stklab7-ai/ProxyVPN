package com.proxyvpn.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxyvpn.data.network.IpGeoResponse
import com.proxyvpn.data.repository.ToolsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ToolsUiState {
    object Idle : ToolsUiState()
    object Loading : ToolsUiState()
    data class SuccessGeo(val data: IpGeoResponse) : ToolsUiState()
    data class SuccessSpeed(val pingMs: Long, val speedKbps: Long) : ToolsUiState()
    data class Error(val message: String) : ToolsUiState()
}

class ToolsViewModel(private val repository: ToolsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ToolsUiState>(ToolsUiState.Idle)
    val uiState: StateFlow<ToolsUiState> = _uiState

    fun checkIp() {
        viewModelScope.launch {
            _uiState.value = ToolsUiState.Loading
            repository.getIpInfo().collect { result ->
                result.onSuccess { 
                    _uiState.value = ToolsUiState.SuccessGeo(it) 
                }.onFailure { 
                    _uiState.value = ToolsUiState.Error(it.message ?: "Unknown error") 
                }
            }
        }
    }

    fun runSpeedTest() {
        viewModelScope.launch {
            _uiState.value = ToolsUiState.Loading
            repository.measurePingAndSpeed().collect { result ->
                result.onSuccess { (ping, speed) ->
                    _uiState.value = ToolsUiState.SuccessSpeed(ping, speed)
                }.onFailure {
                    _uiState.value = ToolsUiState.Error(it.message ?: "Speedtest failed")
                }
            }
        }
    }
}
