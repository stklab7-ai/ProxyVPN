with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('private var ssProcess: Process? = null', '')
text = text.replace('ssProcess?.destroy()', '')

old_routing = """                var targetIp = proxyIp
                var targetPort = proxyPort
                var localSocksPort = 10800
                
                if (method == "VLESS" && password != null) {
                    localSocksPort = 10801
                    startXrayLocal(proxyIp, proxyPort, password, proxyIp, localSocksPort)
                    targetIp = "127.0.0.1"
                    targetPort = localSocksPort
                } else if (method != null && password != null && method != "VLESS") {
                    localSocksPort = 10802
                    startShadowsocksLocal(proxyIp, proxyPort, method, password, localSocksPort)
                    targetIp = "127.0.0.1"
                    targetPort = localSocksPort
                }
                
                val byedpiEnabled = prefs.getBoolean("byedpi", false)

                // Skip ByeDPI if using a custom protocol like VLESS/Shadowsocks
                if (byedpiEnabled && method == null) {
                    try {
                        val ciadpiPath = applicationInfo.nativeLibraryDir + "/libciadpi.so"
                        val port = 10800

                        val byedpiArgsStr = prefs.getString("byedpi_args", "--split 1 --auto=torst --tlsrec 1+s") ?: "--split 1 --auto=torst --tlsrec 1+s"
                        val argsList = mutableListOf(ciadpiPath, "--port", port.toString())
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
                                    android.util.Log.d("ProxyVPN-ciadpi", line ?: "")
                                }
                            } catch (e: Exception) {}
                        }.start()
                        
                        targetIp = "127.0.0.1"
                        targetPort = port
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }"""

new_routing = """                var targetIp = "127.0.0.1"
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
                }"""

text = text.replace(old_routing, new_routing)

xray_old_func = """    private fun startXrayLocal(remoteHost: String, remotePort: Int, password: String, sni: String, localPort: Int) {
        try {
            val xrayPath = File(applicationInfo.nativeLibraryDir, "libxray.so").absolutePath
            val configFile = File(cacheDir, "xray_config.json")
            
            val resolvedIp = try {
                java.net.InetAddress.getAllByName(remoteHost).firstOrNull { it is java.net.Inet4Address }?.hostAddress ?: java.net.InetAddress.getByName(remoteHost).hostAddress
            } catch (e: Exception) {
                "104.21.93.131" // fallback to CF worker IP
            }

            // Generate Xray config for VLESS over WebSocket (Cloudflare)
            val configJson = \"\"\"{
  "log": {
    "loglevel": "debug"
  },
  "dns": {
    "servers": [
      "https+local://8.8.8.8/dns-query"
    ]
  },
  "inbounds": [{
    "port": $localPort,
    "listen": "127.0.0.1",
    "protocol": "socks",
    "settings": {
      "udp": true
    },
    "sniffing": {
      "enabled": true,
      "destOverride": ["http", "tls"]
    }
  }],
  "routing": {
    "domainStrategy": "AsIs",
    "rules": [
      { "type": "field", "network": "udp", "port": "53", "outboundTag": "dns-out" },
      { "type": "field", "network": "udp", "outboundTag": "block" },
      { "type": "field", "network": "tcp", "outboundTag": "proxy" }
    ]
  },
  "outbounds": [
    {
      "tag": "proxy",
        "protocol": "vless",
        "settings": {
          "vnext": [{
            "address": "$resolvedIp",
            "port": 443,
            "users": [{ "id": "$password", "encryption": "none", "level": 0 }]
          }]
        },
        "streamSettings": {
          "network": "ws",
          "security": "tls",
          "tlsSettings": {
            "serverName": "$sni",
            "allowInsecure": false,
            "fingerprint": "chrome"
          },
          "wsSettings": {
            "path": "/?ed=2048",
            "headers": { "Host": "$sni" }
          },
          "sockopt": {
            "dialerProxy": "fragment-out",
            "tcpNoDelay": true
          }
        }
    },
    {
      "tag": "fragment-out",
      "protocol": "freedom",
      "settings": {
        "fragment": {
          "packets": "1-3",
          "length": "20-50",
          "interval": "10-20"
        }
      },
      "streamSettings": {
        "sockopt": {
          "tcpNoDelay": true
        }
      }
    },
    {
      "tag": "block",
      "protocol": "blackhole"
    },
    {
      "tag": "dns-out",
      "protocol": "dns"
    }
  ]
}\"\"\".trimIndent()
            
            configFile.writeText(configJson)
            
            Log.i("TunnelVpnService", "Starting Xray-core for VLESS -> $remoteHost:$remotePort (SNI: $sni)")
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
                        Log.d("XrayCore", line ?: "")
                    }
                } catch (e: Exception) {}
            }.start()
            
            Thread.sleep(1000) // Wait for Xray to initialize
        } catch (e: Exception) {
            Log.e("TunnelVpnService", "Failed to start Xray: ${e.message}")
        }
    }"""

ss_old_func = """    private fun startShadowsocksLocal(remoteHost: String, remotePort: Int, method: String, password: String, localPort: Int) {
        try {
            val ssPath = File(applicationInfo.nativeLibraryDir, "libsslocal.so").absolutePath
            val configFile = File(cacheDir, "ss_config.json")
            val configJson = \"\"\"{
              "server": "$remoteHost",
              "server_port": $remotePort,
              "password": "$password",
              "method": "$method",
              "local_address": "127.0.0.1",
              "local_port": $localPort
            }\"\"\"
            configFile.writeText(configJson)
            
            Log.i("TunnelVpnService", "Starting sslocal for SS -> $remoteHost:$remotePort")
            val pb = ProcessBuilder(ssPath, "-c", configFile.absolutePath)
            pb.directory(cacheDir)
            pb.redirectErrorStream(true)
            ssProcess = pb.start()
            Thread {
                try {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(ssProcess!!.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d("Shadowsocks", line ?: "")
                    }
                } catch (e: Exception) {}
            }.start()
            Thread.sleep(1000)
        } catch (e: Exception) {
            Log.e("TunnelVpnService", "Failed to start SS: ${e.message}")
        }
    }"""

xray_core_func = """    private fun startXrayCore(remoteHost: String, remotePort: Int, method: String?, password: String?, localPort: Int) {
        try {
            val xrayPath = File(applicationInfo.nativeLibraryDir, "libxray.so").absolutePath
            val configFile = File(cacheDir, "xray_config.json")
            
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
                // Shadowsocks
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
                // SOCKS5 (Free Proxies)
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
            
            Log.i("TunnelVpnService", "Starting Xray-core -> $remoteHost:$remotePort (Method: $method)")
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
                        Log.d("XrayCore", line ?: "")
                    }
                } catch (e: Exception) {}
            }.start()
            
            Thread.sleep(1000)
        } catch (e: Exception) {
            Log.e("TunnelVpnService", "Failed to start Xray: ${e.message}")
        }
    }"""

text = text.replace(xray_old_func, xray_core_func)
text = text.replace(ss_old_func, "")

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)

print("done!")
