package com.finance.token.model

data class Transaction(
    val id: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val timestamp: Long? = null
)