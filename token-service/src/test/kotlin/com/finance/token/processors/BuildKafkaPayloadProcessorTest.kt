package com.finance.token.processors

import com.finance.token.model.Transaction
import com.finance.token.model.TransactionsResponse
import com.finance.token.model.UserAndTransactions
import com.finance.token.model.UserClaims
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BuildKafkaPayloadProcessorTest {

    private val ctx = DefaultCamelContext()
    private val p = BuildKafkaPayload()

    @Test
    fun combinesClaimsAndTransactions() {
        val ex = DefaultExchange(ctx)

        // Даем процессору ровно то, что он читает
        val claims = UserClaims(
            subject = "u1",
            preferredUsername = "demo",
            email = null,
            issuer = "http://keycloak",
            expiresAt = 0L
        )
        ex.setProperty("userClaims", claims)

        val tx = TransactionsResponse(
            userId = "u1",
            transactions = listOf(
                Transaction(
                    id = "t1",
                    accountId = "a1",
                    amount = BigDecimal("10.00"),
                    currency = "EUR",
                    type = "CREDIT",
                    description = "Refund",
                    merchantName = null,
                    category = "Shopping",
                    timestamp = "2025-10-01T00:00:00Z",
                    status = "COMPLETED",
                    reference = "r1",
                    balance = BigDecimal("100.00")
                )
            ),
            count = 1
        )
        // ВАЖНО: именно property "transactions"
        ex.setProperty("transactions", tx)

        p.process(ex)

        // Процессор пишет результат в property "kafkaPayload"
        val payload = ex.getProperty("kafkaPayload", UserAndTransactions::class.java)
        assertNotNull(payload)
        assertEquals("u1", payload.userId)
        assertEquals("demo", payload.user?.preferredUsername)
        assertEquals(1, payload.transactions.size)
        assertEquals(BigDecimal("10.00"), payload.transactions[0].amount)
        assertEquals(BigDecimal("100.00"), payload.transactions[0].balance)
    }

    @Test
    fun fallsBackToSubjectWhenUserIdMissing() {
        val ex = DefaultExchange(ctx)

        val claims = UserClaims(
            subject = "sub-42",
            preferredUsername = "demo",
            email = null,
            issuer = "http://keycloak",
            expiresAt = 0L
        )
        ex.setProperty("userClaims", claims)

        val txWithoutUserId = TransactionsResponse(
            userId = null,
            transactions = emptyList(),
            count = 0
        )
        ex.setProperty("transactions", txWithoutUserId)

        p.process(ex)

        val payload = ex.getProperty("kafkaPayload", UserAndTransactions::class.java)
        assertNotNull(payload)
        assertEquals("sub-42", payload.userId) // fallback на subject
    }
}
