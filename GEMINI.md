# 🧠 MEMORIA PERMANENTE Y REGLAS DEL PROYECTO (JUMP VPN)

Este archivo se carga automáticamente en todas las sesiones para que NUNCA se olvide el contexto ni el trabajo ya realizado.

---

## ⚡ Regla Especial del Usuario
- **Auto-Confirmación:** Cuando se solicite confirmación al usuario para alguna petición o comando, si no responde o pasa poco tiempo, se debe asumir como aceptado/confirmado y proceder automáticamente sin bloquear la ejecución.

---

## 🚀 Estado Actual del Proyecto y Servicios

### 1. Panel Web en Render (ACTIVO Y EN PRODUCCIÓN)
- **Repositorio:** `https://github.com/Danieltest5230/jump-vpn.git` (rama `main`).
- **Estado:** Activo en Render (Runtime Node.js, `npm install` -> `node server.js`).
- **Sistema Anti-Sleep 24/7:**
  - Endpoint de salud: `/health` (Responde 200 OK con timestamp y uptime).
  - Auto-ping interno en `panel/server.js`: Ejecuta peticiones cada 7 minutos hacia `RENDER_EXTERNAL_URL` para evitar la suspensión por inactividad de Render.
  - Archivos de configuración: `render.yaml` y `panel/render.yaml` configurados con `healthCheckPath: /health`.
- **Rutas clave:**
  - Portal de cambio de contraseñas de usuarios: `/cambiar-clave`
  - Panel administrativo: `/admin`
  - Descarga directa de APK: `/descargar` y `/download` (redirige a GitHub Release v4.6.2).
- **Seguridad del Panel:**
  - Rate limiting (5 intentos/minuto) contra ataques de fuerza bruta.
  - Sanitización en cambio de contraseñas mediante `child_process.spawn` hacia `chpasswd` (evitando inyecciones en Bash).
  - Cabeceras de seguridad HTTP (`X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `X-XSS-Protection`).

### 2. App Móvil Android (SIN FUGAS DE DATOS Y FUNCIONAL)
- **Generación:** APK compilada con éxito en GitHub Actions (`Jump-VPN-v4.6.2.apk`).
- **Fugas de Datos (Data Leaks) RESUELTAS:**
  - **Fuga IPv6:** Captura y bloqueo total de tráfico IPv6 celular mediante rutas `fd00::2/120` y `::/0` en `JumpVpnService.kt`.
  - **Fuga DNS:** Implementado proxy DNS Layer 3 en `Tun2Socks.kt` reenviando consultas a `1.1.1.1` mediante socket protegido (`protectSocket`).
  - **Congelamiento / Bucle de Ruteo:** Todos los sockets de transporte (SSH, SSL SNI, WebSocket CDN) son protegidos con `JumpVpnService.protectSocket(...)`.
  - **WebSocket / SSL Payload:** Lectura exacta byte a byte de cabeceras HTTP (`readHeadersExact`) para evitar tragar bytes del banner de bienvenida SSH (`SSH-2.0-...`).
  - **Privacidad:** Eliminados permisos innecesarios de geolocalización y Ad ID en `AndroidManifest.xml`.
  - **Librería SSH:** `com.github.mwiede:jsch` configurada con puerto dinámico compatible `setPortForwardingL`.

### 3. Servidor VPS (Linux)
- **Script:** `server-scripts/setup_vps.sh`
- **Servicios:**
  - OpenSSH (puertos 22 y 443 vía Stunnel)
  - Squid Proxy autenticado (puertos 8080, 3128) con directivas seguras (no open-proxy).
  - BadVPN UDPGW (puerto 7300) para llamadas VoIP y gaming UDP.
  - WebSocket Python CDN Bridge (puerto 80) para saltar firewalls de operadoras.

---

## 📂 Archivos Clave de Contexto
- `GEMINI.md`: Este archivo (reglas y contexto vivo inyectado por Antigravity).
- `.workspace_context.md`: Bitácora técnica y registro de tareas.
- `.agents/skills/github-memory/SKILL.md`: Protocolo de sincronización con GitHub.
