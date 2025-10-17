package com.finance.token.processors

import com.finance.token.model.response.TokenResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.apache.camel.Exchange
import org.apache.camel.Processor

@ApplicationScoped
@Named("errorResponseProcessor")
class ErrorResponseProcessor @Inject constructor(
) : Processor {

    override fun process(exchange: Exchange) {
        val tokenResponse = exchange.message.getBody(TokenResponse::class.java)
        if (tokenResponse.error != null) {
            exchange.message.setHeader(Exchange.CONTENT_TYPE, "application/json")
            exchange.message.body = mapOf("error" to (tokenResponse.errorDescription ?: "keycloak error"))
            exchange.setProperty("kc_error", true)
        } else {
            exchange.setProperty("accessToken", tokenResponse.accessToken ?: "")
        }
    }
}
