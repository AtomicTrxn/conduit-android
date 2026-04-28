package com.atomictrxn.conduit.domain.auth

import java.util.Base64

object JwtRefreshPolicy {
    private const val JWT_PART_COUNT = 3
    private const val REFRESH_THRESHOLD_SECONDS = 24 * 60 * 60

    fun needsRefresh(
        storedKey: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    ): Boolean {
        if (storedKey.isBlank()) return true
        val parts = storedKey.split(".")
        if (parts.size != JWT_PART_COUNT) return false

        return try {
            val payload =
                Base64.getUrlDecoder()
                    .decode(parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='))
            val exp = EXP_REGEX.find(String(payload))?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            exp < nowEpochSeconds + REFRESH_THRESHOLD_SECONDS
        } catch (e: Exception) {
            true
        }
    }

    private val EXP_REGEX = """"exp"\s*:\s*(\d+)""".toRegex()
}
