# -*- coding: utf-8 -*-
with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Change fingerprint to randomized
text = text.replace('"fingerprint": "chrome"', '"fingerprint": "randomized"')

# Remove fragment-out dialerProxy
import re
text = re.sub(r',\s*"sockopt": \{\s*"dialerProxy": "fragment-out"\s*\}', '', text)

# Remove the fragment-out outbound block
text = re.sub(r',\s*\{\s*"tag": "fragment-out",\s*"protocol": "freedom",\s*"settings": \{\s*"fragment": \{\s*"packets": "tlshello",\s*"length": "100-200",\s*"interval": "1-5"\s*\}\s*\}\s*\}', '', text)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)
