package com.finance.token.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionsResponse(
    val userId: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val count: Int? = null
)