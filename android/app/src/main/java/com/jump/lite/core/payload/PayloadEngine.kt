package com.jump.lite.core.payload

import com.jump.lite.model.VpnProfile

object PayloadEngine {

    private const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Parsea e inyecta las variables dinámicas en el payload para enviarlo al proxy o socket
     */
    fun parse(template: String, profile: VpnProfile): String {
        var result = template

        val host = profile.serverHost.trim()
        val port = profile.serverPort.toString()
        val hostPort = "$host:$port"

        // Sustitución de comodines estándar
        result = result.replace("[host_port]", hostPort, ignoreCase = true)
        result = result.replace("[host]", host, ignoreCase = true)
        result = result.replace("[port]", port, ignoreCase = true)
        result = result.replace("[protocol]", "HTTP/1.1", ignoreCase = true)
        result = result.replace("[ua]", DEFAULT_UA, ignoreCase = true)

        // Sustitución de caracteres de escape de salto de línea
        result = result.replace("[crlf]", "\r\n", ignoreCase = true)
        result = result.replace("[lf]", "\n", ignoreCase = true)
        result = result.replace("[cr]", "\r", ignoreCase = true)

        // Limpieza de marcadores especiales
        result = result.replace("[raw]", "", ignoreCase = true)

        return result
    }

    /**
     * Comprueba si el payload requiere división de paquetes (TCP packet fragmentation)
     */
    fun hasSplit(template: String): Boolean {
        return template.contains("[split]", ignoreCase = true)
    }

    /**
     * Divide el payload en partes si contiene la etiqueta [split]
     */
    fun splitPayload(parsedPayload: String): List<String> {
        return parsedPayload.split(Regex("\\[split\\]", RegexOption.IGNORE_CASE))
    }
}
