package com.finance.token.routes

import com.finance.token.model.response.TokenResponse
import com.finance.token.model.response.TransactionsResponse
import jakarta.enterprise.context.ApplicationScoped
import org.apache.camel.Exchange
import org.apache.camel.builder.Builder
import org.apache.camel.builder.PredicateBuilder
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.dataformat.JsonLibrary
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory

@ApplicationScoped
open class TokenRoute(
    @ConfigProperty(name = "keycloak.tokenUrl")
    private val tokenUrl: String,

    @ConfigProperty(name = "transactions.baseUrl")
    private val txBaseUrl: String,

    @ConfigProperty(name = "kafka.topic.user-transactions")
    private val topic: String,

    @ConfigProperty(name = "kafka.enabled", defaultValue = "true")
    private val kafkaEnabled: Boolean,
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
            .process("authCodeProcessor")
            .choice()
            .`when`(
                PredicateBuilder.or(
                    PredicateBuilder.or(
                        Builder.header("authCode").isNull,
                        Builder.exchangeProperty("authCode").isNull,
                        Builder.header("authCode").isEqualTo(""),
                        Builder.exchangeProperty("authCode").isEqualTo("")
                    )
                )
            )
            .setHeader(Exchange.CONTENT_TYPE).constant("application/json")
            .setBody().constant(mapOf("error" to "missing 'code' query param"))
            .marshal().json(JsonLibrary.Jackson)
            .stop()
            .end()

            .removeHeaders("*")
            .setHeader(Exchange.CONTENT_TYPE).constant("application/x-www-form-urlencoded")
            .process("buildKeycloakPayloadProcessor")

            .choice()
            .`when`(Builder.exchangeProperty("kc_error").isEqualTo(true))
            .marshal().json(JsonLibrary.Jackson)
            .stop()
            .end()

            .toD("$tokenUrl?httpMethod=POST&bridgeEndpoint=true&throwExceptionOnFailure=false")
            .unmarshal().json(JsonLibrary.Jackson, TokenResponse::class.java)
            .process("errorResponseProcessor")

            .choice()
            .`when`(Builder.exchangeProperty("kc_error").isEqualTo(true))
            .marshal().json(JsonLibrary.Jackson)
            .stop()
            .end()

            .process("jwtClaimsProcessor")

            .removeHeaders("*")
            .setHeader("Authorization").simple("Bearer \${exchangeProperty.accessToken}")
            .setBody().constant(null)
            .toD("$txBaseUrl/api/transactions?httpMethod=GET&bridgeEndpoint=true&throwExceptionOnFailure=false")

            .unmarshal().json(JsonLibrary.Jackson, TransactionsResponse::class.java)
            .setProperty("transactions").body()

            .process("buildKafkaPayload")
            .choice()
            .`when`().constant(kafkaEnabled)
            .setBody().exchangeProperty("kafkaPayload")
            .marshal().json(JsonLibrary.Jackson)
            .toD("kafka:$topic?brokers={{kafka.bootstrap.servers}}")
            .log("Published user + transactions to Kafka topic '$topic'")
            .end()

            .setBody().exchangeProperty("transactions")
            .marshal().json(JsonLibrary.Jackson)
    }
}
