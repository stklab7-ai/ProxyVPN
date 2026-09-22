with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

target1 = """                    val builder = Builder()
                        .setSession("ProxyVPN Tunnel")
                        .setMtu(customMtu)
                        .addAddress("10.0.0.2", 32)
                        .addRoute("0.0.0.0", 0)
                        .addDnsServer(customDns)"""

replacement1 = """                    val builder = Builder()
                        .setSession("ProxyVPN Tunnel")
                        .setMtu(customMtu)
                        .addAddress("10.0.0.2", 32)
                        .addAddress("fc00::2", 128)
                        .addRoute("0.0.0.0", 0)
                        .addRoute("::", 0)
                        .addDnsServer(customDns)"""

target2 = """                      tunnel:
                        name: "$fd"
                        mtu: $customMtu
                        ipv4:
                          address: 10.0.0.2
                          gateway: 10.0.0.1
                          mask: 255.255.255.0
                      socks5:"""

replacement2 = """                      tunnel:
                        name: "$fd"
                        mtu: $customMtu
                        ipv4:
                          address: 10.0.0.2
                          gateway: 10.0.0.1
                          mask: 255.255.255.0
                        ipv6:
                          address: "fc00::2"
                          gateway: "fc00::1"
                          prefix: 128
                      socks5:"""

text = text.replace(target1, replacement1).replace(target2, replacement2)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)
