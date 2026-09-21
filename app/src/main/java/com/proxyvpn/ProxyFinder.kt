package com.proxyvpn

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.*
import java.net.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.*

interface ProgressCallback {
    fun onProgress(percent: Int)
    fun onStatus(status: String)
}

interface BackgroundRefreshCallback {
    fun onProxiesUpdated(proxies: List<ProxyInfo>)
}

class ProxyFinder(context: Context) {
    companion object {
        private const val PREFS_NAME = "proxy_cache"
        private const val CACHE_KEY = "working_proxies"
        private const val LAST_UPDATE_KEY = "last_update"
        private const val CACHE_TTL = 30 * 60 * 1000L
        private const val TAG = "ProxyFinder"
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        System.setProperty("java.net.preferIPv4Stack", "true")
        System.setProperty("java.net.preferIPv6Addresses", "false")
    }

    private val MIN_PROXIES = 5
    private val MAX_PROXIES = 10
    private val MAX_ATTEMPTS = 3

    @Volatile
    private var isCancelled = false

    private var backgroundScheduler: ScheduledExecutorService? = null
    private var backgroundCallback: BackgroundRefreshCallback? = null

    fun cancel() {
        isCancelled = true
        stopBackgroundRefresh()
    }

    private var backgroundProxyType: ProxyType = ProxyType.SOCKS5

    fun startBackgroundRefresh(callback: BackgroundRefreshCallback, proxyType: ProxyType = ProxyType.SOCKS5) {
        stopBackgroundRefresh()
        backgroundCallback = callback
        backgroundProxyType = proxyType
        backgroundScheduler = Executors.newSingleThreadScheduledExecutor()
        scheduleNextRefresh()
    }

    fun stopBackgroundRefresh() {
        backgroundScheduler?.shutdownNow()
        backgroundScheduler = null
        backgroundCallback = null
    }

    private fun scheduleNextRefresh() {
        val currentCount = getCachedProxies().size
        val intervalMin = when {
            currentCount >= 10 -> 5L
            currentCount >= 5 -> 3L
            else -> 1L
        }

        backgroundScheduler?.schedule({
            try {
                backgroundRefresh()
            } catch (e: Exception) {
                Log.d(TAG, "Background refresh error: ${e.message}")
            }
        }, intervalMin, TimeUnit.MINUTES)
    }

    private fun backgroundRefresh() {
        val callback = backgroundCallback ?: return
        val cacheKey = if (backgroundProxyType == ProxyType.MTPROTO) "mtproto_cache" else CACHE_KEY
        val cacheTTLKey = if (backgroundProxyType == ProxyType.MTPROTO) "mtproto_last_update" else LAST_UPDATE_KEY

        val cached = getCachedProxies(cacheKey).toMutableList()
        val usedIps = cached.map { "${it.host}:${it.port}:${it.type}" }.toMutableSet()

        // Шаг 1: Перепроверяем все существующие прокси
        Log.d(TAG, "Background: re-testing ${cached.size} existing $backgroundProxyType proxies")
        val stillAlive = mutableListOf<ProxyInfo>()
        for (proxy in cached) {
            if (isCancelled) break
            val ok = when (backgroundProxyType) {
                ProxyType.SOCKS5 -> testProxyQuick(proxy)
                ProxyType.MTPROTO -> testMTProto(proxy) > 0
                ProxyType.SHADOWSOCKS -> testProxyQuick(proxy)
                ProxyType.VLESS -> testProxyQuick(proxy)
            }
            if (ok) stillAlive.add(proxy)
        }
        Log.d(TAG, "Background: ${stillAlive.size}/${cached.size} still alive")

        // Шаг 2: Ищем новые только если мало прокси
        val needMore = MAX_PROXIES - stillAlive.size
        val newWorking = mutableListOf<ProxyInfo>()

        if (needMore > 0 && !isCancelled) {
            Log.d(TAG, "Background: need $needMore more, fetching new sources")
            val freshProxies = fetchAllProxies(null, backgroundProxyType)
            val unique = freshProxies.filter { "${it.host}:${it.port}:${it.type}" !in usedIps }
            Log.d(TAG, "Background: ${unique.size} new candidates to test")
            if (unique.isNotEmpty()) {
                newWorking.addAll(findWorkingProxies(unique.take(50), null, backgroundProxyType).take(needMore))
            }
        }

        // Шаг 3: Объединяем
        val merged = mutableListOf<ProxyInfo>()
        merged.addAll(stillAlive)
        for (p in newWorking) {
            val key = "${p.host}:${p.port}:${p.type}"
            if (key !in merged.map { "${it.host}:${it.port}:${it.type}" }) {
                merged.add(p)
            }
        }

        val result = merged.sortedBy { it.responseTime }.take(MAX_PROXIES)
        if (result.isNotEmpty()) {
            cacheProxies(result, cacheKey, cacheTTLKey)
        }

        Log.d(TAG, "Background result: alive=${stillAlive.size} + new=${newWorking.size} = ${result.size}")
        callback.onProxiesUpdated(result)

        if (backgroundScheduler != null && !isCancelled) {
            scheduleNextRefresh()
        }
    }

