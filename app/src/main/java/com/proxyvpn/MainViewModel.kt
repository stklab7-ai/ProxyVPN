package com.proxyvpn

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.IOException

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ProxyItem(
    val ip: String,
    val port: Int,
    val country: String = "Global",
    val ping: Int = 0,
    var isFavorite: Boolean = false,
    val method: String? = null,
    val password: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("proxy_favorites", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient()

    private val _proxies = MutableStateFlow<List<ProxyItem>>(emptyList())
    val proxies = _proxies.asStateFlow()

    private val _isLoadingProxies = MutableStateFlow(false)
    val isLoadingProxies = _isLoadingProxies.asStateFlow()
    
    private val _selectedProxy = MutableStateFlow<ProxyItem?>(null)
    val selectedProxy = _selectedProxy.asStateFlow()

    private val _currentIp = MutableStateFlow<String?>(null)
    val currentIp = _currentIp.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive = _isVpnActive.asStateFlow()

    fun setVpnActive(active: Boolean) {
        _isVpnActive.value = active
    }

    fun selectProxy(proxy: ProxyItem) {
        _selectedProxy.value = proxy
    }


    private fun getFavorites(): List<ProxyItem> {
        val json = prefs.getString("favs_v3", "[]")
        val type = object : TypeToken<List<ProxyItem>>() {}.type
        val favs = (gson.fromJson<List<ProxyItem>>(json, type) ?: emptyList()).toMutableList()
        // Force inject VLESS worker
        val vlessIp = "proxy-vpn.stk-lab7.workers.dev"
        if (favs.none { it.ip == vlessIp }) {
            favs.add(0, ProxyItem(
                ip = vlessIp,
                port = 443,
                country = "Vless Cloudflare",
                ping = 0,
                isFavorite = true,
                method = "VLESS", // Trick: use method to pass proxy type since it's unused otherwise for custom
                password = "e1830450-8ac0-4850-be71-3afe3ab603b2" // UUID
            ))
        }
        return favs
    }

    private fun saveFavorites(favs: List<ProxyItem>) {
        prefs.edit().putString("favs_v3", gson.toJson(favs)).apply()
    }

    fun toggleFavorite(proxy: ProxyItem) {
        val favs = getFavorites().toMutableList()
        val index = favs.indexOfFirst { it.ip == proxy.ip && it.port == proxy.port }
        val isFav = if (index != -1) {
            favs.removeAt(index)
            false
        } else {
            favs.add(proxy.copy(isFavorite = true))
            true
        }
        saveFavorites(favs)
        
        // Update list
        _proxies.value = _proxies.value.map { 
            if (it.ip == proxy.ip && it.port == proxy.port) it.copy(isFavorite = isFav) else it
        }
    }

    fun addCustomProxy(ip: String, port: Int) {
        val customProxy = ProxyItem(ip = ip, port = port, country = "Custom", ping = 0, isFavorite = true)
        val favs = getFavorites().toMutableList()
        if (favs.none { it.ip == ip && it.port == port }) {
            favs.add(customProxy)
            saveFavorites(favs)
        }
        
        // Prepend to current list
        val current = _proxies.value.toMutableList()
        if (current.none { it.ip == ip && it.port == port }) {
            current.add(0, customProxy)
            _proxies.value = current
        }
    }


    init {
        _proxies.value = getFavorites()
        fetchProxies(forceRefresh = false)
        // Silent quick check of existing
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            quickCheckProxies()
        }
    }

    private fun quickCheckProxies() {
        if (_proxies.value.isEmpty()) return
        viewModelScope.launch {
            try {
                val finder = ProxyFinder(getApplication())
                val current = _proxies.value
                val tested = withContext(Dispatchers.IO) {
                    current.map { proxy ->
                        async {
                            // Map to ProxyInfo
                            val info = ProxyInfo(proxy.ip, proxy.port, if (proxy.method != null) ProxyType.SHADOWSOCKS else ProxyType.SOCKS5, "", proxy.method, proxy.password)
                            if (finder.testProxyQuick(info)) {
                                proxy
                            } else {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                
                // Keep favorites even if they failed quick test temporarily, 
                // but we only update the list if there's a difference.
                
                val finalProxies = current.filter { p -> p.isFavorite || tested.any { it.ip == p.ip && it.port == p.port } }
                _proxies.value = finalProxies
                
                if (finalProxies.count { !it.isFavorite } < 6) {
                    fetchProxies(forceRefresh = true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun fetchProxies(forceRefresh: Boolean = false) {
        if (!forceRefresh && _proxies.value.isNotEmpty()) return
        
        viewModelScope.launch {
            _isLoadingProxies.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val finder = ProxyFinder(getApplication())
                    val infosSocks = finder.loadProxies(forceRefresh, null, ProxyType.SOCKS5)
                    val infosSS = finder.loadProxies(forceRefresh, null, ProxyType.SHADOWSOCKS)
                    val infos = infosSocks + infosSS
                    val favs = getFavorites()
                    val favSet = favs.map { "${it.ip}:${it.port}" }.toSet()
                    
                    val scraped = infos.map { info ->
                        val key = "${info.host}:${info.port}"
                        ProxyItem(
                            ip = info.host,
                            port = info.port,
                            country = info.location ?: "Global",
                            ping = (info.responseTime * 1000).toInt(),
                            isFavorite = favSet.contains(key),
                            method = info.method,
                            password = info.password
                        )
                    }
                    
                    // Add favorites that were NOT scraped
                    val notScrapedFavs = favs.filter { fav -> 
                        scraped.none { it.ip == fav.ip && it.port == fav.port }
                    }
                    
                    notScrapedFavs + scraped
                }
                _proxies.value = result
                if (result.isNotEmpty() && _selectedProxy.value == null) {
                    _selectedProxy.value = result[0]
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingProxies.value = false
            }
        }
    }

    private val _forceDns = MutableStateFlow(prefs.getBoolean("force_dns", false))
    val forceDns = _forceDns.asStateFlow()
    
    private val _encryptedDns = MutableStateFlow(prefs.getBoolean("encrypted_dns", false))
    val encryptedDns = _encryptedDns.asStateFlow()
    
    fun toggleEncryptedDns() {
        val newVal = !_encryptedDns.value
        _encryptedDns.value = newVal
        prefs.edit().putBoolean("encrypted_dns", newVal).apply()
    }

    private val _byedpi = MutableStateFlow(prefs.getBoolean("byedpi", false))
    val byedpi = _byedpi.asStateFlow()

    fun toggleForceDns() {
        val newVal = !_forceDns.value
        _forceDns.value = newVal
        prefs.edit().putBoolean("force_dns", newVal).apply()
    }


    private val _customDns = MutableStateFlow(prefs.getString("custom_dns", "1.1.1.1") ?: "1.1.1.1")
    val customDns = _customDns.asStateFlow()


    private val _customMtu = MutableStateFlow(prefs.getString("custom_mtu", "1280") ?: "1280")
    val customMtu = _customMtu.asStateFlow()


    private val _byedpiArgs = MutableStateFlow(prefs.getString("byedpi_args", "--split 1 --auto=torst --tlsrec 1+s") ?: "--split 1 --auto=torst --tlsrec 1+s")
    val byedpiArgs = _byedpiArgs.asStateFlow()

    fun setByedpiArgs(args: String) {
        _byedpiArgs.value = args
        prefs.edit().putString("byedpi_args", args).apply()
    }

    fun setCustomMtu(mtu: String) {
        _customMtu.value = mtu
        prefs.edit().putString("custom_mtu", mtu).apply()
    }

    fun setCustomDns(dns: String) {
        _customDns.value = dns
        prefs.edit().putString("custom_dns", dns).apply()
    }

    fun toggleByeDpi() {
        val newVal = !_byedpi.value
        _byedpi.value = newVal
        prefs.edit().putBoolean("byedpi", newVal).apply()
    }

    fun checkMyIp() {
        viewModelScope.launch {
            _currentIp.value = "Проверка..."
            try {
                val ip = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://api.ipify.org")
                        .build()
                    client.newCall(request).execute().use { response ->
                        response.body?.string()?.trim() ?: "Ошибка"
                    }
                }
                _currentIp.value = ip
            } catch (e: Exception) {
                _currentIp.value = "Не удалось подключиться"
            }
        }
    }
}