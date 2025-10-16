package com.finance.token.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class Transaction(
    val id: String = "",
    val accountId: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val currency: String = "",
    val type: String = "",
    val description: String? = null,
    val merchantName: String? = null,
    val category: String? = null,
    val timestamp: String? = null,
    val status: String? = null,
    val reference: String? = null,
    val balance: BigDecimal? = null
)
