package com.finance.token.model

data class UserAndTransactions(
    val userId: String? = null,
    val user: UserClaims,
    val transactions: List<Transaction>
)