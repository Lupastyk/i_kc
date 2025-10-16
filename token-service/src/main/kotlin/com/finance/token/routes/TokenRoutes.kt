package com.finance.token.routes

import com.finance.token.config.PemLoader
import com.finance.token.model.*
import com.finance.token.processors.JwtClaimsProcessor
import io.smallrye.jwt.build.Jwt
import io.smallrye.jwt.util.KeyUtils
import jakarta.enterprise.context.ApplicationScoped
import org.apache.camel.Exchange
import org.apache.camel.builder.Builder
import org.apache.camel.builder.PredicateBuilder
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.dataformat.JsonLibrary
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@ApplicationScoped
open class TokenRoutes(
    @ConfigProperty(name = "keycloak.tokenUrl") private val tokenUrl: String,
    @ConfigProperty(name = "transactions.baseUrl") private val txBase: String,

    //client secret
    @ConfigProperty(name = "oauth.clientId") private val clientId: String,
    @ConfigProperty(name = "oauth.clientSecret") private val clientSecret: Optional<String>,
    //private key
    @ConfigProperty(name = "oauth.privateKeyPem") private val privateKeyPem: String,
    @ConfigProperty(name = "oauth.redirectUri") private val redirectUri: String,
    @ConfigProperty(name = "oauth.keyId") private val keyId: Optional<String>,

    @ConfigProperty(name = "kafka.topic.user-transactions") private val topic: String,
    @ConfigProperty(name = "kafka.enabled", defaultValue = "true") private val kafkaEnabled: Boolean,

    private val jwtClaimsProcessor: JwtClaimsProcessor,
    private val pemLoader: PemLoader,
) : RouteBuilder() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun configure() {
        onException(Exception::class.java)
            .handled(true)
            .setHeader(Exchange.CONTENT_TYPE).constant("application/json")
            .process { ex ->
                val msg = ex.exception?.message ?: "unexpected error"
                ex.message.body = mapOf("error" to msg)
            }
            .marshal().json(JsonLibrary.Jackson)

        rest("/")
            .get("token")
            .description("Exchange auth code -> token, call transactions, publish to Kafka")
            .produces("application/json")
            .to("direct:tokenFlow")

        from("direct:tokenFlow")
            .process { ex ->
                val codeFromHeader = ex.message.getHeader("code", String::class.java)
                val rawQuery = ex.message.getHeader(Exchange.HTTP_QUERY, String::class.java)
                val fromQuery = rawQuery
                    ?.split('&')
                    ?.mapNotNull {
                        val p = it.split('=', limit = 2)
                        if (p.size == 2) p[0] to p[1] else null
                    }
                    ?.firstOrNull { it.first == "code" }
                    ?.second
                    ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }

                val code = (codeFromHeader ?: fromQuery)?.trim().orEmpty()
                ex.setProperty("authCode", code)
                log.info("Received /token with code={}", if (code.isBlank()) "<empty>" else "***")
            }
            .choice()
            .`when`(
                PredicateBuilder.or(
                    headerOrPropertyIsNull("authCode"),
                    headerOrPropertyEquals("authCode", "")
                )
            )
            .setHeader(Exchange.CONTENT_TYPE).constant("application/json")
            .setBody().constant(mapOf("error" to "missing 'code' query param"))
            .marshal().json(JsonLibrary.Jackson)
            .stop()
            .end()

            .removeHeaders("*")
            .setHeader(Exchange.CONTENT_TYPE).constant("application/x-www-form-urlencoded")

            .process { ex ->
                val code = ex.getProperty("authCode", String::class.java) ?: ""

                val form = StringBuilder()
                    .append("grant_type=authorization_code")
                    .append("&code=").append(url(code))
                    .append("&client_id=").append(url(clientId))
                    .append("&redirect_uri=").append(url(redirectUri))

//                fun hasReadable(path: String?) =
//                    !path.isNullOrBlank() && try {
//                        java.nio.file.Files.isReadable(java.nio.file.Path.of(path))
//                    } catch (_: Exception) {
//                        false
//                    }


                val canUsePkJwt = hasReadable(privateKeyPem)
                val hasSecret = clientSecret.orElse("").isNotBlank()

                if (!canUsePkJwt && !hasSecret) {
                    ex.message.headers["CamelHttpResponseCode"] = 400

                    val err = "Missing credentials: no private key (oauth.privateKeyPem/keyId) and no client_secret"
                    ex.setProperty("kc_error", err)
                    ex.message.body = mapOf("error" to err)
                    return@process
                }

                when {
                    canUsePkJwt -> {
                        val assertion = buildClientAssertion(
                            clientId = clientId,
                            tokenUrl = tokenUrl,
                            keyId = keyId.orElse(null),
                            privatePem = privateKeyPem
                        )
                        form.append("&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                            .append("&client_assertion=").append(url(assertion))
                        log.info("KC form ready (pk_jwt). client_assertion=***")
                    }

                    hasSecret -> {
                        form.append("&client_secret=").append(url(clientSecret.get()))
                        log.info("KC form ready (client_secret)")
                    }

                    else -> {
                        val err = "Missing credentials: no private key (oauth.privateKeyPem/keyId) and no client_secret"
                        ex.setProperty("kc_error", err)
                        ex.message.body = mapOf("error" to err)
                        return@process
                    }
                }

                ex.message.body = form.toString()
            }
            .choice()
            .`when`(Builder.exchangeProperty("kc_error").isEqualTo(true))
            .marshal().json(JsonLibrary.Jackson)
            .stop()
            .end()
            .toD("$tokenUrl?httpMethod=POST&bridgeEndpoint=true&throwExceptionOnFailure=false")
            .unmarshal().json(JsonLibrary.Jackson, TokenResponse::class.java)
            .process { ex ->
                val tokenResponse = ex.message.getBody(TokenResponse::class.java)
                if (tokenResponse.error != null) {
                    ex.message.setHeader(Exchange.CONTENT_TYPE, "application/json")
                    ex.message.body = mapOf("error" to (tokenResponse.errorDescription ?: "keycloak error"))
                    ex.setProperty("kc_error", true)
                } else {
                    ex.setProperty("accessToken", tokenResponse.accessToken ?: "")
                }
            }
            .choice()
            .`when`(Builder.exchangeProperty("kc_error").isEqualTo(true))
            .marshal().json(JsonLibrary.Jackson)
            .stop()
            .end()

            .process(jwtClaimsProcessor)

            .removeHeaders("*")
            .setHeader("Authorization").simple("Bearer \${exchangeProperty.accessToken}")
            .setBody().constant(null)
            .toD("$txBase/api/transactions?httpMethod=GET&bridgeEndpoint=true&throwExceptionOnFailure=false")
            .unmarshal().json(JsonLibrary.Jackson, TransactionsResponse::class.java)
            .setProperty("transactions").body()

            .process { ex ->
                val userClaims = ex.getProperty("userClaims", UserClaims::class.java)
                val txResp = ex.getProperty("transactions", TransactionsResponse::class.java)

                val userId =
                    txResp.userId
                        ?: userClaims.subject
                        ?: userClaims.preferredUsername
                        ?: throw IllegalStateException("Cannot find userId")

                ex.setProperty("kafkaPayload", UserAndTransactions(userId, userClaims, txResp.transactions))
            }
            .choice()
            .`when`().constant(kafkaEnabled)
            .setBody().exchangeProperty("kafkaPayload")
            .marshal().json(JsonLibrary.Jackson)
            .toD("kafka:$topic?brokers={{kafka.bootstrap.servers}}")
            .log("Published user + transactions to Kafka topic '$topic'")
            .end()

            .setBody().exchangeProperty("transactions")
            .marshal().json(JsonLibrary.Jackson)

        rest("/api")
            .get("health")
            .produces("application/json")
            .to("direct:health")

        from("direct:health")
            .setHeader(Exchange.CONTENT_TYPE).constant("application/json")
            .setBody().constant(HealthResponse(HealthStatus.UP))
            .marshal().json(JsonLibrary.Jackson)
    }

    private fun headerOrPropertyIsNull(name: String) =
        PredicateBuilder.or(Builder.header(name).isNull, Builder.exchangeProperty(name).isNull)

    private fun headerOrPropertyEquals(name: String, value: String) =
        PredicateBuilder.or(Builder.header(name).isEqualTo(value), Builder.exchangeProperty(name).isEqualTo(value))

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
            .claim("jti", java.util.UUID.randomUUID().toString())
            .jws()
            .algorithm(io.smallrye.jwt.algorithm.SignatureAlgorithm.RS256)

        if (!keyId.isNullOrBlank()) jws.keyId(keyId)

        return jws.sign(pk)
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

    private fun url(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)
}
