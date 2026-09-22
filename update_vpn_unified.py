import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace startXrayLocal and startShadowsocksLocal with startXrayCore
# We will just find where they are defined and replace everything to the end of the class,
# BUT wait, stopVpn is below them.

# Let's find "private fun startXrayLocal" and everything after it, then we can manipulate it better.
# We know the methods are defined at the end of the file.
start_idx = text.find('private fun startXrayLocal')
end_idx = text.find('private fun stopVpn()')

if start_idx != -1 and end_idx != -1:
    before = text[:start_idx]
    after = text[end_idx:]
else:
    print("Could not find startXrayLocal or stopVpn!")
    exit(1)

xray_core_func = """
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
                    { "tag": "dns-out", "protocol": "dns" },
                    { "tag": "block", "protocol": "blackhole" }
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
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("TunnelVpnService", "Error starting Xray-core", e)
        }
    }

"""

text = before + xray_core_func + after

# Now replace the routing block in startVpn
routing_start = text.find('var targetIp = proxyIp')
routing_end = text.find('val configPath = cacheDir.absolutePath + "/config.yaml"')

if routing_start != -1 and routing_end != -1:
    before_routing = text[:routing_start]
    after_routing = text[routing_end:]
    
    new_routing = """var targetIp = "127.0.0.1"
                var targetPort = 10808
                
                val byedpiEnabled = prefs.getBoolean("byedpi", false)

                if (byedpiEnabled && method == null) {
                    // Direct ByeDPI Mode
                    try {
                        targetPort = 10800
                        val ciadpiPath = applicationInfo.nativeLibraryDir + "/libciadpi.so"
                        val byedpiArgsStr = prefs.getString("byedpi_args", "--split 1 --auto=torst --tlsrec 1+s") ?: "--split 1 --auto=torst --tlsrec 1+s"
                        val argsList = mutableListOf(ciadpiPath, "-p", targetPort.toString())
                        argsList.addAll(byedpiArgsStr.split(" ").filter { it.isNotBlank() })
                        val pb = ProcessBuilder(argsList)
                        pb.directory(cacheDir)
                        pb.redirectErrorStream(true)
                        ciadpiProcess = pb.start()
                        
                        Thread {
                            try {
                                val reader = java.io.BufferedReader(java.io.InputStreamReader(ciadpiProcess!!.inputStream))
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
                    // Unified Xray Architecture (SS, VLESS, HTTP, SOCKS5)
                    startXrayCore(proxyIp, proxyPort, method, password, targetPort)
                }

                """
    text = before_routing + new_routing + after_routing
else:
    print("Could not find routing block!")
    exit(1)

# Remove ssProcess
text = text.replace('var ssProcess: Process? = null', '')
text = text.replace('ssProcess?.destroy()', '')

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)

print("TunnelVpnService unified refactoring complete.")
