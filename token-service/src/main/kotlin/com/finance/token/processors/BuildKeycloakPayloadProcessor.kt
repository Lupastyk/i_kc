package com.finance.token.processors

import io.smallrye.jwt.build.Jwt
import io.smallrye.jwt.util.KeyUtils
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.net.URLEncoder.encode
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@ApplicationScoped
@Named("buildKeycloakPayloadProcessor")
class BuildKeycloakPayloadProcessor @Inject constructor(

    @ConfigProperty(name = "oauth.clientId")
    private val clientId: String,

    @ConfigProperty(name = "oauth.clientSecret")
    private val clientSecret: Optional<String>,

    @ConfigProperty(name = "oauth.privateKeyPem")
    private val privateKeyPem: String,

    @ConfigProperty(name = "oauth.redirectUri")
    private val redirectUri: String,

    @ConfigProperty(name = "oauth.keyId")
    private val keyId: Optional<String>,

    @ConfigProperty(name = "keycloak.tokenUrl")
    private val tokenUrl: String,

    ) : Processor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun process(exchange: Exchange) {

        val canUsePkJwt = hasReadable(privateKeyPem)
        val hasSecret = clientSecret.orElse("").isNotBlank()
        if (!canUsePkJwt && !hasSecret) {
            exchange.message.headers["CamelHttpResponseCode"] = 400

            val err = "Missing credentials: no private key (oauth.privateKeyPem/keyId) and no client_secret"
            exchange.setProperty("kc_error", err)
            exchange.message.body = mapOf("error" to err)
            return@process
        }

        val code = exchange.getProperty("authCode", String::class.java) ?: ""
        val form = StringBuilder()
            .append("grant_type=authorization_code")
            .append("&code=").append(encode(code, UTF_8))
            .append("&client_id=").append(encode(clientId, UTF_8))
            .append("&redirect_uri=").append(encode(redirectUri, UTF_8))

        when {
            canUsePkJwt -> {
                val assertion = buildClientAssertion(
                    clientId = clientId,
                    tokenUrl = tokenUrl,
                    keyId = keyId.orElse(null),
                    privatePem = privateKeyPem
                )
                form.append("&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                    .append("&client_assertion=").append(encode(assertion, UTF_8))
                log.info("KC form ready (pk_jwt). client_assertion=***")
            }

            hasSecret -> {
                form.append("&client_secret=").append(URLEncoder.encode(clientSecret.get(), UTF_8))
                log.info("KC form ready (client_secret)")
            }

            else -> {
                val err = "Missing credentials: no private key (oauth.privateKeyPem/keyId) and no client_secret"
                exchange.setProperty("kc_error", err)
                exchange.message.body = mapOf("error" to err)
                return@process
            }
        }

        exchange.message.body = form.toString()
    }

    fun hasReadable(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            val p = Paths.get(path)
            val candidates = listOf(
                p,
                p.toAbsolutePath(),
                Paths.get(System.getProperty("user.dir")).resolve(path)
            )
            candidates.any { Files.isReadable(it) }.also {
                val abs = p.toAbsolutePath()
                log.info(
                    "PK path check: raw='{}', abs='{}', exists={}, readable={}, user.dir='{}'",
                    path, abs, Files.exists(abs), Files.isReadable(abs), System.getProperty("user.dir")
                )
            }
        } catch (e: Exception) {
            log.warn("PK path check failed for '{}': {}", path, e.toString())
            false
        }
    }

    private fun buildClientAssertion(
        clientId: String,
        tokenUrl: String,
        keyId: String?,
        privatePem: String
    ): String {
        val pk = KeyUtils.decodePrivateKey(Files.readString(Paths.get(privatePem)))
        val now = java.time.Instant.now().epochSecond
        val jws = Jwt.claims()
            .issuer(clientId)
            .subject(clientId)
            .audience(tokenUrl)
            .issuedAt(now)
            .expiresAt(now + 300)
            .claim("jti", UUID.randomUUID().toString())
            .jws()
            .algorithm(io.smallrye.jwt.algorithm.SignatureAlgorithm.RS256)

        if (!keyId.isNullOrBlank()) jws.keyId(keyId)

        return jws.sign(pk)
    }
}
