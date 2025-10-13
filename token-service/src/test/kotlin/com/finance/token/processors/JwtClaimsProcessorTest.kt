package com.finance.token.processors

import org.apache.camel.impl.DefaultCamelContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.apache.camel.support.DefaultExchange
import java.util.Base64

class JwtClaimsProcessorTest {

    private val proc = JwtClaimsProcessor()

    @Test
    fun `parses user claims from jwt payload`() {
        val ctx = DefaultCamelContext()
        val ex  = DefaultExchange(ctx)

        fun encodeB64(s: String) =
            Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

        val header  = encodeB64("""{"alg":"none"}""")
        val payload = encodeB64("""{
          "subject":"u123",
          "preferred_username":"john",
          "email":"john@example.com",
          "iss":"http://issuer",
          "exp":999999
        }""".trimIndent())

        val token = "$header.$payload."

        ex.setProperty("accessToken", token)

        proc.process(ex)

        val json = ex.getProperty("userClaimsJson", String::class.java) ?: ""

        assertTrue(json.contains("\"subject\":\"u123\""))
        assertTrue(json.contains("\"preferred_username\":\"john\""))
        assertTrue(json.contains("\"email\":\"john@example.com\""))
        assertTrue(json.contains("\"iss\":\"http://issuer\""))
        assertTrue(json.contains("\"exp\":999999"))
    }
}
