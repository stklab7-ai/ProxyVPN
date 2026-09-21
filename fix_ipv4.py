# -*- coding: utf-8 -*-
with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

old_resolve = 'java.net.InetAddress.getByName(remoteHost).hostAddress'
new_resolve = 'java.net.InetAddress.getAllByName(remoteHost).firstOrNull { it is java.net.Inet4Address }?.hostAddress ?: java.net.InetAddress.getByName(remoteHost).hostAddress'
text = text.replace(old_resolve, new_resolve)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)
