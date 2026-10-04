package com.jump.lite.core.tunnel

import com.jump.lite.core.JumpVpnService
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SslSniSocket {

    private val permissiveTrustManager = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    private val permissiveSslSocketFactory: SSLSocketFactory by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, permissiveTrustManager, SecureRandom())
        sslContext.socketFactory
    }

    /**
     * Establece una conexión de socket SSL/TLS forzando una cabecera SNI específica (Bug Host)
     * para bypass de inspección profunda de paquetes (DPI) en redes móviles.
     * Protege el socket contra bucle de enrutamiento TUN.
     */
    fun createSocket(
        targetHost: String,
        targetPort: Int,
        sniHost: String,
        timeoutMs: Int = 12000
    ): Socket {
        val plainSocket = Socket()
        // Proteger socket para que no sea interceptado por la interfaz virtual TUN
        JumpVpnService.protectSocket(plainSocket)

        plainSocket.connect(InetSocketAddress(targetHost, targetPort), timeoutMs)
        plainSocket.tcpNoDelay = true

        val sslSocket = permissiveSslSocketFactory.createSocket(
            plainSocket,
            targetHost,
            targetPort,
            true
        ) as SSLSocket

        val sslParameters = sslSocket.sslParameters ?: SSLParameters()
        val sniHostClean = sniHost.trim()
        if (sniHostClean.isNotEmpty()) {
            sslParameters.serverNames = listOf(SNIHostName(sniHostClean))
        }

        sslSocket.sslParameters = sslParameters
        sslSocket.startHandshake()

        return sslSocket
    }
}
