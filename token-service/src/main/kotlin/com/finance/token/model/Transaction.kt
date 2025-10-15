package com.finance.token.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Transaction(
    val id: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val timestamp: Long? = null
)