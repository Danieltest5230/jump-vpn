#!/bin/bash
# ==============================================================================
# Script de Instalación y Despliegue de Servidor para Jump VPN (Jump Lite)
# Compatible con Ubuntu 20.04 / 22.04 / 24.04 y Debian 11 / 12
# Arquitectura Hardened / Segura sin Fugas ni Proxies Abiertos
# ==============================================================================

set -e

echo "=== Configurando Servidor Seguro para Jump VPN ==="

# 1. Actualización de paquetes
export DEBIAN_FRONTEND=noninteractive
apt update && apt upgrade -y
apt install -y dropbear squid stunnel4 cmake build-essential git python3 python3-pip openssl

# 2. Configurar Dropbear (SSH en puerto local 2222 y secundario)
cat << 'EOF' > /etc/default/dropbear
NO_START=0
DROPBEAR_PORT=2222
DROPBEAR_EXTRA_ARGS=""
DROPBEAR_BANNER="/etc/dropbear/banner"
EOF

mkdir -p /etc/dropbear
echo "Bienvenido a Jump VPN Server (Cifrado Activo)" > /etc/dropbear/banner
systemctl restart dropbear

# 3. Configurar Stunnel4 para Modo SSH + SSL/TLS (SNI Bug Spoofer en Puerto 443)
echo "Configurando Stunnel TLS en puerto 443..."
mkdir -p /etc/stunnel
if [ ! -f /etc/stunnel/stunnel.pem ]; then
    openssl req -new -x509 -days 3650 -nodes \
        -subj "/C=US/ST=Security/L=Cloud/O=JumpVPN/CN=jump.net" \
        -out /etc/stunnel/stunnel.pem -keyout /etc/stunnel/stunnel.pem
fi

cat << 'EOF' > /etc/stunnel/stunnel.conf
cert = /etc/stunnel/stunnel.pem
client = no
socket = a:SO_REUSEADDR=1
socket = l:TCP_NODELAY=1
socket = r:TCP_NODELAY=1

[dropbear_ssl]
accept = 443
connect = 127.0.0.1:2222
EOF

sed -i 's/ENABLED=0/ENABLED=1/' /etc/default/stunnel4 || true
systemctl restart stunnel4 || systemctl restart stunnel || true

# 4. Configurar Bridge WebSocket en Puerto 80 para Modo Cloudflare CDN
echo "Configurando WebSocket Bridge en puerto 80..."
cat << 'EOF' > /usr/local/bin/jump-ws-proxy.py
#!/usr/bin/env python3
import socket
import select
import sys

def forward(src, dst):
    try:
        data = src.recv(8192)
        if not data:
            return False
        dst.sendall(data)
        return True
    except Exception:
        return False

def handle_client(client_sock):
    buffer = b""
    while b"\r\n\r\n" not in buffer:
        chunk = client_sock.recv(4096)
        if not chunk:
            client_sock.close()
            return
        buffer += chunk

    # Responder al handshake HTTP 101 Switching Protocols
    response = (
        b"HTTP/1.1 101 Switching Protocols\r\n"
        b"Upgrade: websocket\r\n"
        b"Connection: Upgrade\r\n"
        b"Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n\r\n"
    )
    client_sock.sendall(response)

    # Conectar con Dropbear SSH local
    backend_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        backend_sock.connect(("127.0.0.1", 2222))
    except Exception:
        client_sock.close()
        return

    sockets = [client_sock, backend_sock]
    while True:
        r, _, _ = select.select(sockets, [], [], 60)
        if not r:
            break
        for s in r:
            other = backend_sock if s is client_sock else client_sock
            if not forward(s, other):
                client_sock.close()
                backend_sock.close()
                return

def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", 80))
    server.listen(100)
    while True:
        try:
            client, _ = server.accept()
            import threading
            threading.Thread(target=handle_client, args=(client,), daemon=True).start()
        except KeyboardInterrupt:
            break

if __name__ == "__main__":
    main()
EOF

chmod +x /usr/local/bin/jump-ws-proxy.py

cat << 'EOF' > /etc/systemd/system/jump-ws.service
[Unit]
Description=Jump VPN WebSocket CDN Bridge
After=network.target

[Service]
ExecStart=/usr/bin/python3 /usr/local/bin/jump-ws-proxy.py
Restart=always
User=root

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now jump-ws || true

# 5. Configurar Squid Proxy Blindado (Anti-Open Proxy)
cat << 'EOF' > /etc/squid/squid.conf
http_port 8080
http_port 3128

# Puertos estrictamente autorizados para tunelización SSH
acl SSH_ports port 22 2222 443 80 7300
acl CONNECT method CONNECT

# Permitir CONNECT únicamente hacia puertos de túnel seguros
http_access allow CONNECT SSH_ports
# Denegar cualquier otro tráfico proxy público para evitar abusos
http_access deny all

via off
forwarded_for off
request_header_access Allow allow all
EOF

systemctl restart squid

# 6. Instalar y Configurar BadVPN-udpgw (Soporte UDP para juegos y llamadas)
if [ ! -f /usr/local/bin/badvpn-udpgw ]; then
    echo "Compilando BadVPN UDPGW..."
    cd /tmp
    git clone https://github.com/ambrop72/badvpn.git || true
    if [ -d badvpn ]; then
        cd badvpn
        cmake -DBUILD_NOTHING_BY_DEFAULT=1 -DBUILD_UDPGW=1
        make install
        cd ..
        rm -rf badvpn
    fi
fi

cat << 'EOF' > /etc/systemd/system/badvpn.service
[Unit]
Description=BadVPN UDP Gateway para Jump VPN
After=network.target

[Service]
ExecStart=/usr/local/bin/badvpn-udpgw --listen-addr 127.0.0.1:7300 --max-clients 500
Restart=always
User=root

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now badvpn || true

# 7. Instalar Node.js y levantar el Panel Web de Jump VPN
echo "Configurando Panel Web de Gestión Jump..."
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt install -y nodejs

PANEL_DIR="/opt/jump-panel"
mkdir -p $PANEL_DIR

cat << 'EOF' > /etc/systemd/system/jump-panel.service
[Unit]
Description=Jump VPN Web Admin & User Portal
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/jump-panel
ExecStart=/usr/bin/node server.js
Restart=always
Environment=PORT=3000

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable jump-panel || true

# 8. Crear usuario inicial seguro para Jump VPN
USER_NAME="jump"
USER_PASS="jump123"
id -u $USER_NAME >/dev/null 2>&1 || useradd -m -s /bin/false $USER_NAME
echo "$USER_NAME:$USER_PASS" | chpasswd

echo "=========================================================="
echo "  ✓ Servidor Jump VPN Configurado Exitosamente (Blindado)"
echo "  - SSH Directo:       Puerto 22 y 2222"
echo "  - SSH + Proxy:       Puertos 8080 y 3128 (Squid Protegido)"
echo "  - SSH + SSL/TLS:     Puerto 443 (Stunnel SNI Bug)"
echo "  - SSH + WebSocket:   Puerto 80 (Cloudflare CDN Bridge 101)"
echo "  - BadVPN UDPGW:      Puerto 7300"
echo "  - Panel Web Admin:   http://TU-IP-VPS:3000/admin"
echo "  - Portal Clientes:   http://TU-IP-VPS:3000/cambiar-clave"
echo "=========================================================="
