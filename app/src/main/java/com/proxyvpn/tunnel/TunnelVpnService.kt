package com.proxyvpn.tunnel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import java.io.File
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.proxyvpn.MainActivity
import com.proxyvpn.R
import kotlinx.coroutines.*

class TunnelVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var socketPair: Array<ParcelFileDescriptor>? = null
    private var byedpiProcess: Process? = null
        private var xrayProcess: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        const val ACTION_START = "com.proxyvpn.START_VPN"
        const val ACTION_STOP = "com.proxyvpn.STOP_VPN"
        const val EXTRA_PROXY_IP = "proxy_ip"
        const val EXTRA_PROXY_PORT = "proxy_port"
        
        private const val NOTIFICATION_CHANNEL_ID = "vpn_tunnel_channel"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val ip = intent.getStringExtra(EXTRA_PROXY_IP) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PROXY_PORT, 1080)
                scope.launch {
                    if (vpnInterface != null) {
                        NativeBridge.TProxyStopService()
                        vpnInterface?.close()
                        vpnInterface = null
                    }
                    startVpn(ip, port, intent.getStringExtra("method"), intent.getStringExtra("password"))
                }
            }
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private suspend fun startVpn(proxyIp: String, proxyPort: Int, method: String?, password: String?) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("proxy_favorites", android.content.Context.MODE_PRIVATE)
                val customDns = prefs.getString("custom_dns", "1.1.1.1") ?: "1.1.1.1"
                val customMtu = prefs.getString("custom_mtu", "1280")?.toIntOrNull() ?: 1280
                val encryptedDns = prefs.getBoolean("encrypted_dns", false)

                val builder = Builder()
                    .setSession("ProxyVPN Tunnel")
                    .setMtu(customMtu)
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(customDns)
                    
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                vpnInterface = builder.establish()
                
                val fd = vpnInterface?.fd ?: throw IllegalStateException("Не удалось создать TUN интерфейс")
                
                // Write config.yaml

                
                var targetIp = "127.0.0.1"
                var targetPort = 10808
                
                val byedpiEnabled = prefs.getBoolean("byedpi", false)

                // If no method, it's a plain SOCKS5/HTTP proxy.
                if (byedpiEnabled && method == null) {
                    try {
                        val ciadpiPath = applicationInfo.nativeLibraryDir + "/libciadpi.so"
                        targetPort = 10800

                        val byedpiArgsStr = prefs.getString("byedpi_args", "--split 1 --auto=torst --tlsrec 1+s") ?: "--split 1 --auto=torst --tlsrec 1+s"
                        val argsList = mutableListOf(ciadpiPath, "-p", targetPort.toString())
                        argsList.addAll(byedpiArgsStr.split(" ").filter { it.isNotBlank() })
                        if (!argsList.contains("-U")) argsList.add("-U")
                        
                        android.util.Log.d("ProxyVPN", "Starting ciadpi with args: $argsList")
                        byedpiProcess = ProcessBuilder(argsList)
                            .directory(cacheDir)
                            .redirectErrorStream(true)
                            .start()
                            
                        Thread {
                            try {
                                val reader = java.io.BufferedReader(java.io.InputStreamReader(byedpiProcess!!.inputStream))
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    android.util.Log.d("ByeDPI", line ?: "")
                                }
                            } catch (e: Exception) {}
                        }.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    startXrayCore(proxyIp, proxyPort, method, password, targetPort)
                }
                
                val configPath = cacheDir.absolutePath + "/config.yaml" 

                val configContent = """
                    tunnel:
                      name: "$fd"
                      mtu: $customMtu
                      ipv4:
                        address: 10.0.0.2
                        gateway: 10.0.0.1
                        mask: 255.255.255.0
                    socks5:
                      address: "$targetIp"
                      port: $targetPort
                      udp: udp
                    misc:
                      log-file: ${cacheDir.absolutePath}/hev.log
                      log-level: debug
                """.trimIndent()
                java.io.File(configPath).writeText(configContent)

                val success: Boolean
                if (encryptedDns) {
                    val fd1 = java.io.FileDescriptor()
                    val fd2 = java.io.FileDescriptor()
                    android.system.Os.socketpair(android.system.OsConstants.AF_UNIX, android.system.OsConstants.SOCK_DGRAM, 0, fd1, fd2)
                    socketPair = arrayOf(
                        ParcelFileDescriptor.dup(fd1),
                        ParcelFileDescriptor.dup(fd2)
                    )
                    val tunFd = vpnInterface!!
                    val proxyFd = socketPair!![1]
                    val interceptorFd = socketPair!![0]
                    
                    val dohResolver = DohResolver()
                    val interceptor = TunPacketInterceptor(tunFd, interceptorFd, dohResolver)
                    interceptor.start(scope)
                    
                    success = NativeBridge.TProxyStartService(configPath, proxyFd.fd)
                } else {
                    success = NativeBridge.TProxyStartService(configPath, fd)
                }
                
                if (success) {
                    updateNotification("Подключение к $proxyIp")
                } else {
                    stopVpn()
                }
            } catch (e: Exception) {
                Log.e("TunnelVpnService", "Ошибка VPN: ${e.message}")
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        scope.launch {
            NativeBridge.TProxyStopService()
            socketPair?.forEach { try { it.close() } catch(e:Exception){} }
            socketPair = null
            vpnInterface?.close()
            vpnInterface = null
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
            
        byedpiProcess?.destroy()
                xrayProcess?.destroy()
        byedpiProcess = null
        stopSelf()

        }
    }

    private fun createNotification(contentText: String): android.app.Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, TunnelVpnService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val uiIntent = Intent(this, MainActivity::class.java)
        val pendingUi = PendingIntent.getActivity(this, 0, uiIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ProxyVPN Tunnel")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher) // Fallback icon
            .setContentIntent(pendingUi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", pendingStop)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        scope.cancel()
    }

    private fun startXrayCore(remoteHost: String, remotePort: Int, method: String?, password: String?, localPort: Int) {
        try {
            val xrayPath = java.io.File(applicationInfo.nativeLibraryDir, "libxray.so").absolutePath
            val configFile = java.io.File(cacheDir, "xray_config.json")
            
            val resolvedIp = try {
                java.net.InetAddress.getAllByName(remoteHost).firstOrNull { it is java.net.Inet4Address }?.hostAddress ?: java.net.InetAddress.getByName(remoteHost).hostAddress
            } catch (e: Exception) {
                remoteHost
            }

            var outboundJson = ""
            
            if (method == "VLESS") {
                outboundJson = """{
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "$resolvedIp",
                            "port": $remotePort,
                            "users": [{ "id": "$password", "encryption": "none", "level": 0 }]
                        }]
                    },
                    "streamSettings": {
                        "network": "ws",
                        "security": "tls",
                        "tlsSettings": {
                            "serverName": "$remoteHost",
                            "allowInsecure": false,
                            "fingerprint": "chrome"
                        },
                        "wsSettings": {
                            "path": "/?ed=2048",
                            "host": "$remoteHost"
                        },
                        "sockopt": { "dialerProxy": "fragment-out", "tcpNoDelay": true }
                    }
                },
                {
                    "tag": "fragment-out",
                    "protocol": "freedom",
                    "settings": {
                        "fragment": { "packets": "1-3", "length": "20-50", "interval": "10-20" }
                    },
                    "streamSettings": { "sockopt": { "tcpNoDelay": true } }
                }"""
            } else if (method != null && password != null) {
                outboundJson = """{
                    "tag": "proxy",
                    "protocol": "shadowsocks",
                    "settings": {
                        "servers": [{
                            "address": "$resolvedIp",
                            "port": $remotePort,
                            "method": "$method",
                            "password": "$password",
                            "level": 0
                        }]
                    }
                }"""
            } else {
                outboundJson = """{
                    "tag": "proxy",
                    "protocol": "socks",
                    "settings": {
                        "servers": [{
                            "address": "$resolvedIp",
                            "port": $remotePort
                        }]
                    }
                }"""
            }

            val configJson = """{
                "log": { "loglevel": "debug" },
                "dns": { "servers": ["https+local://8.8.8.8/dns-query"] },
                "inbounds": [{
                    "port": $localPort,
                    "listen": "127.0.0.1",
                    "protocol": "socks",
                    "settings": { "udp": true },
                    "sniffing": { "enabled": true, "destOverride": ["http", "tls"] }
                }],
                "routing": {
                    "domainStrategy": "AsIs",
                    "rules": [
                        { "type": "field", "network": "udp", "port": "53", "outboundTag": "dns-out" },
                        { "type": "field", "network": "tcp", "outboundTag": "proxy" },
                        { "type": "field", "network": "udp", "outboundTag": "proxy" }
                    ]
                },
                "outbounds": [
                    $outboundJson,
                    { "tag": "dns-out", "protocol": "dns" }
                ]
            }"""

            configFile.writeText(configJson)
            
            android.util.Log.i("TunnelVpnService", "Starting Xray-core -> $remoteHost:$remotePort (Method: $method)")
            val xrayLogFile = java.io.File(cacheDir, "xray.log")
            val pb = ProcessBuilder(xrayPath, "-c", configFile.absolutePath)
            pb.directory(cacheDir)
            pb.redirectErrorStream(true)
            pb.environment()["SSL_CERT_DIR"] = "/system/etc/security/cacerts"
            xrayProcess = pb.start()
            
            Thread {
                try {
                    val writer = xrayLogFile.bufferedWriter()
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(xrayProcess!!.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        android.util.Log.d("XrayCore", line ?: "")
                        writer.write(line ?: "")
                        writer.newLine()
                        writer.flush()
                    }
                    writer.close()
                    val exitCode = xrayProcess!!.waitFor()
                    android.util.Log.e("XrayCore", "Xray process exited with code: $exitCode")
                } catch (e: Exception) {
                    android.util.Log.e("XrayCore", "Xray thread error: ${e.message}")
                }
            }.start()
            
            Thread.sleep(1000) // Wait for Xray to initialize
        } catch (e: Exception) {
            android.util.Log.e("TunnelVpnService", "Failed to start Xray: ${e.message}")
        }
    }
}

