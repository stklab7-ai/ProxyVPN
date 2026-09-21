import urllib.request
try:
    req = urllib.request.Request('https://proxy-vpn.stk-lab7.workers.dev/e1830450-8ac0-4850-be71-3afe3ab603b2', headers={'User-Agent': 'Mozilla/5.0'})
    response = urllib.request.urlopen(req)
    print(response.read().decode('utf-8'))
except Exception as e:
    print(f"Error: {e}")
