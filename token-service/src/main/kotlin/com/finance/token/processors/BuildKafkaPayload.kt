package com.finance.token.processors

import com.fasterxml.jackson.databind.ObjectMapper
import com.finance.token.model.TransactionsResponse
import com.finance.token.model.UserAndTransactions
import com.finance.token.model.UserClaims
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.apache.camel.Exchange
import org.apache.camel.Processor

@ApplicationScoped
@Named("buildKafkaPayload")
class BuildKafkaPayload @Inject constructor(
) : Processor {

    override fun process(exchange: Exchange) {
        val userClaims = exchange.getProperty("userClaims", UserClaims::class.java)
        val txResp = exchange.getProperty("transactions", TransactionsResponse::class.java)

        val userId =
            txResp.userId
                ?: userClaims.subject
                ?: userClaims.preferredUsername
                ?: throw IllegalStateException("Cannot find userId")

        exchange.setProperty("kafkaPayload", UserAndTransactions(userId, userClaims, txResp.transactions))
    }
}
