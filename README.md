# Jump VPN (Jump Lite) - Arquitectura y Documentación

**Identificador de Paquete:** `com.jump.lite`  
**Nombre de la App:** Jump  
**Versión:** 4.6.2  
**Target OS:** Android 7.1+ (API 25 a API 35)  
**Arquitecturas:** `arm64-v8a`, `armeabi-v7a`  
**Tipo de Software:** VPN & Inyector de Redes Móviles / Tunelizador SSH, SSL/TLS & WebSocket  

---

## 🚀 Descripción del Proyecto

**Jump** es una aplicación de tunelización VPN diseñada para dispositivos móviles Android, orientada a cifrar el tráfico de red, reducir latencia y permitir el acceso a internet a través de servidores intermediarios evadiendo restricciones de operadoras móviles y redes Wi-Fi públicas.

Inspirada en la arquitectura de herramientas de referencia como *RayFlash* (SSH T PROJECT), *HTTP Custom* y *HTTP Injector*, Jump implementa:

1. **Android VpnService Nativo:** Crea una interfaz virtual `tun0` para capturar todo el tráfico del dispositivo y redirigirlo a un proxy Socks5 local.
2. **Motor de Inyección de Payloads:** Generador y parser de cabeceras HTTP personalizadas con sustitución dinámica de variables (`[host]`, `[port]`, `[host_port]`, `[crlf]`, `[protocol]`, `[ua]`, etc.).
3. **Múltiples Modos de Conexión:**
   - **SSH Directo:** Conexión directa SSH TCP a un servidor remoto.
   - **SSH + HTTP Proxy (Payload):** Inyección HTTP con bugs de operadoras a través de proxies remotos (Squid/Privoxy).
   - **SSH + SSL/TLS (SNI):** Cifrado TLS con *Server Name Indication* engañoso (Bug Host) para pasar por firewalls basados en inspección SNI.
   - **SSH + WebSocket (CDN):** Conexión vía HTTP Upgrade 101 a través de redes CDN (como Cloudflare) en puertos 80/443.
4. **Soporte UDP Gateway (BadVPN udpgw):** Reenvío de tráfico UDP en el puerto 7300 para llamadas de voz/video (WhatsApp) y juegos en línea.
5. **Reconexión Automática y Ahorro de Energía:** Ignora la optimización de batería de Android y reacciona a cambios en la antena de red celular.

---

## 📂 Estructura del Repositorio

```
vpn-mobile-app/
├── android/                         # Proyecto Nativo Android Studio (Kotlin/Java)
│   ├── app/
│   │   ├── build.gradle.kts         # Configuración del módulo de la app
│   │   └── src/main/
│   │       ├── AndroidManifest.xml  # Registro de 18 permisos y VpnService
│   │       ├── java/com/jump/lite/
│   │       │   ├── core/            # JumpVpnService, TunnelManager, Tun2Socks
│   │       │   ├── core/payload/    # PayloadEngine, PayloadGenerator
│   │       │   ├── core/tunnel/     # SshTunnelClient, SslSniSocket, WebSocketTunnel
│   │       │   ├── model/           # VpnProfile, TunnelMode, ConnectionStats
│   │       │   ├── receiver/        # BootReceiver, NetworkChangeReceiver
│   │       │   ├── ui/              # MainActivity, PayloadActivity, ServerListActivity
│   │       │   └── utils/           # BatteryOptimizationHelper, DnsHelper
│   │       └── res/                 # Layouts XML, Drawables, Colores, Temas
│   ├── build.gradle.kts             # Gradle a nivel de proyecto
│   └── settings.gradle.kts
├── panel/                           # Panel Web de Gestión y Autogestión de Usuarios
│   ├── server.js                    # Backend Express + API REST de usuarios y Linux PAM
│   ├── render.yaml                  # Despliegue gratuito en 1 clic para Render.com
│   └── public/
│       ├── admin.html               # Panel del Administrador (crear usuarios, días de vigencia)
│       ├── cambiar-clave.html       # Portal del Usuario (cambio de clave y consulta de días)
│       └── style.css
├── web-preview/                     # Simulador Interactivo Web y Generador de Payloads
│   ├── index.html                   # Interfaz visual interactiva idéntica a la App móvil
│   ├── app.js                       # Lógica de simulación, estados, métricas y payloads
│   └── styles.css                   # Diseño Neumórfico / Glassmorphism futurista
├── server-scripts/                  # Scripts para desplegar tu propio VPS para Jump
│   └── setup_vps.sh                 # Script bash para instalar SSH, Dropbear, Squid, BadVPN y Panel
└── .github/workflows/
    └── build-apk.yml                # CI/CD para compilar el APK automáticamente en GitHub Actions
```

---

## 🛡️ Los 18 Permisos Registrados en AndroidManifest.xml

1. `android.permission.INTERNET`
2. `android.permission.ACCESS_NETWORK_STATE`
3. `android.permission.CHANGE_NETWORK_STATE`
4. `android.permission.ACCESS_WIFI_STATE`
5. `android.permission.CHANGE_WIFI_STATE`
6. `android.permission.FOREGROUND_SERVICE`
7. `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` (Android 14+)
8. `android.permission.WAKE_LOCK`
9. `android.permission.RECEIVE_BOOT_COMPLETED`
10. `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
11. `android.permission.POST_NOTIFICATIONS`
12. `android.permission.ACCESS_FINE_LOCATION`
13. `android.permission.ACCESS_COARSE_LOCATION`
14. `android.permission.VIBRATE`
15. `android.permission.READ_EXTERNAL_STORAGE`
16. `android.permission.WRITE_EXTERNAL_STORAGE`
17. `com.google.android.gms.permission.AD_ID`
18. `android.permission.BIND_VPN_SERVICE` (Permiso del sistema reservado al VpnService)

---

## ⚡ Formato de Payloads y Variables

El motor de inyección de Jump soporta los siguientes comodines:
- `[host_port]`: Dirección IP/Host y puerto de destino del servidor SSH (`1.2.3.4:22`).
- `[host]`: Dirección IP o nombre de dominio del servidor SSH.
- `[port]`: Puerto del servidor SSH (`22`, `443`, `80`).
- `[protocol]`: Versión del protocolo HTTP (`HTTP/1.1` o `HTTP/1.0`).
- `[crlf]`: Retorno de carro y salto de línea `\r\n`.
- `[lf]`: Salto de línea `\n`.
- `[cr]`: Retorno de carro `\r`.
- `[ua]`: User-Agent dinámico simulando navegador o dispositivo móvil.
- `[raw]`: Envío directo sin modificaciones.
- `[split]`: División del payload en múltiples paquetes TCP (fragmentación).

### Ejemplo de Payload HTTP Proxy:
```http
CONNECT [host_port] [protocol][crlf]Host: portal.operadora.com[crlf]X-Online-Host: portal.operadora.com[crlf]Connection: Keep-Alive[crlf]User-Agent: [ua][crlf][crlf]
```

### Ejemplo de Payload WebSocket Cloudflare CDN:
```http
GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf]User-Agent: [ua][crlf][crlf]
```
