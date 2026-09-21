# -*- coding: utf-8 -*-
import json
import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# I will use a simple replacement for the Xray config JSON string in startXrayLocal
# First, find the JSON block. It starts with """ and ends with """
json_match = re.search(r'val configJson = """(.*?)"""', text, re.DOTALL)
if json_match:
    config_json = json_match.group(1)
    
    # Replace the routing rule for port 53
    # { "type": "field", "network": "udp", "port": "53", "outboundTag": "proxy" }
    # with
    # { "type": "field", "network": "udp", "port": "53", "outboundTag": "dns-out" }
    config_json = config_json.replace(
        '{ "type": "field", "network": "udp", "port": "53", "outboundTag": "proxy" }',
        '{ "type": "field", "network": "udp", "port": "53", "outboundTag": "dns-out" }'
    )
    
    # Add dns-out outbound
    outbounds_match = re.search(r'"outbounds": \[(.*?)\]', config_json, re.DOTALL)
    if outbounds_match:
        outbounds = outbounds_match.group(1)
        if '"tag": "dns-out"' not in outbounds:
            new_outbounds = outbounds + ',\n      {\n        "tag": "dns-out",\n        "protocol": "dns"\n      }'
            config_json = config_json.replace(outbounds, new_outbounds)
            
    # Add dns block at the beginning
    if '"dns": {' not in config_json:
        # Insert after "log": { ... },
        log_match = re.search(r'"log": \{.*?\},\n', config_json, re.DOTALL)
        if log_match:
            dns_block = '    "dns": {\n      "servers": [\n        "https+local://8.8.8.8/dns-query"\n      ]\n    },\n'
            config_json = config_json.replace(log_match.group(0), log_match.group(0) + dns_block)
            
    text = text.replace(json_match.group(1), config_json)
    
with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)
