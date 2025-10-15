package com.finance.token.processors

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.finance.token.model.UserClaims
import jakarta.inject.Singleton
import org.apache.camel.Exchange
import org.apache.camel.Processor
import java.nio.charset.StandardCharsets
import java.util.*

@Singleton
class JwtClaimsProcessor : Processor {

    private val mapper = jacksonObjectMapper()

    override fun process(exchange: Exchange) {
        val token = exchange.getProperty("accessToken", String::class.java)?.trim().orEmpty()
        if (token.isEmpty()) return

        require(token.count { it == '.' } >= 1) { "Invalid JWT format" }

        val parts = token.split('.', limit = 3)
        val payloadB64 = parts[1]

        val pad = (4 - payloadB64.length % 4) % 4
        val fixed = payloadB64 + "=".repeat(pad)
        val payload = String(Base64.getUrlDecoder().decode(fixed), StandardCharsets.UTF_8)

        var claims: UserClaims = mapper.readValue(payload, UserClaims::class.java)
        if (claims.preferredUsername == null) {
            val node = mapper.readTree(payload)
            val alt = node.path("preferredUsername").asText(null)
                ?: node.path("username").asText(null)
            if (alt != null) {
                claims = claims.copy(preferredUsername = alt)
            }
        }

        exchange.setProperty("userClaims", claims)
        exchange.setProperty("userClaimsJson", mapper.writeValueAsString(claims))
    }
}
