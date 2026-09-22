import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Add a field to store socketPair so we can close it
if "private var socketPair: Array<ParcelFileDescriptor>? = null" not in text:
    text = text.replace("private var vpnInterface: ParcelFileDescriptor? = null",
                        "private var vpnInterface: ParcelFileDescriptor? = null\n    private var socketPair: Array<ParcelFileDescriptor>? = null")

# In stopVpn()
stop_replacement = """NativeBridge.TProxyStopService()
            socketPair?.forEach { try { it.close() } catch(e:Exception){} }
            socketPair = null
            vpnInterface?.close()"""
text = text.replace("""NativeBridge.TProxyStopService()
            vpnInterface?.close()""", stop_replacement)

# In startVpn(), read prefs for encryptedDns
if "val encryptedDns = prefs.getBoolean(\"encrypted_dns\", false)" not in text:
    text = text.replace("val customMtu = prefs.getString(\"custom_mtu\", \"1280\")?.toIntOrNull() ?: 1280",
                        "val customMtu = prefs.getString(\"custom_mtu\", \"1280\")?.toIntOrNull() ?: 1280\n                val encryptedDns = prefs.getBoolean(\"encrypted_dns\", false)")

# Modify the NativeBridge.TProxyStartService call
old_start_call = """                val success = NativeBridge.TProxyStartService(configPath, fd)
                if (success) {"""
new_start_call = """                val success: Boolean
                if (encryptedDns) {
                    socketPair = ParcelFileDescriptor.createSocketPair()
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
                
                if (success) {"""
if "val interceptor = TunPacketInterceptor" not in text:
    text = text.replace(old_start_call, new_start_call)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)
