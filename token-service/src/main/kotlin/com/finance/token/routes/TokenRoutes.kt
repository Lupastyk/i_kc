package com.finance.token.routes

import com.finance.token.model.*
import com.finance.token.processors.JwtClaimsProcessor
import jakarta.enterprise.context.ApplicationScoped
import org.apache.camel.Exchange
import org.apache.camel.builder.Builder
import org.apache.camel.builder.PredicateBuilder
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.dataformat.JsonLibrary
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*

@ApplicationScoped
open class TokenRoutes(
    @ConfigProperty(name = "keycloak.tokenUrl") private val tokenUrl: String,
    @ConfigProperty(name = "transactions.baseUrl") private val txBase: String,
    @ConfigProperty(name = "oauth.clientId") private val clientId: String,
    @ConfigProperty(name = "oauth.clientSecret") private val clientSecret: Optional<String>,
    @ConfigProperty(name = "oauth.redirectUri") private val redirectUri: String,
    @ConfigProperty(name = "kafka.topic.user-transactions") private val topic: String,
    @ConfigProperty(name = "kafka.enabled", defaultValue = "true") private val kafkaEnabled: Boolean,
    private val jwtClaimsProcessor: JwtClaimsProcessor
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
                val clientSecret = this@TokenRoutes.clientSecret.orElse("").trim()
                val body = buildString {
                    append("grant_type=authorization_code")
                    append("&code=").append(code)
                    append("&client_id=").append(clientId)
                    append("&redirect_uri=").append(redirectUri)
                    if (clientSecret.isNotBlank()) append("&client_secret=").append(clientSecret)
                }
                ex.message.body = body
            }
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
                val txResp   = ex.getProperty("transactions", TransactionsResponse::class.java)

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

        // Health
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
        PredicateBuilder.or(
            Builder.header(name).isNull,
            Builder.exchangeProperty(name).isNull
        )

    private fun headerOrPropertyEquals(name: String, value: String) =
        PredicateBuilder.or(
            Builder.header(name).isEqualTo(value),
            Builder.exchangeProperty(name).isEqualTo(value)
        )
}
