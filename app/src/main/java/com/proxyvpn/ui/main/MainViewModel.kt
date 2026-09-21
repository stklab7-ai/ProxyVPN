package com.proxyvpn.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProxyItem(val host: String, val port: Int, val ping: Double)

class MainViewModel : ViewModel() {
    private val _proxies = MutableStateFlow<List<ProxyItem>>(emptyList())
    val proxies: StateFlow<List<ProxyItem>> = _proxies

    fun loadProxies() {
        viewModelScope.launch {
            // TODO: Fetch from Repository
            _proxies.value = listOf(
                ProxyItem("192.168.1.1", 1080, 0.5),
                ProxyItem("10.0.0.1", 1080, 0.8)
            )
        }
    }
}
