package com.jump.lite.core.payload

object PayloadGenerator {

    enum class RequestMethod {
        CONNECT, GET, POST, HEAD, PUT, OPTIONS, TRACE
    }

    enum class InjectionType {
        NORMAL, FRONT_INJECT, BACK_INJECT
    }

    data class GeneratorOptions(
        val bugHost: String,
        val method: RequestMethod = RequestMethod.CONNECT,
        val injectionType: InjectionType = InjectionType.NORMAL,
        val keepAlive: Boolean = true,
        val onlineHost: Boolean = true,
        val forwardHost: Boolean = false,
        val reverseProxy: Boolean = false,
        val userAgent: Boolean = true,
        val dualConnect: Boolean = false
    )

    fun generate(options: GeneratorOptions): String {
        val sb = StringBuilder()
        val bug = options.bugHost.trim()

        when (options.injectionType) {
            InjectionType.NORMAL -> {
                sb.append("${options.method.name} [host_port] [protocol][crlf]")
                if (bug.isNotEmpty()) {
                    sb.append("Host: $bug[crlf]")
                    if (options.onlineHost) sb.append("X-Online-Host: $bug[crlf]")
                    if (options.forwardHost) sb.append("X-Forward-Host: $bug[crlf]")
                    if (options.reverseProxy) sb.append("X-Forwarded-For: $bug[crlf]")
                } else {
                    sb.append("Host: [host][crlf]")
                }
            }
            InjectionType.FRONT_INJECT -> {
                if (bug.isNotEmpty()) {
                    sb.append("GET http://$bug/ [protocol][crlf]Host: $bug[crlf][crlf]")
                }
                sb.append("${options.method.name} [host_port] [protocol][crlf]")
                sb.append("Host: ${if (bug.isNotEmpty()) bug else "[host]"}[crlf]")
            }
            InjectionType.BACK_INJECT -> {
                sb.append("${options.method.name} [host_port] [protocol][crlf]")
                sb.append("Host: ${if (bug.isNotEmpty()) bug else "[host]"}[crlf][crlf]")
                if (bug.isNotEmpty()) {
                    sb.append("GET http://$bug/ [protocol][crlf]Host: $bug[crlf]")
                }
            }
        }

        if (options.keepAlive) {
            sb.append("Connection: Keep-Alive[crlf]")
            sb.append("Proxy-Connection: Keep-Alive[crlf]")
        }
        if (options.userAgent) {
            sb.append("User-Agent: [ua][crlf]")
        }

        sb.append("[crlf]")
        return sb.toString()
    }

    /**
     * Genera payload para tunelización WebSocket con Cloudflare CDN (HTTP 101 Switching Protocols)
     */
    fun generateWebSocket(bugHost: String, customPath: String = "/"): String {
        val host = if (bugHost.isNotEmpty()) bugHost.trim() else "[host]"
        val path = if (customPath.startsWith("/")) customPath else "/$customPath"
        return "GET $path HTTP/1.1[crlf]" +
                "Host: $host[crlf]" +
                "Upgrade: websocket[crlf]" +
                "Connection: Upgrade[crlf]" +
                "Sec-WebSocket-Key: [ua][crlf]" +
                "Sec-WebSocket-Version: 13[crlf][crlf]"
    }
}
