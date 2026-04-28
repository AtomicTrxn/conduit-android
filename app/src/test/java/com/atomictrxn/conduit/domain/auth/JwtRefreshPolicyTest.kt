package com.atomictrxn.conduit.domain.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtRefreshPolicyTest {
    @Test
    fun blankKeyRefreshes() {
        assertTrue(JwtRefreshPolicy.needsRefresh(""))
    }

    @Test
    fun permanentApiKeyDoesNotRefresh() {
        assertFalse(JwtRefreshPolicy.needsRefresh("sk-permanent-key", nowEpochSeconds = NOW))
    }

    @Test
    fun jwtExpiringWithinThresholdRefreshes() {
        assertTrue(JwtRefreshPolicy.needsRefresh(jwt(exp = NOW + 60), nowEpochSeconds = NOW))
    }

    @Test
    fun malformedJwtRefreshes() {
        assertTrue(JwtRefreshPolicy.needsRefresh("a.b.c", nowEpochSeconds = NOW))
    }

    @Test
    fun validJwtOutsideThresholdDoesNotRefresh() {
        assertFalse(JwtRefreshPolicy.needsRefresh(jwt(exp = NOW + 48 * 60 * 60), nowEpochSeconds = NOW))
    }

    private fun jwt(exp: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = encoder.encodeToString("""{"exp":$exp}""".toByteArray())
        return "$header.$payload.signature"
    }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}
