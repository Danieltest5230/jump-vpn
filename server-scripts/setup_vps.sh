#!/bin/bash
# ==============================================================================
# Script de Instalación y Despliegue de Servidor para Jump VPN (Jump Lite)
# Compatible con Ubuntu 20.04 / 22.04 / 24.04 y Debian 11 / 12
# ==============================================================================

set -e

echo "=== Configurando Servidor para Jump VPN ==="

# 1. Actualización de paquetes
apt update && apt upgrade -y
apt install -y dropbear squid stunnel4 cmake build-essential git python3 python3-pip

# 2. Configurar Dropbear (SSH ligero alternativo en puerto 443 o 2222)
cat << 'EOF' > /etc/default/dropbear
NO_START=0
DROPBEAR_PORT=2222
DROPBEAR_EXTRA_ARGS="-p 80 -p 443"
DROPBEAR_BANNER="/etc/dropbear/banner"
EOF

echo "Bienvenido a Jump VPN Server" > /etc/dropbear/banner
systemctl restart dropbear

# 3. Configurar Squid Proxy (Para modo SSH + Payload)
cat << 'EOF' > /etc/squid/squid.conf
http_port 8080
http_port 3128

acl all src 0.0.0.0/0
acl SSL_ports port 443 80 22 2222 7300
acl Safe_ports port 80
acl Safe_ports port 443
acl Safe_ports port 22
acl Safe_ports port 2222
acl CONNECT method CONNECT

http_access allow CONNECT
http_access allow all
via off
forwarded_for off
request_header_access Allow allow all
EOF

systemctl restart squid

# 4. Instalar y Configurar BadVPN-udpgw (Soporte UDP para juegos y llamadas)
if [ ! -f /usr/local/bin/badvpn-udpgw ]; then
    echo "Compilando BadVPN UDPGW..."
    cd /tmp
    git clone https://github.com/ambrop72/badvpn.git || true
    if [ -d badvpn ]; then
        cd badvpn
        cmake -DBUILD_NOTHING_BY_DEFAULT=1 -DBUILD_UDPGW=1
        make install
    fi
fi

# Crear servicio systemd para badvpn-udpgw (Puerto 7300)
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
systemctl enable --now badvpn

# 5. Instalar Node.js y levantar el Panel Web de Jump VPN
echo "Configurando Panel Web de Gestión Jump..."
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt install -y nodejs

PANEL_DIR="/opt/jump-panel"
mkdir -p $PANEL_DIR
# El contenido de la carpeta panel se copia o sincroniza aquí
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
Environment=ADMIN_USER=admin
Environment=ADMIN_PASS=jumpadmin2026

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable jump-panel || true

# 6. Crear usuario de prueba para Jump VPN
USER_NAME="jump"
USER_PASS="jump123"
useradd -m -s /bin/false $USER_NAME || true
echo "$USER_NAME:$USER_PASS" | chpasswd

echo "=========================================================="
echo "  Servidor y Panel Jump VPN configurados correctamente!"
echo "  - SSH / Dropbear: Puertos 22, 80, 443, 2222"
echo "  - Proxy Squid:    Puertos 8080, 3128"
echo "  - BadVPN UDPGW:   Puerto 7300"
echo "  - Panel Admin:    http://TU-IP-VPS:3000/admin"
echo "  - Portal Usuario: http://TU-IP-VPS:3000/cambiar-clave"
echo "  - Admin Usuario:  admin"
echo "  - Admin Clave:    jumpadmin2026"
echo "=========================================================="
