import re

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace socketPair = ParcelFileDescriptor.createSocketPair() with Os.socketpair
old_sp = "socketPair = ParcelFileDescriptor.createSocketPair()"
new_sp = """val fds = java.io.FileDescriptor()
                    val fdsArray = android.system.Os.socketpair(android.system.OsConstants.AF_UNIX, android.system.OsConstants.SOCK_DGRAM, 0)
                    socketPair = arrayOf(
                        ParcelFileDescriptor(fdsArray[0]),
                        ParcelFileDescriptor(fdsArray[1])
                    )"""
if "Os.socketpair" not in text:
    text = text.replace(old_sp, new_sp)

with open(r'app\src\main\java\com\proxyvpn\tunnel\TunnelVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(text)
