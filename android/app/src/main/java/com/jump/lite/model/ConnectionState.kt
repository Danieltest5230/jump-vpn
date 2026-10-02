package com.jump.lite.model

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    INJECTING_PAYLOAD,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    FAILED
}