    fun loadProxies(forceRefresh: Boolean, callback: ProgressCallback?, proxyType: ProxyType = ProxyType.SOCKS5): List<ProxyInfo> {
        isCancelled = false
        val cacheKey = if (proxyType == ProxyType.MTPROTO) "mtproto_cache" else CACHE_KEY
        val cacheTTLKey = if (proxyType == ProxyType.MTPROTO) "mtproto_last_update" else LAST_UPDATE_KEY

        if (!forceRefresh) {
            val cached = getCachedProxies(cacheKey)
            val lastUpdate = prefs.getLong(cacheTTLKey, 0)
            if (cached.isNotEmpty()) {
                callback?.onStatus("Загружено из кэша: ${cached.size} прокси")
                return cached
            }
        }

        // MTProto: загружаем все (тестировать нельзя — обфусцированный протокол)
        if (proxyType == ProxyType.MTPROTO) {
            callback?.onStatus("Загрузка MTProto прокси...")
            val allProxies = fetchAllProxies(callback, proxyType)
            val unique = allProxies.distinctBy { "${it.host}:${it.port}" }
            if (unique.isNotEmpty()) {
                cacheProxies(unique, cacheKey, cacheTTLKey)
                callback?.onStatus("✅ Найдено: ${unique.size} MTProto прокси")
            } else {
                callback?.onStatus("❌ MTProto прокси не найдены")
            }
            return unique
        }

        // SOCKS5: стандартный поиск с тестированием
        var allWorking = mutableListOf<ProxyInfo>()
        val usedIps = mutableSetOf<String>()

        for (attempt in 1..MAX_ATTEMPTS) {
            if (isCancelled) break

            val freshProxies = fetchAllProxies(callback, proxyType)
            if (freshProxies.isEmpty()) continue

            val unique = freshProxies.filter { "${it.host}:${it.port}:${it.type}" !in usedIps }
            if (unique.isEmpty()) {
                callback?.onStatus("Новых прокси нет, завершаем")
                break
            }

            val est = estimateTime(unique.size)
            callback?.onStatus("Проверка прокси... ~${est}с")
            val working = findWorkingProxies(unique, callback, proxyType)

            for (p in working) {
                val key = "${p.host}:${p.port}:${p.type}"
                if (key !in usedIps) {
                    usedIps.add(key)
                    allWorking.add(p)
                }
            }

            if (allWorking.size >= MAX_PROXIES) {
                callback?.onStatus("Набрано ${allWorking.size} прокси, достаточно")
                break
            }

            if (allWorking.size >= MIN_PROXIES && attempt >= 2) {
                break
            }
        }

        if (allWorking.isNotEmpty()) {
            cacheProxies(allWorking, cacheKey, cacheTTLKey)
            callback?.onStatus("✅ Найдено: ${allWorking.size} прокси")
        } else {
            callback?.onStatus("❌ Прокси не найдены, попробуйте позже")
        }
        return allWorking.sortedBy { it.responseTime }
    }

    private fun estimateTime(proxyCount: Int): Int {
        val maxThreads = minOf(Runtime.getRuntime().availableProcessors() * 2, 12)
        val testTimeSec = 4
        return ((proxyCount + maxThreads - 1) / maxThreads) * testTimeSec
    }

