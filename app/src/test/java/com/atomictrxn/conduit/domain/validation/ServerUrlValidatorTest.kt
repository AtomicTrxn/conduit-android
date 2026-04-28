package com.atomictrxn.conduit.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerUrlValidatorTest {
    @Test
    fun acceptsHttpsPublicUrls() {
        assertValid("https://example.com")
        assertValid(" HTTPS://EXAMPLE.COM/path ")
    }

    @Test
    fun acceptsLocalhostHttpUrls() {
        assertValid("http://localhost:3000")
        assertValid("http://127.0.0.1:3000")
        assertValid("http://[::1]:3000")
    }

    @Test
    fun acceptsPrivateIpv4HttpUrls() {
        assertValid("http://192.168.1.50:3000")
        assertValid("http://192.168.50.122:9000/response.txt")
        assertValid("http://10.0.0.5")
        assertValid("http://172.16.0.5")
    }

    @Test
    fun rejectsPublicRangeNearPrivateIpv4Urls() {
        assertEquals(ServerUrlValidationResult.PublicHttp, ServerUrlValidator.validate("http://172.32.0.1"))
    }

    @Test
    fun acceptsTailscaleIpv4HttpUrls() {
        assertValid("http://100.64.0.1")
        assertValid("http://100.127.255.254")
    }

    @Test
    fun acceptsMagicDnsAndSingleLabelHttpUrls() {
        assertValid("http://openwebui:8080")
        assertValid("http://openwebui.tailnet-name.ts.net")
        assertValid("HTTP://OPENWEBUI.TAILNET-NAME.TS.NET")
    }

    @Test
    fun rejectsPublicHttpUrls() {
        assertEquals(ServerUrlValidationResult.PublicHttp, ServerUrlValidator.validate("http://example.com"))
    }

    @Test
    fun rejectsInvalidIpv4LikeUrls() {
        assertNotEquals(ServerUrlValidationResult.Valid, ServerUrlValidator.validate("http://192.168.1.999"))
        assertEquals(ServerUrlValidationResult.PublicHttp, ServerUrlValidator.validate("http://100.128.0.1"))
    }

    @Test
    fun rejectsBlankAndNonHttpUrls() {
        assertEquals(ServerUrlValidationResult.Required, ServerUrlValidator.validate(""))
        assertEquals(ServerUrlValidationResult.Required, ServerUrlValidator.validate("   "))
        assertEquals(ServerUrlValidationResult.InvalidScheme, ServerUrlValidator.validate("ftp://example.com"))
    }

    private fun assertValid(url: String) {
        assertEquals(ServerUrlValidationResult.Valid, ServerUrlValidator.validate(url))
    }
}
