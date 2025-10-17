package com.finance.token.processors

import com.finance.token.model.UserAndTransactionsPayload
import com.finance.token.model.UserClaims
import com.finance.token.model.response.TransactionsResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.apache.camel.Exchange
import org.apache.camel.Processor

@ApplicationScoped
@Named("buildKafkaPayload")
class BuildKafkaPayloadProcessor @Inject constructor(
) : Processor {

    override fun process(exchange: Exchange) {
        val userClaims = exchange.getProperty("userClaims", UserClaims::class.java)
        val txResp = exchange.getProperty("transactions", TransactionsResponse::class.java)

        val userId =
            txResp.userId
                ?: userClaims.subject
                ?: userClaims.preferredUsername
                ?: throw IllegalStateException("Cannot find userId")

        exchange.setProperty("kafkaPayload", UserAndTransactionsPayload(userId, userClaims, txResp.transactions))
    }
}
