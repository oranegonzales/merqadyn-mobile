package dev.merqadyn.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun `production accepts https and normalizes the origin`() {
        assertEquals("https://api.example.com:8443/", EndpointPolicy.normalize("HTTPS://API.EXAMPLE.COM:8443/", false))
    }

    @Test
    fun `production rejects cleartext`() {
        assertThrows(IllegalArgumentException::class.java) {
            EndpointPolicy.normalize("http://192.168.1.25:8080/", false)
        }
    }

    @Test
    fun `debug accepts only private cleartext origins`() {
        assertEquals("http://10.0.2.2:8080/", EndpointPolicy.normalize("http://10.0.2.2:8080", true))
        assertThrows(IllegalArgumentException::class.java) {
            EndpointPolicy.normalize("http://example.com/", true)
        }
    }

    @Test
    fun `addresses cannot contain credentials paths or query parameters`() {
        listOf(
            "https://user:password@example.com/",
            "https://example.com/api",
            "https://example.com/?token=secret",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { EndpointPolicy.normalize(value, false) }
        }
    }
}
