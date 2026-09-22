import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Remove ssProcess references
text = text.replace('private var ssProcess: Process? = null\n', '')
text = text.replace('ssProcess?.destroy()\n', '')

# 2. Replace routing logic
routing_pattern = re.compile(r'var targetIp = proxyIp.*?val configPath = cacheDir.absolutePath \+ "/config\.yaml"', re.DOTALL)
routing_replacement = """var targetIp = "127.0.0.1"
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
                
                val configPath = cacheDir.absolutePath + "/config.yaml" """
text = routing_pattern.sub(routing_replacement, text)

# 3. Replace startXrayLocal and startShadowsocksLocal
methods_pattern = re.compile(r'private fun startXrayLocal\(.*?$', re.DOTALL)

xray_core_func = """private fun startXrayCore(remoteHost: String, remotePort: Int, method: String?, password: String?, localPort: Int) {
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
                outboundJson = \"\"\"{
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "$resolvedIp",
                            "port": remotePort,
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
                            "headers": { "Host": "$remoteHost" }
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
                }\"\"\"
            } else if (method != null && password != null) {
                outboundJson = \"\"\"{
                    "tag": "proxy",
                    "protocol": "shadowsocks",
                    "settings": {
                        "servers": [{
                            "address": "$resolvedIp",
                            "port": remotePort,
                            "method": "$method",
                            "password": "$password",
                            "level": 0
                        }]
                    }
                }\"\"\"
            } else {
                outboundJson = \"\"\"{
                    "tag": "proxy",
                    "protocol": "socks",
                    "settings": {
                        "servers": [{
                            "address": "$resolvedIp",
                            "port": remotePort
                        }]
                    }
                }\"\"\"
            }

            val configJson = \"\"\"{
                "log": { "loglevel": "debug" },
                "dns": { "servers": ["https+local://8.8.8.8/dns-query"] },
                "inbounds": [{
                    "port": localPort,
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
            }\"\"\"

            configFile.writeText(configJson)
            
            android.util.Log.i("TunnelVpnService", "Starting Xray-core -> $remoteHost:$remotePort (Method: $method)")
            val pb = ProcessBuilder(xrayPath, "-c", configFile.absolutePath)
            pb.directory(cacheDir)
            pb.redirectErrorStream(true)
            pb.environment()["SSL_CERT_DIR"] = "/system/etc/security/cacerts"
            xrayProcess = pb.start()
            
            Thread {
                try {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(xrayProcess!!.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        android.util.Log.d("XrayCore", line ?: "")
                    }
                } catch (e: Exception) {}
            }.start()
            
            Thread.sleep(1000) // Wait for Xray to initialize
        } catch (e: Exception) {
            android.util.Log.e("TunnelVpnService", "Failed to start Xray: ${e.message}")
        }
    }
}
"""

text = methods_pattern.sub(xray_core_func, text)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)

print("done")
