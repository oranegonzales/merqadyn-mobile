package dev.merqadyn.mobile.data

import java.net.URI

object EndpointPolicy {
    fun normalize(value: String, allowPrivateCleartext: Boolean): String {
        val raw = value.trim()
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            throw IllegalArgumentException("Enter a valid API address.")
        }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "The API address cannot contain credentials, a query, or a fragment." }
        require(uri.path.isNullOrBlank() || uri.path == "/") { "Use only the API origin, without an extra path." }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("The API address needs a host.")
        val scheme = uri.scheme?.lowercase()
        val secure = scheme == "https"
        val privateDebugHost = scheme == "http" && allowPrivateCleartext && isPrivateHost(host)
        require(secure || privateDebugHost) { "Use HTTPS. Debug builds allow HTTP only for loopback or private-network addresses." }
        val displayHost = if (host.contains(':')) "[$host]" else host
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "$scheme://$displayHost$port/"
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host == "::1" || host.startsWith("127.")) return true
        if (host.startsWith("10.") || host.startsWith("192.168.")) return true
        val parts = host.split('.')
        if (parts.size == 4 && parts[0] == "172") {
            val second = parts[1].toIntOrNull() ?: return false
            return second in 16..31
        }
        return false
    }
}
