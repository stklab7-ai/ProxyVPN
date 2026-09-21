# -*- coding: utf-8 -*-
import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace the whole outbounds block
old_outbounds_match = re.search(r'"outbounds": \[(.*?)\]\n\}"""', text, re.DOTALL)
if old_outbounds_match:
    new_outbounds = """"outbounds": [
    {
      "tag": "proxy",
      "protocol": "vless",
      "settings": {
        "vnext": [{
          "address": "$resolvedIp",
          "port": 80,
          "users": [{ "id": "$uuid", "encryption": "none", "level": 0 }]
        }]
      },
      "streamSettings": {
        "network": "ws",
        "security": "none",
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
}\"\"\""""
    text = text.replace(old_outbounds_match.group(0), new_outbounds)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)
