package com.finance.token.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserClaims(
    val subject: String? = null,

    @JsonProperty("preferred_username")
    val preferredUsername: String? = null,

    val email: String? = null,

    @JsonProperty("iss")
    val issuer: String? = null,

    @JsonProperty("exp")
    val expiresAt: Long? = null
)