    private val SOCKS5_SOURCES = listOf(
        "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks5.txt" to "text",
        "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt" to "text",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt" to "text",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt" to "text",
        "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt" to "text",
        "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt" to "text",
        "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/socks5.txt" to "text",
        "https://raw.githubusercontent.com/roosterkid/openproxylist/main/SOCKS5_RAW.txt" to "text",
    )

    private val MTPROTO_SOURCES = listOf(
        "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_all_mtproto.txt" to "mtproto_url",
    )
    
    private val SHADOWSOCKS_SOURCES = listOf(
        "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity" to "base64_ss"
    )

    private var sourceIndex = 0

    private fun fetchAllProxies(callback: ProgressCallback?, proxyType: ProxyType): List<ProxyInfo> {
        val allProxies = mutableListOf<ProxyInfo>()
        callback?.onStatus("Загрузка списка прокси...")

        when (proxyType) {
            ProxyType.SOCKS5 -> {
                val start = sourceIndex % SOCKS5_SOURCES.size
                for (i in 0 until minOf(2, SOCKS5_SOURCES.size)) {
                    val idx = (start + i) % SOCKS5_SOURCES.size
                    val (url, type) = SOCKS5_SOURCES[idx]
                    allProxies.addAll(fetchFromUrl(url, type))
                }
                sourceIndex = (start + 2) % SOCKS5_SOURCES.size
            }
            ProxyType.MTPROTO -> {
                for ((url, type) in MTPROTO_SOURCES) {
                    allProxies.addAll(fetchFromUrl(url, type))
                }
            }
            ProxyType.VLESS -> {}
            ProxyType.SHADOWSOCKS -> {
                for ((url, type) in SHADOWSOCKS_SOURCES) {
                    allProxies.addAll(fetchFromUrl(url, type))
                }
            }
        }

        Log.d(TAG, "Fetched ${allProxies.size} $proxyType proxies")
        return allProxies.distinct()
    }

