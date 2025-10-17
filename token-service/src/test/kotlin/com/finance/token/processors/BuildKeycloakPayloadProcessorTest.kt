package com.finance.token.processors

import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*

class BuildKeycloakPayloadProcessorTest {

    private val ctx = DefaultCamelContext()
    private val processor = BuildKeycloakPayloadProcessor(
        clientId = "finance-client",
        clientSecret = Optional.of("s3cr3t"),
        privateKeyPem = "",
        redirectUri = "http://localhost:8081/callback",
        keyId = Optional.empty(),
        tokenUrl = "http://keycloak/realms/finance-app/protocol/openid-connect/token"
    )

    @Test
    fun `builds urlencoded form using client_secret`() {
        val ex = DefaultExchange(ctx)
        ex.setProperty("authCode", "abc-123")

        processor.process(ex)

        val body = ex.message.getBody(String::class.java)
        val decoded = URLDecoder.decode(body, StandardCharsets.UTF_8)

        assertTrue(decoded.contains("grant_type=authorization_code"))
        assertTrue(decoded.contains("code=abc-123"))
        assertTrue(decoded.contains("client_id=finance-client"))
        assertTrue(decoded.contains("client_secret=s3cr3t"))
        assertTrue(decoded.contains("redirect_uri=http://localhost:8081/callback"))

        assertFalse(decoded.contains("client_assertion"))
    }

    @Test
    fun `when neither secret nor key 400 + kc_error`() {
        val ex = DefaultExchange(ctx)
        val proc = BuildKeycloakPayloadProcessor(
            clientId = "finance-client",
            clientSecret = Optional.of(""),
            privateKeyPem = "",
            redirectUri = "http://localhost:8081/callback",
            keyId = Optional.empty(),
            tokenUrl = "http://keycloak/token"
        )

        proc.process(ex)

        assertEquals(400, ex.message.getHeader("CamelHttpResponseCode"))
        val err = ex.getProperty("kc_error") as String
        assertTrue(err.contains("Missing credentials", ignoreCase = true))
    }
}
