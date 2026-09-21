import urllib.request
import urllib.error

url = "http://proxy-vpn.stk-lab7.workers.dev/"
req = urllib.request.Request(url, headers={'User-Agent': 'curl/7.68.0'})
try:
    with urllib.request.urlopen(req) as response:
        print("HTTP Status:", response.status)
        print("Response:", response.read().decode('utf-8')[:100])
except urllib.error.HTTPError as e:
    print("HTTP Error:", e.code)
    print("Headers:", e.headers)
except Exception as e:
    print("Error:", e)
