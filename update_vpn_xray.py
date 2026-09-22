import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Remove startShadowsocksLocal and startXrayLocal
text = re.sub(r'private fun startShadowsocksLocal.*?\n    }\n', '', text, flags=re.DOTALL)
text = re.sub(r'private fun startXrayLocal.*?\n    }\n', '', text, flags=re.DOTALL)

# Add universal startXrayCore
xray_core_func = """
    private fun startXrayCore(remoteHost: String, remotePort: Int, method: String?, password: String?, localPort: Int) {
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
            xrayProcess = pb.start()
            
            Thread {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(xrayProcess!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d("XrayCore", line!!)
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("TunnelVpnService", "Error starting Xray-core", e)
        }
    }
"""

text = text.replace('private fun stopLocalProcesses() {', xray_core_func + '\n    private fun stopLocalProcesses() {')

# Rewrite the target assignment block
old_block = """                var targetIp = proxyIp
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
                        val pb = ProcessBuilder(argsList)
                        pb.directory(cacheDir)
                        pb.redirectErrorStream(true)
                        ciadpiProcess = pb.start()
                        
                        Thread {
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(ciadpiProcess!!.inputStream))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                Log.d("ByeDPI", line!!)
                            }
                        }.start()
                        
                        targetIp = "127.0.0.1"
                        targetPort = port
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }"""

new_block = """                var targetIp = "127.0.0.1"
                var targetPort = 10808
                
                val byedpiEnabled = prefs.getBoolean("byedpi", false)

                if (byedpiEnabled && method == null) {
                    // Direct ByeDPI Mode
                    try {
                        targetPort = 10800
                        val ciadpiPath = applicationInfo.nativeLibraryDir + "/libciadpi.so"
                        val byedpiArgsStr = prefs.getString("byedpi_args", "--split 1 --auto=torst --tlsrec 1+s") ?: "--split 1 --auto=torst --tlsrec 1+s"
                        val argsList = mutableListOf(ciadpiPath, "--port", targetPort.toString())
                        argsList.addAll(byedpiArgsStr.split(" ").filter { it.isNotBlank() })
                        val pb = ProcessBuilder(argsList)
                        pb.directory(cacheDir)
                        pb.redirectErrorStream(true)
                        ciadpiProcess = pb.start()
                        
                        Thread {
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(ciadpiProcess!!.inputStream))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                Log.d("ByeDPI", line!!)
                            }
                        }.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    // Unified Xray Architecture (SS, VLESS, SOCKS5)
                    startXrayCore(proxyIp, proxyPort, method, password, targetPort)
                }"""

text = text.replace(old_block, new_block)

# Stop sslocal in stopLocalProcesses
text = text.replace('ssProcess?.destroy()', '')

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)

print("TunnelVpnService refactored successfully.")
