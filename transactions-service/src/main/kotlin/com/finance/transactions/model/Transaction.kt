package com.finance.transactions.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDateTime

data class Transaction(
    val id: String,
    val accountId: String,
    val amount: BigDecimal,
    val currency: String,
    val type: TransactionType,
    val description: String,
    val merchantName: String?,
    val category: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime,
    val status: TransactionStatus,
    val reference: String,
    val balance: BigDecimal?
)

enum class TransactionType {
    DEBIT, CREDIT, TRANSFER, FEE, INTEREST
}

enum class TransactionStatus {
    PENDING, COMPLETED, FAILED, CANCELLED
}