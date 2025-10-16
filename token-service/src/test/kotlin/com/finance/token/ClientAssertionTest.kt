package com.finance.token

import io.smallrye.jwt.util.KeyUtils
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class ClientAssertionTest {

    val privatePem = readResource("/keys/private_key.pem")
    val publicPem = readResource("/keys/public_key.pem")

    private fun buildClientAssertion(
        clientId: String,
        tokenUrl: String,
        privateKeyPem: String,
        kid: String?
    ): String {
        val pk = KeyUtils.decodePrivateKey(privateKeyPem)
        val now = Instant.now().epochSecond
        return io.smallrye.jwt.build.Jwt.claims()
            .issuer(clientId)
            .subject(clientId)
            .audience(tokenUrl)
            .issuedAt(now)
            .expiresAt(now + 300)
            .claim("jti", UUID.randomUUID().toString())
            .jws()
            .keyId(kid)
            .sign(pk)
    }

    @Test
    fun `builds signed client assertion with required claims`() {
        val clientId = "finance-client"
        val tokenUrl = "http://localhost:8080/realms/finance-app/protocol/openid-connect/token"

        val jwt = buildClientAssertion(clientId, tokenUrl, privatePem, "test-kid")

        val jws = JsonWebSignature()
        jws.compactSerialization = jwt
        val pubKey = KeyUtils.decodePublicKey(publicPem)
        jws.key = pubKey

        val consumer = JwtConsumerBuilder()
            .setSkipDefaultAudienceValidation()
            .setVerificationKey(pubKey)
            .setRequireSubject()
            .setRequireExpirationTime()
            .build()

        val claims = consumer.processToClaims(jwt)

        assertEquals(clientId, claims.issuer)
        assertEquals(clientId, claims.subject)
        assertEquals(listOf(tokenUrl), claims.audience)
        assertNotNull(claims.getClaimValue("jti"))
        assertTrue(claims.expirationTime.valueInMillis > System.currentTimeMillis())
    }

    private fun readResource(path: String): String =
        requireNotNull(this::class.java.getResource(path)) { "Resource not found: $path" }
            .readText(Charsets.UTF_8)

}
