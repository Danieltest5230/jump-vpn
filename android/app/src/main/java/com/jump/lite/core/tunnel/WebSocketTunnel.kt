package com.jump.lite.core.tunnel

import com.jump.lite.core.payload.PayloadEngine
import com.jump.lite.model.VpnProfile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

object WebSocketTunnel {

    /**
     * Establece un socket TCP con handshake HTTP 101 Switching Protocols
     * a través de servidores CDN (Cloudflare) o proxies inversos.
     */
    fun openUpgradeSocket(
        profile: VpnProfile,
        onLog: (String) -> Unit
    ): Socket {
        val socket = Socket()
        val targetHost = if (profile.remoteProxyHost.isNotEmpty()) profile.remoteProxyHost else profile.serverHost
        val targetPort = if (profile.remoteProxyPort > 0) profile.remoteProxyPort else profile.serverPort

        onLog("Conectando a CDN/Proxy: $targetHost:$targetPort...")
        socket.connect(InetSocketAddress(targetHost, targetPort), 10000)
        socket.tcpNoDelay = true

        val out: OutputStream = socket.getOutputStream()
        val rawPayload = PayloadEngine.parse(profile.payload, profile)

        onLog("Enviando petición HTTP Upgrade (WebSocket)...")
        out.write(rawPayload.toByteArray(Charsets.UTF_8))
        out.flush()

        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val statusLine = reader.readLine()
        onLog("Respuesta del servidor: $statusLine")

        if (statusLine == null || (!statusLine.contains("101") && !statusLine.contains("200"))) {
            socket.close()
            throw IllegalStateException("Fallo en handshake WebSocket/HTTP: $statusLine")
        }

        // Leer cabeceras restantes hasta línea vacía
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (line!!.isEmpty()) break
        }

        onLog("Handshake 101 Switching Protocols completado con éxito.")
        return socket
    }
}
