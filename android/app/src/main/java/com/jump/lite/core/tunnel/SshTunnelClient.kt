package com.jump.lite.core.tunnel

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jump.lite.core.JumpVpnService
import com.jump.lite.core.payload.PayloadEngine
import com.jump.lite.model.TunnelMode
import com.jump.lite.model.VpnProfile
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Properties

class SshTunnelClient(
    private val profile: VpnProfile,
    private val localSocksPort: Int = 1080,
    private val onLog: (String) -> Unit
) {
    private var session: Session? = null
    private var isRunning = false

    fun start() {
        try {
            onLog("Iniciando cliente SSH Jump...")
            val jsch = JSch()

            session = jsch.getSession(profile.sshUser, profile.serverHost, profile.serverPort)
            session?.setPassword(profile.sshPass)

            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            config["PreferredAuthentications"] = "password,keyboard-interactive"
            session?.setConfig(config)

            // Configurar SocketFactory según el modo de túnel, protegiendo todos los sockets salientes
            when (profile.tunnelMode) {
                TunnelMode.SSH_DIRECT -> {
                    onLog("Modo: Conexión directa TCP...")
                    session?.setSocketFactory(object : SocketFactory {
                        private var sock: Socket? = null
                        override fun createSocket(host: String?, port: Int): Socket {
                            val s = Socket()
                            JumpVpnService.protectSocket(s)
                            s.connect(InetSocketAddress(host, port), 12000)
                            s.tcpNoDelay = true
                            sock = s
                            return s
                        }
                        override fun getInputStream(socket: Socket?): InputStream = socket!!.getInputStream()
                        override fun getOutputStream(socket: Socket?): OutputStream = socket!!.getOutputStream()
                    })
                }
                TunnelMode.SSH_SSL_SNI -> {
                    onLog("Modo: SSL/TLS con SNI Bug: ${profile.sniHost}")
                    session?.setSocketFactory(object : SocketFactory {
                        private var sock: Socket? = null
                        override fun createSocket(host: String?, port: Int): Socket {
                            val s = SslSniSocket.createSocket(
                                profile.serverHost,
                                profile.serverPort,
                                profile.sniHost
                            )
                            sock = s
                            return s
                        }
                        override fun getInputStream(socket: Socket?): InputStream = socket!!.getInputStream()
                        override fun getOutputStream(socket: Socket?): OutputStream = socket!!.getOutputStream()
                    })
                }
                TunnelMode.SSH_WEBSOCKET_CDN -> {
                    onLog("Modo: WebSocket Cloudflare CDN...")
                    session?.setSocketFactory(object : SocketFactory {
                        private var sock: Socket? = null
                        override fun createSocket(host: String?, port: Int): Socket {
                            val s = WebSocketTunnel.openUpgradeSocket(profile, onLog)
                            sock = s
                            return s
                        }
                        override fun getInputStream(socket: Socket?): InputStream = socket!!.getInputStream()
                        override fun getOutputStream(socket: Socket?): OutputStream = socket!!.getOutputStream()
                    })
                }
                TunnelMode.SSH_PROXY_PAYLOAD -> {
                    onLog("Modo: HTTP Proxy con inyección de Payload...")
                    session?.setSocketFactory(object : SocketFactory {
                        private var sock: Socket? = null
                        override fun createSocket(host: String?, port: Int): Socket {
                            val s = Socket()
                            JumpVpnService.protectSocket(s)
                            val proxyHost = if (profile.remoteProxyHost.isNotEmpty()) profile.remoteProxyHost else profile.serverHost
                            val proxyPort = if (profile.remoteProxyPort > 0) profile.remoteProxyPort else 8080
                            onLog("Conectando a Proxy: $proxyHost:$proxyPort...")
                            s.connect(InetSocketAddress(proxyHost, proxyPort), 12000)
                            s.tcpNoDelay = true

                            val out = s.getOutputStream()
                            val payload = PayloadEngine.parse(profile.payload, profile)
                            onLog("Inyectando Payload HTTP...")
                            out.write(payload.toByteArray(Charsets.UTF_8))
                            out.flush()

                            // Consumir la respuesta HTTP 200 Connection established del proxy
                            // para no romper la identificación del banner SSH posterior
                            val responseHeaders = WebSocketTunnel.readHeadersExact(s.getInputStream())
                            val statusLine = responseHeaders.lines().firstOrNull() ?: ""
                            onLog("Proxy respuesta: $statusLine")

                            sock = s
                            return s
                        }
                        override fun getInputStream(socket: Socket?): InputStream = socket!!.getInputStream()
                        override fun getOutputStream(socket: Socket?): OutputStream = socket!!.getOutputStream()
                    })
                }
                TunnelMode.V2RAY_VMESS -> {
                    onLog("Modo: V2Ray Core...")
                }
            }

            onLog("Autenticando usuario '${profile.sshUser}'...")
            session?.connect(30000)

            if (session?.isConnected == true) {
                onLog("✓ SSH Conectado exitosamente!")
                val boundPort = session?.setPortForwardingL("127.0.0.1", localSocksPort, "127.0.0.1", profile.serverPort)
                onLog("✓ Enrutador de puerto local activo en 127.0.0.1:$boundPort")
                isRunning = true
            } else {
                throw IllegalStateException("No se pudo establecer la sesión SSH")
            }

        } catch (e: Exception) {
            onLog("Error en túnel SSH: ${e.message}")
            stop()
            throw e
        }
    }

    fun stop() {
        isRunning = false
        try {
            session?.delPortForwardingL("127.0.0.1", localSocksPort)
        } catch (e: Exception) {
            // Ignorar
        }
        try {
            session?.disconnect()
            session = null
            onLog("Túnel SSH cerrado.")
        } catch (e: Exception) {
            // Ignorar errores al cerrar
        }
    }

    fun isConnected(): Boolean = session?.isConnected == true && isRunning
}
