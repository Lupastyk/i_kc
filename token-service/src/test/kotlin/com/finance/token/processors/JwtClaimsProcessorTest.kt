package com.finance.token.processors

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.finance.token.model.UserClaims
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.*

class JwtClaimsProcessorTest {

    private val ctx = DefaultCamelContext()
    private val p = JwtClaimsProcessor()
    private val mapper = jacksonObjectMapper()

    @Test
    fun `parses payload and stores claims json (preferredUsername may be blank)`() {
        //given
        val headerJson = """{"alg":"none","typ":"JWT"}"""
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(headerJson.toByteArray(StandardCharsets.UTF_8))

        val payloadJson = """{
          "subject":"u1",
          "preferred_username":"", 
          "email":"user@test.dev",
          "iss":"kc",
          "exp": 9999999999
        }""".trimIndent()
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toByteArray(StandardCharsets.UTF_8))

        val token = "$header.$payload"
        val ex = DefaultExchange(ctx)
        ex.setProperty("accessToken", token)
        //when
        p.process(ex)
        //then
        val claims = ex.getProperty("userClaims", UserClaims::class.java)
        assertNotNull(claims)
        assertEquals("u1", claims!!.subject)
        assertEquals("", claims.preferredUsername)
        assertEquals("user@test.dev", claims.email)
        assertEquals("kc", claims.issuer)

        val json = ex.getProperty("userClaimsJson", String::class.java)
        assertNotNull(json)

        val back = mapper.readValue(json, UserClaims::class.java)
        assertEquals(claims, back)

        if (!claims.preferredUsername.isNullOrBlank()) {
            assertTrue(json!!.contains("preferredUsername") || json.contains("preferred_username"))
        }
    }

    @Test
    fun `pads base64 payload without '='`() {
        //given
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"none"}""".toByteArray())
        val payloadRaw = """{"subject":"pad"}"""
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadRaw.toByteArray())

        assertNotEquals(0, payload.length % 4)

        val token = "$header.$payload"
        val ex = DefaultExchange(ctx)
        ex.setProperty("accessToken", token)
        //when
        p.process(ex)
        //then
        val claims = ex.getProperty("userClaims", UserClaims::class.java)
        assertNotNull(claims)
        assertEquals("pad", claims!!.subject)
    }
}
