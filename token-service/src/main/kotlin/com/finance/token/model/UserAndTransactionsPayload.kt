package com.finance.token.model

data class UserAndTransactionsPayload(
    val userId: String? = null,
    val user: UserClaims,
    val transactions: List<Transaction>
)