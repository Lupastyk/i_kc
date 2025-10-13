package com.finance.transactions.service

import com.finance.transactions.model.Transaction
import com.finance.transactions.model.TransactionType
import com.finance.transactions.model.TransactionStatus
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@ApplicationScoped
class TransactionService {

    companion object {
        private val logger = LoggerFactory.getLogger(TransactionService::class.java)
    }

    private val merchants = listOf(
        "Amazon", "Starbucks", "McDonald's", "Target", "Walmart", "Apple Store",
        "Netflix", "Spotify", "Uber", "Airbnb", "PayPal", "Steam"
    )

    private val categories = listOf(
        "Shopping", "Food & Drink", "Transportation", "Entertainment", "Bills & Utilities",
        "Health & Medical", "Travel", "Education", "Investment", "Other"
    )

    fun getTransactionsForUser(userId: String, limit: Int = 20): List<Transaction> {
        logger.info("Generating {} fake transactions for user: {}", limit, userId)

        return (1..limit).map { index ->
            generateFakeTransaction(userId, index)
        }.sortedByDescending { it.timestamp }
    }

    private fun generateFakeTransaction(userId: String, index: Int): Transaction {
        val random = Random()
        val isDebit = random.nextBoolean()
        val amount = BigDecimal.valueOf(random.nextDouble() * 1000 + 1).setScale(2, BigDecimal.ROUND_HALF_UP)
        val transactionType = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT

        return Transaction(
            id = UUID.randomUUID().toString(),
            accountId = "ACC-${userId}-001",
            amount = if (isDebit) amount.negate() else amount,
            currency = "EUR",
            type = transactionType,
            description = generateDescription(transactionType),
            merchantName = if (isDebit) merchants.random() else null,
            category = categories.random(),
            timestamp = LocalDateTime.now().minusHours(random.nextLong(720)), // Random time within last 30 days
            status = TransactionStatus.values().random(),
            reference = "REF-${System.currentTimeMillis()}-${index}",
            balance = BigDecimal.valueOf(random.nextDouble() * 5000 + 100).setScale(2, BigDecimal.ROUND_HALF_UP)
        )
    }

    private fun generateDescription(type: TransactionType): String {
        return when (type) {
            TransactionType.DEBIT -> listOf(
                "Card payment", "Online purchase", "ATM withdrawal", "Direct debit", "Subscription payment"
            ).random()
            TransactionType.CREDIT -> listOf(
                "Salary deposit", "Transfer received", "Interest payment", "Refund", "Cash deposit"
            ).random()
            TransactionType.TRANSFER -> "Transfer to account"
            TransactionType.FEE -> "Banking fee"
            TransactionType.INTEREST -> "Interest earned"
        }
    }
}