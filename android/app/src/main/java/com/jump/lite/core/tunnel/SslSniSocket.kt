package com.jump.lite.core.tunnel

import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object SslSniSocket {

    /**
     * Establece una conexión de socket SSL/TLS forzando una cabecera SNI específica (Bug Host)
     * para bypass de inspección profunda de paquetes (DPI) en redes móviles.
     */
    fun createSocket(
        targetHost: String,
        targetPort: Int,
        sniHost: String,
        timeoutMs: Int = 10000
    ): Socket {
        val plainSocket = Socket()
        plainSocket.connect(InetSocketAddress(targetHost, targetPort), timeoutMs)
        plainSocket.tcpNoDelay = true

        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val sslSocket = sslFactory.createSocket(
            plainSocket,
            targetHost,
            targetPort,
            true
        ) as SSLSocket

        val sslParameters = SSLParameters()
        val sniHostClean = sniHost.trim()
        if (sniHostClean.isNotEmpty()) {
            sslParameters.serverNames = listOf(SNIHostName(sniHostClean))
        }

        sslSocket.sslParameters = sslParameters
        sslSocket.startHandshake()

        return sslSocket
    }
}
