package com.atomictrxn.conduit.domain.validation

import java.net.URI
import java.util.Locale

object ServerUrlValidator {
    fun validate(url: String): ServerUrlValidationResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ServerUrlValidationResult.Required

        val uri =
            runCatching { URI(trimmed) }
                .getOrNull()
                ?: return ServerUrlValidationResult.InvalidScheme

        val scheme = uri.scheme?.lowercase(Locale.US)
        val host = uri.host?.trim('[', ']')?.lowercase(Locale.US)

        if (scheme != "http" && scheme != "https") {
            return ServerUrlValidationResult.InvalidScheme
        }
        if (host.isNullOrBlank()) {
            return ServerUrlValidationResult.InvalidScheme
        }
        if (scheme == "https") {
            return ServerUrlValidationResult.Valid
        }

        return if (isAllowedHttpHost(host)) {
            ServerUrlValidationResult.Valid
        } else {
            ServerUrlValidationResult.PublicHttp
        }
    }

    private fun isAllowedHttpHost(host: String): Boolean =
        host == "localhost" ||
            host == "::1" ||
            isSingleLabelHost(host) ||
            host.endsWith(".ts.net") ||
            isAllowedIpv4Host(host)

    private fun isSingleLabelHost(host: String): Boolean = "." !in host && ":" !in host && host.any { it.isLetter() }

    private fun isAllowedIpv4Host(host: String): Boolean {
        val octets = host.split(".").map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false

        val first = octets[0]
        val second = octets[1]
        return first == 10 ||
            first == 127 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254) ||
            (first == 100 && second in 64..127)
    }
}

sealed interface ServerUrlValidationResult {
    data object Valid : ServerUrlValidationResult

    data object Required : ServerUrlValidationResult

    data object InvalidScheme : ServerUrlValidationResult

    data object PublicHttp : ServerUrlValidationResult
}
