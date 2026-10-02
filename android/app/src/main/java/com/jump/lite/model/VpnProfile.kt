package com.jump.lite.model

import com.google.gson.Gson
import java.io.Serializable

data class VpnProfile(
    var id: String = "default",
    var name: String = "Servidor LATAM #1",
    var serverHost: String = "192.168.1.100",
    var serverPort: Int = 22,
    var sshUser: String = "jump",
    var sshPass: String = "jump123",
    var tunnelMode: TunnelMode = TunnelMode.SSH_PROXY_PAYLOAD,
    var remoteProxyHost: String = "104.16.1.1",
    var remoteProxyPort: Int = 80,
    var payload: String = "CONNECT [host_port] [protocol][crlf]Host: portal.operadora.com[crlf]X-Online-Host: portal.operadora.com[crlf]Connection: Keep-Alive[crlf]User-Agent: [ua][crlf][crlf]",
    var sniHost: String = "portal.operadora.com",
    var dnsPrimary: String = "1.1.1.1",
    var dnsSecondary: String = "1.0.0.1",
    var udpGatewayPort: Int = 7300,
    var enableUdpForwarding: Boolean = true,
    var autoReconnect: Boolean = true,
    var isLocked: Boolean = false,
    var expiryDate: Long = 0L,
    var note: String = "Configuración optimizada para datos móviles Jump"
) : Serializable {

    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String): VpnProfile {
            return try {
                Gson().fromJson(json, VpnProfile::class.java)
            } catch (e: Exception) {
                VpnProfile()
            }
        }

        fun createDefault(): VpnProfile {
            return VpnProfile()
        }
    }
}
