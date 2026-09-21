# -*- coding: utf-8 -*-
import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace the whole configJson block
old_json_match = re.search(r'val configJson = """(.*?)"""', text, re.DOTALL)
if old_json_match:
    new_json = """{
  "log": {
    "loglevel": "warning"
  },
  "dns": {
    "servers": [
      "https+local://8.8.8.8/dns-query"
    ]
  },
  "inbounds": [{
    "port": $localSocksPort,
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
          "port": $remotePort,
          "users": [{ "id": "$uuid", "encryption": "none", "level": 0 }]
        }]
      },
      "streamSettings": {
        "network": "ws",
        "security": "tls",
        "tlsSettings": {
          "serverName": "$sni",
          "fingerprint": "randomized"
        },
        "wsSettings": {
          "path": "/",
          "headers": { "Host": "$sni" }
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
}"""
    text = text.replace(old_json_match.group(1), new_json)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)
