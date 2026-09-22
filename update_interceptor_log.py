import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunPacketInterceptor.kt', 'r', encoding='utf-8') as f:
    text = f.read()

target = """                        val packet = buffer.copyOfRange(0, length)
                        if (isIpv4UdpPort53(packet)) {"""
replacement = """                        val packet = buffer.copyOfRange(0, length)
                        // LOGGING:
                        if (DohResolver.DEBUG) {
                            val v = (packet[0].toInt() and 0xFF) >> 4
                            val p = if (packet.size > 9) packet[9].toInt() and 0xFF else -1
                            // Log.d(TAG, "Packet V:$v Prot:$p Len:$length")
                        }
                        if (isIpv4UdpPort53(packet)) {"""

# let's just log UDP packets
replacement2 = """                        val packet = buffer.copyOfRange(0, length)
                        if (packet.size > 20 && (packet[0].toInt() and 0xF0) == 0x40 && packet[9].toInt() == 17) {
                            val ipHL = (packet[0].toInt() and 0x0F) * 4
                            val dp = ((packet[ipHL + 2].toInt() and 0xFF) shl 8) or (packet[ipHL + 3].toInt() and 0xFF)
                            Log.d(TAG, "UDP Packet DstPort: $dp Len: $length")
                        }
                        if (isIpv4UdpPort53(packet)) {"""
text = text.replace(target, replacement2)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunPacketInterceptor.kt', 'w', encoding='utf-8') as f:
    f.write(text)
