import socket
import ssl

def test_sni(sni, host):
    context = ssl.create_default_context()
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    
    try:
        s = socket.create_connection(('104.21.75.121', 443), timeout=5)
        ssock = context.wrap_socket(s, server_hostname=sni)
        
        req = f"GET / HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\n\r\n"
        ssock.sendall(req.encode('utf-8'))
        
        resp = ssock.recv(1024)
        print(f"SNI: {sni}, Host: {host} -> Status:", resp.decode('utf-8').split('\r\n')[0])
    except Exception as e:
        print(f"SNI: {sni}, Host: {host} -> Error:", e)

test_sni('www.udemy.com', 'proxy-vpn.stk-lab7.workers.dev')
test_sni('discord.com', 'proxy-vpn.stk-lab7.workers.dev')
test_sni('cloudflare.com', 'proxy-vpn.stk-lab7.workers.dev')