    private fun fetchFromUrl(urlString: String, type: String): List<ProxyInfo> {
        val proxies = mutableListOf<ProxyInfo>()
        try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            when (type) {
                "text" -> {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.contains(":") && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                                val parts = trimmed.split(":")
                                if (parts.size >= 2) {
                                    val port = parts.last().toIntOrNull()
                                    if (port != null && port in 1..65535) {
                                        proxies.add(ProxyInfo(parts[0], port))
                                    }
                                }
                            }
                        }
                    }
                }
                "base64_ss" -> {
                    val content = conn.inputStream.readBytes()
                    val decodedListStr = try {
                        String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), Charsets.UTF_8)
                    } catch (e: Exception) { "" }
                    
                    decodedListStr.lineSequence().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("ss://")) {
                            try {
                                val base64Part = trimmed.substring(5).substringBefore("#")
                                // If it contains @, only the part before @ is base64
                                if (base64Part.contains("@")) {
                                    val parts = base64Part.split("@", limit=2)
                                    val authDecoded = String(android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT), Charsets.UTF_8)
                                    val methodPass = authDecoded.split(":", limit=2)
                                    val ipPort = parts[1].split(":", limit=2)
                                    if (methodPass.size == 2 && ipPort.size == 2) {
                                        val port = ipPort[1].substringBefore("/").toIntOrNull()
                                        if (port != null) {
                                            proxies.add(ProxyInfo(ipPort[0], port, ProxyType.SHADOWSOCKS, "", methodPass[0], methodPass[1]))
                                        }
                                    }
                                } else {
                                    // The entire thing is base64
                                    val decodedStr = String(android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT), Charsets.UTF_8)
                                    if (decodedStr.contains("@") && decodedStr.contains(":")) {
                                        val (auth, address) = decodedStr.split("@", limit = 2)
                                        val methodPass = auth.split(":", limit = 2)
                                        val ipPort = address.split(":", limit = 2)
                                        if (methodPass.size == 2 && ipPort.size == 2) {
                                            val port = ipPort[1].substringBefore("/").toIntOrNull()
                                            if (port != null) {
                                                proxies.add(ProxyInfo(ipPort[0], port, ProxyType.SHADOWSOCKS, "", methodPass[0], methodPass[1]))
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
                "mtproto" -> {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            val trimmed = line.trim()
                            // Формат: IP:PORT:SECRET
                            val parts = trimmed.split(":")
                            if (parts.size == 3) {
                                val port = parts[1].toIntOrNull()
                                if (port != null && port in 1..65535 && parts[2].isNotEmpty()) {
                                    proxies.add(ProxyInfo(parts[0], port, ProxyType.MTPROTO, parts[2]))
                                }
                            }
                        }
                    }
                }
                "mtproto_url" -> {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            val trimmed = line.trim()
                            // Формат: tg://proxy?server=IP&port=PORT&secret=SECRET
                            // или: https://t.me/proxy?server=IP&port=PORT&secret=SECRET
                            val regex = Regex("""(?:tg://|https?://t\.me/)proxy\?server=([^&]+)&port=(\d+)&secret=([^&\s]+)""")
                            regex.find(trimmed)?.let { match ->
                                val server = match.groupValues[1]
                                val port = match.groupValues[2].toIntOrNull()
                                val secret = match.groupValues[3]
                                if (port != null && port in 1..65535 && secret.isNotEmpty()) {
                                    proxies.add(ProxyInfo(server, port, ProxyType.MTPROTO, secret))
                                }
                            }
                        }
                    }
                }
                "mtproto_json" -> {
                    val body = conn.inputStream.bufferedReader().readText()
                    val jsonArray = JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val server = obj.optString("server", "")
                        val port = obj.optInt("port", 0)
                        val secret = obj.optString("secret", "")
                        if (server.isNotEmpty() && port in 1..65535 && secret.isNotEmpty()) {
                            proxies.add(ProxyInfo(server, port, ProxyType.MTPROTO, secret))
                        }
                    }
                }
                "html" -> {
                    val doc = Jsoup.parse(conn.inputStream, "UTF-8", urlString)
                    val rows = doc.select("table#iptable tr, table.table tr, table tr")
                    for (row in rows) {
                        val ipCell = row.selectFirst("td[data-ip]")
                        val portCell = row.selectFirst("td[data-port]")
                        if (ipCell != null && portCell != null) {
                            try {
                                val ip = String(Base64.decode(ipCell.attr("data-ip") + "====", Base64.DEFAULT)).trim()
                                val portStr = String(Base64.decode(ipCell.attr("data-port") + "====", Base64.DEFAULT)).trim()
                                val port = portStr.toIntOrNull()
                                if (port != null && port in 1..65535 && ip.isNotEmpty()) {
                                    proxies.add(ProxyInfo(ip, port))
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Fetch error: ${e.message}")
        }
        return proxies
    }

    private fun findWorkingProxies(proxies: List<ProxyInfo>, callback: ProgressCallback?, proxyType: ProxyType = ProxyType.SOCKS5): List<ProxyInfo> {
        val testList = proxies
        val startTime = System.currentTimeMillis()
        callback?.onStatus("Test ${testList.size} proxies...")
        val working = java.util.concurrent.CopyOnWriteArrayList<ProxyInfo>()
        val maxThreads = kotlin.math.min(Runtime.getRuntime().availableProcessors() * 2, 12)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(maxThreads)
        val latch = java.util.concurrent.CountDownLatch(testList.size)

        for (proxy in testList) {
            if (isCancelled || working.size >= MAX_PROXIES) {
                latch.countDown()
                continue
            }
            executor.submit {
                try {
                    if (isCancelled || working.size >= MAX_PROXIES) return@submit
                    val pingTime = when (proxyType) {
                        ProxyType.SOCKS5 -> testProxyFull(proxy)
                        ProxyType.MTPROTO -> testMTProto(proxy)
                        ProxyType.VLESS -> {
                            var socket: java.net.Socket? = null
                            try {
                                val start = System.currentTimeMillis()
                                socket = java.net.Socket()
                                socket.connect(java.net.InetSocketAddress(proxy.host, proxy.port), 3000)
                                System.currentTimeMillis() - start
                            } catch (e: Exception) { -1L }
                            finally { socket?.close() }
                        }
                        ProxyType.SHADOWSOCKS -> {
                            var socket: java.net.Socket? = null
                            try {
                                val start = System.currentTimeMillis()
                                socket = java.net.Socket()
                                socket.connect(java.net.InetSocketAddress(proxy.host, proxy.port), 3000)
                                System.currentTimeMillis() - start
                            } catch (e: Exception) { -1L }
                            finally { socket?.close() }
                        }
                    }
                    if (pingTime in 1..4999) {
                        proxy.responseTime = pingTime / 1000.0
                        working.add(proxy)
                    }
                } catch (e: Exception) {
                } finally {
                    latch.countDown()
                    val checked = testList.size - latch.count.toInt()
                    val progress = (checked * 100 / testList.size)
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    val speed = if (checked > 0) elapsed / checked else 4.0
                    val remaining = (latch.count * speed).toInt()
                    callback?.onProgress(progress)
                    callback?.onStatus("$checked/${testList.size} | OK: ${working.size} | ~${remaining}s")
                }
            }
        }

        try { latch.await(30, java.util.concurrent.TimeUnit.SECONDS) } catch (_: InterruptedException) {}
        executor.shutdownNow()
        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0

        val filtered = if (proxyType == ProxyType.SOCKS5) {
            working.filter { proxy ->
                val loc = checkProxyLocation(proxy)
                proxy.location = loc
                loc != null && loc != "RU"
            }
        } else {
            working.toList()
        }

        return filtered.sortedBy { it.responseTime }
    }

    private fun testProxyFull(proxy: ProxyInfo): Long {
        var socket: Socket? = null
        return try {
            val start = System.currentTimeMillis()
            socket = Socket()
            socket.connect(InetSocketAddress(proxy.host, proxy.port), 4000)
            socket.soTimeout = 3000

            val out = socket.getOutputStream()
            val inp = socket.getInputStream()

            // SOCKS5 Handshake
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val handshakeResp = ByteArray(2)
            if (inp.read(handshakeResp) != 2) return -1
            if (handshakeResp[0] != 0x05.toByte() || handshakeResp[1] != 0x00.toByte()) return -1

            // CONNECT к 1.1.1.1:80
            val connectReq = byteArrayOf(0x05, 0x01, 0x00, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x50)
            out.write(connectReq)
            out.flush()

            val connectResp = ByteArray(10)
            val bytesRead = inp.read(connectResp)
            if (bytesRead < 4) return -1

            if (connectResp[0] == 0x05.toByte() && connectResp[1] == 0x00.toByte()) {
                val elapsed = System.currentTimeMillis() - start
                socket.close()
                elapsed
            } else -1
        } catch (e: Exception) {
            -1
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun testMTProto(proxy: ProxyInfo): Long {
        return try {
            val start = System.currentTimeMillis()
            // Попытка TLS handshake напрямую
            try {
                val factory = javax.net.ssl.SSLSocketFactory.getDefault()
                val ssl = factory.createSocket(proxy.host, proxy.port) as javax.net.ssl.SSLSocket
                ssl.soTimeout = 4000
                ssl.startHandshake()
                val elapsed = System.currentTimeMillis() - start
                ssl.close()
                if (elapsed in 1..5000) elapsed else -1
            } catch (_: Exception) {
                // TLS не удался — проверяем просто TCP
                val socket = Socket()
                socket.connect(InetSocketAddress(proxy.host, proxy.port), 5000)
                val elapsed = System.currentTimeMillis() - start
                socket.close()
                if (elapsed in 1..3000) elapsed else -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * 🔑 ПРОВЕРКА РЕАЛЬНОГО IP ЧЕРЕЗ ПРОКСИ
     * @param proxyUrl формат "socks5://host:port" или null для прямого соединения
     * @return внешний IP или null при ошибке
     */
    fun checkIpThroughProxy(proxyUrl: String?): String? {
        return try {
            val url = URL("http://ipinfo.io/json")

            if (proxyUrl != null && proxyUrl.startsWith("socks5://")) {
                // Парсим прокси
                val parts = proxyUrl.removePrefix("socks5://").split(":")
                if (parts.size != 2) return null
                val host = parts[0]
                val port = parts[1].toIntOrNull() ?: return null

                // Создаём сокет через SOCKS5
                val socket = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port)))
                socket.connect(InetSocketAddress("ipinfo.io", 80), 5000)
                socket.soTimeout = 5000

                // Отправляем HTTP-запрос вручную
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()
                out.write("GET /json HTTP/1.1\r\nHost: ipinfo.io\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()

                // Читаем ответ
                val response = inp.bufferedReader().readText()
                socket.close()

                // Извлекаем IP из JSON
                Regex("\"ip\":\\s*\"([\\d.]+)\"").find(response)?.groupValues?.get(1)
            } else {
                // Прямое соединение (без прокси)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                Regex("\"ip\":\\s*\"([\\d.]+)\"").find(response)?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            Log.d(TAG, "IP check failed: ${e.message}")
            null
        }
    }

    fun testProxyQuick(proxy: ProxyInfo): Boolean = testProxyFull(proxy) > 0

    fun checkProxyLocation(proxy: ProxyInfo): String? {
        return try {
            val client = OkHttpClient.Builder()
                .proxy(java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress(proxy.host, proxy.port)))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("https://www.cloudflare.com/cdn-cgi/trace")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            response.close()

            val locMatch = Regex("loc=(\\w+)").find(body)
            val loc = locMatch?.groupValues?.get(1)
            Log.d(TAG, "Geo check ${proxy.host}:${proxy.port} -> loc=$loc")
            loc
        } catch (e: Exception) {
            Log.d(TAG, "Geo check failed ${proxy.host}:${proxy.port}: ${e.message}")
            null
        }
    }

    private fun cacheProxies(proxies: List<ProxyInfo>, cacheKey: String = CACHE_KEY, ttlKey: String = LAST_UPDATE_KEY) {
        try {
            val jsonArray = JSONArray()
            for (proxy in proxies.take(100)) {
                val obj = JSONObject().apply {
                    put("host", proxy.host)
                    put("port", proxy.port)
                    put("type", proxy.type.name)
                    put("secret", proxy.secret)
                    put("responseTime", proxy.responseTime)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(cacheKey, jsonArray.toString())
                .putLong(ttlKey, System.currentTimeMillis()).apply()
        } catch (e: Exception) {}
    }

    fun getCachedProxies(cacheKey: String = CACHE_KEY): List<ProxyInfo> {
        val proxies = mutableListOf<ProxyInfo>()
        try {
            val jsonString = prefs.getString(cacheKey, "")
            if (!jsonString.isNullOrEmpty()) {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val type = try { ProxyType.valueOf(obj.optString("type", "SOCKS5")) } catch (_: Exception) { ProxyType.SOCKS5 }
                    proxies.add(ProxyInfo(
                        obj.getString("host"),
                        obj.getInt("port"),
                        type,
                        obj.optString("secret", ""),
                        if (obj.has("method")) obj.getString("method") else null,
                        if (obj.has("password")) obj.getString("password") else null
                    ).apply {
                        responseTime = obj.optDouble("responseTime", 99.0)
                    })
                }
            }
        } catch (e: Exception) {}
        return proxies
    }
}

enum class ProxyType { SOCKS5, MTPROTO, SHADOWSOCKS, VLESS }

data class ProxyInfo(val host: String, val port: Int, val type: ProxyType = ProxyType.SOCKS5, val secret: String = "", val method: String? = null, val password: String? = null) {
    val url: String
        get() = when (type) {
            ProxyType.MTPROTO -> "mtproto://$host:$port"
            ProxyType.SOCKS5 -> "socks5://$host:$port"
            ProxyType.SHADOWSOCKS -> "ss://$method:$password@$host:$port"
            ProxyType.VLESS -> "vless://$secret@$host:$port?type=ws&security=tls"
        }
    var responseTime: Double = 99.0
    var location: String? = null
    val telegramUrl: String
        get() = when (type) {
            ProxyType.MTPROTO -> "tg://proxy?server=$host&port=$port&secret=$secret"
            ProxyType.SOCKS5 -> "tg://proxy?server=$host&port=$port&type=socks5"
            ProxyType.SHADOWSOCKS -> "tg://proxy?server=$host&port=$port" // unused
            ProxyType.VLESS -> "tg://proxy?server=$host&port=$port" // unused
        }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProxyInfo) return false
        return port == other.port && host == other.host && type == other.type
    }
    override fun hashCode(): Int = "$host:$port:$type".hashCode()
}