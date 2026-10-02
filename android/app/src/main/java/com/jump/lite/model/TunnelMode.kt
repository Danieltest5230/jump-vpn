package com.jump.lite.model

enum class TunnelMode(val displayName: String, val defaultPort: Int) {
    SSH_DIRECT("SSH Directo", 22),
    SSH_PROXY_PAYLOAD("SSH + HTTP Proxy (Payload)", 8080),
    SSH_SSL_SNI("SSH + SSL/TLS (SNI Bug)", 443),
    SSH_WEBSOCKET_CDN("SSH + WebSocket (Cloudflare CDN)", 80),
    V2RAY_VMESS("V2Ray / VMess Core", 443);

    companion object {
        fun fromOrdinal(ordinal: Int): TunnelMode {
            return values().getOrElse(ordinal) { SSH_PROXY_PAYLOAD }
        }
    }
}
