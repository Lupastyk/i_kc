package com.finance.token.model

data class TransactionsResponse(
    val userId: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val count: Int? = null
)