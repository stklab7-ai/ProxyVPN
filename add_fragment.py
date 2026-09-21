# -*- coding: utf-8 -*-
import json
import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace outbounds
old_outbounds_match = re.search(r'"outbounds": \[(.*?)\]\n\}"""', text, re.DOTALL)
if old_outbounds_match:
    new_outbounds = """"outbounds": [
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
          "packets": "tlshello",
          "length": "100-200",
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
}\"\"\""""
    text = text.replace(old_outbounds_match.group(0), new_outbounds)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)
