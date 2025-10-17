package com.finance.token.model.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.finance.token.model.Transaction

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionsResponse(
    val userId: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val count: Int? = null
)