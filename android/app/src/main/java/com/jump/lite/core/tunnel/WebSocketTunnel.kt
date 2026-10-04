package com.jump.lite.core.tunnel

import com.jump.lite.core.JumpVpnService
import com.jump.lite.core.payload.PayloadEngine
import com.jump.lite.model.VpnProfile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

object WebSocketTunnel {

    /**
     * Establece un socket TCP con handshake HTTP 101 Switching Protocols
     * a través de servidores CDN (Cloudflare) o proxies inversos.
     * Lee cabeceras byte a byte de forma exacta sin buffering destructivo.
     */
    fun openUpgradeSocket(
        profile: VpnProfile,
        onLog: (String) -> Unit
    ): Socket {
        val socket = Socket()
        // Proteger el socket para evitar bucle infinito con la interfaz TUN de Android
        JumpVpnService.protectSocket(socket)

        val targetHost = if (profile.remoteProxyHost.isNotEmpty()) profile.remoteProxyHost else profile.serverHost
        val targetPort = if (profile.remoteProxyPort > 0) profile.remoteProxyPort else profile.serverPort

        onLog("Conectando a CDN/Proxy: $targetHost:$targetPort...")
        socket.connect(InetSocketAddress(targetHost, targetPort), 12000)
        socket.tcpNoDelay = true

        val out: OutputStream = socket.getOutputStream()
        val rawPayload = PayloadEngine.parse(profile.payload, profile)

        onLog("Enviando petición HTTP Upgrade (WebSocket)...")
        out.write(rawPayload.toByteArray(Charsets.UTF_8))
        out.flush()

        // Lectura exacta byte a byte para preservar los bytes del banner SSH
        val responseHeaders = readHeadersExact(socket.getInputStream())
        val firstLine = responseHeaders.lines().firstOrNull() ?: ""
        onLog("Respuesta del servidor: $firstLine")

        if (!responseHeaders.contains("101") && !responseHeaders.contains("200")) {
            socket.close()
            throw IllegalStateException("Fallo en handshake WebSocket/HTTP: $firstLine")
        }

        onLog("✓ Handshake 101 Switching Protocols completado. Banner SSH intacto.")
        return socket
    }

    /**
     * Lee del flujo byte a byte hasta detectar la secuencia de fin de cabeceras HTTP \r\n\r\n.
     * Esto evita que un BufferedReader consuma los primeros bytes del protocolo SSH posterior.
     */
    fun readHeadersExact(inputStream: InputStream): String {
        val buffer = ByteArrayOutputStream()
        val endPattern = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        var matchCount = 0

        while (true) {
            val b = inputStream.read()
            if (b == -1) {
                throw IOException("Conexión cerrada por el servidor antes de completar las cabeceras HTTP.")
            }
            buffer.write(b)
            if (b.toByte() == endPattern[matchCount]) {
                matchCount++
                if (matchCount == endPattern.size) {
                    break
                }
            } else {
                matchCount = if (b.toByte() == endPattern[0]) 1 else 0
            }
        }
        return buffer.toString(Charsets.UTF_8.name())
    }
}
